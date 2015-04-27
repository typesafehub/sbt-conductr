/*
 * Copyright © 2014 Typesafe, Inc. All rights reserved.
 */

package com.typesafe.conductr.client

import akka.actor.{ Actor, ActorRef, ActorRefFactory, ActorSystem, Cancellable, Props }
import akka.cluster.UniqueAddress
import akka.contrib.stream.InputStreamPublisher
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.HttpEntity.IndefiniteLength
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.Multipart.FormData
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse, MediaTypes, RequestEntity, Uri }
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.pattern.pipe
import akka.stream.actor.ActorPublisher
import akka.stream.scaladsl.{ Flow, ImplicitFlowMaterializer, Sink, Source }
import akka.util.{ ByteString, Timeout }
import java.net.{ URI, URL }
import play.api.libs.json.Json
import scala.concurrent.Future
import scala.concurrent.duration.Duration

object ConductRController {

  private[this] val BundlePath = "(.*?)(-[a-fA-F0-9]{0,64})?\\.zip".r

  /**
   * The Props for an actor that represents the ConductR's control endpoint.
   * @param address The address to reach the ConductR at.
   * @param connectTimeout The amount of time to wait for establishing a connection with the conductor's control interface.
   */
  def props(address: Uri, connectTimeout: Timeout) =
    Props(new ConductRController(address, connectTimeout))

  /**
   * Load a bundle with optional configuration.
   * @param bundle The address of the bundle.
   * @param config An optional configuration that will override any configuration found within the bundle.
   * @param nrOfCpus the number of cpus required to run.
   * @param memory the memory required to run.
   * @param diskSpace the amount of disk space required to load.
   * @param roles the types of node that the bundle can run on.
   */
  case class LoadBundle(
    bundle: Uri,
    config: Option[Uri],
    system: String,
    nrOfCpus: Double,
    memory: Long,
    diskSpace: Long,
    roles: Set[String])

  /**
   * Run a bundle/config combination. Returns a request id for tracking purposes.
   * @param bundleId The bundle/config combination to start
   * @param scale The number of instances to scale up or down to.
   */
  case class RunBundle(bundleId: String, scale: Int)

  /**
   * Stop a bundle/config combination. Returns a request id for tracking purposes.
   * @param bundleId The bundle/config combination to stop
   */
  case class StopBundle(bundleId: String)

  /**
   * Unload a bundle from the storage managed by the RR.
   * @param bundleId the bundle id to unload
   */
  case class UnloadBundle(bundleId: String)

  /**
   * Request for a [[BundleInfo]] stream.
   */
  case object GetBundleInfoStream

  /**
   * A flow of [[BundleInfo]]. Needs to be materialized after attaching sink.
   */
  case class BundleInfosSource(source: Source[Seq[BundleInfo], Cancellable])

  /**
   * Represent a bundle execution - ignores the endpoint info for now as we
   * do not render it.
   */
  case class BundleExecution(host: String, isStarted: Boolean)

  /**
   * Representation of bundle in the runtime.
   */
  case class BundleInfo(
    bundleId: String,
    bundleDigest: String,
    configDigest: Option[String],
    bundleInstallations: Seq[BundleInstallation],
    attributes: Attributes,
    bundleExecutions: Set[BundleExecution])

  /**
   * Representation of bundle attributes.
   */
  case class Attributes(
    nrOfCpus: Double,
    memory: Long,
    diskSpace: Long,
    roles: Set[String],
    bundleName: String)

  /**
   * Descriptor of a node's bundle installation including its associated optional configuration.
   * @param uniqueAddress the unique address within the cluster
   * @param bundleFile the path to the bundle, has to be a `URI`, because `Path` is not serializable
   * @param configurationFile the optional path to the bundle, has to be a `URI`, because `Path` is not serializable
   */
  case class BundleInstallation(uniqueAddress: UniqueAddress, bundleFile: URI, configurationFile: Option[URI])

  private val blockingIoDispatcher = "conductr-blocking-io-dispatcher"

  private def absolute(uri: Uri): Uri =
    if (uri.isAbsolute) uri else uri.withScheme("file")

  private def connect(host: String, port: Int)(implicit system: ActorSystem, timeout: Timeout) =
    Future.successful(Http(system).outgoingConnection(host, port))

  private def fileBodyPart(name: String, filename: String, source: Source[ByteString, Unit]): FormData.BodyPart =
    FormData.BodyPart(
      name,
      IndefiniteLength(MediaTypes.`application/octet-stream`, source),
      Map("filename" -> filename)
    )

  private def filename(uri: Uri): String =
    uri.path.reverse.head.toString

  private def publisher(uri: Uri)(implicit actorRefFactory: ActorRefFactory): Source[ByteString, Unit] =
    Source(
      ActorPublisher[ByteString](
        actorRefFactory.actorOf(
          InputStreamPublisher.props(new URL(absolute(uri).toString()).openStream(), Duration.Undefined)
            .withDispatcher(blockingIoDispatcher)
        )
      )
    )

  private def toBundleName(bundle: Uri): String =
    bundle.path.toString().reverse.takeWhile(_ != '/').reverse match {
      case BundlePath(name, _) => name
      case name                => name
    }
}

/**
 * An actor that represents the ConductR's control endpoint.
 */
class ConductRController(uri: Uri, connectTimeout: Timeout)
    extends Actor
    with ImplicitFlowMaterializer {

  import ConductRController._
  import context.dispatcher

  override def receive: Receive = {
    case GetBundleInfoStream   => fetchBundleFlow(sender())
    case request: LoadBundle   => loadBundle(request)
    case request: RunBundle    => runBundle(request)
    case request: StopBundle   => stopBundle(request)
    case request: UnloadBundle => unloadBundle(request)
  }

  protected def request(request: HttpRequest, connection: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]]): Future[HttpResponse] =
    Source.single(request).via(connection).runWith(Sink.head)

  private def loadBundle(loadBundle: LoadBundle): Unit = {
    val pendingResponse =
      for {
        connection <- connect(uri.authority.host.address(), uri.authority.port)(context.system, connectTimeout)
        bundleFileBodyPart = fileBodyPart("bundle", filename(loadBundle.bundle), publisher(loadBundle.bundle))
        configFileBodyPart = loadBundle.config.map(config => fileBodyPart("configuration", filename(config), publisher(config)))
        entity <- Marshal(FormData(formBodyParts(loadBundle, bundleFileBodyPart, configFileBodyPart))).to[RequestEntity]
        response <- request(HttpRequest(POST, "/bundles", entity = entity), connection)
        body <- Unmarshal(response.entity).to[String]
      } yield bodyOrThrow(response, body)
    pendingResponse.pipeTo(sender())
  }

  private def formBodyParts(
    loadBundle: LoadBundle,
    bundleFileBodyPart: FormData.BodyPart,
    configFileBodyPart: Option[FormData.BodyPart]): Source[FormData.BodyPart, Unit] =
    Source(
      List(
        FormData.BodyPart.Strict("system", loadBundle.system),
        FormData.BodyPart.Strict("nrOfCpus", loadBundle.nrOfCpus.toString),
        FormData.BodyPart.Strict("memory", loadBundle.memory.toString),
        FormData.BodyPart.Strict("diskSpace", loadBundle.diskSpace.toString),
        FormData.BodyPart.Strict("roles", loadBundle.roles.mkString(" ")),
        FormData.BodyPart.Strict("bundleName", toBundleName(loadBundle.bundle)),
        bundleFileBodyPart
      ) ++ configFileBodyPart
    )

  private def runBundle(runBundle: RunBundle): Unit = {
    val pendingResponse =
      for {
        connection <- connect(uri.authority.host.address(), uri.authority.port)(context.system, connectTimeout)
        response <- request(HttpRequest(PUT, s"/bundles/${runBundle.bundleId}?scale=${runBundle.scale}"), connection)
        body <- Unmarshal(response.entity).to[String]
      } yield bodyOrThrow(response, body)
    pendingResponse.pipeTo(sender())
  }

  private def stopBundle(stopBundle: StopBundle): Unit = {
    val pendingResponse =
      for {
        connection <- connect(uri.authority.host.address(), uri.authority.port)(context.system, connectTimeout)
        response <- request(HttpRequest(PUT, s"/bundles/${stopBundle.bundleId}?scale=0"), connection)
        body <- Unmarshal(response.entity).to[String]
      } yield bodyOrThrow(response, body)
    pendingResponse.pipeTo(sender())
  }

  private def unloadBundle(unloadBundle: UnloadBundle): Unit = {
    val pendingResponse =
      for {
        connection <- connect(uri.authority.host.address(), uri.authority.port)(context.system, connectTimeout)
        response <- request(HttpRequest(DELETE, s"/bundles/${unloadBundle.bundleId}"), connection)
        body <- Unmarshal(response.entity).to[String]
      } yield bodyOrThrow(response, body)
    pendingResponse.pipeTo(sender())
  }

  private def fetchBundleFlow(originalSender: ActorRef): Unit = {
    import scala.concurrent.duration._
    // TODO this needs to be driven by SSE and not by the timer
    val source = Source(100.millis, 2.seconds, () => ()).mapAsync(1, _ => getBundles)
    originalSender ! BundleInfosSource(source)
  }

  private def getBundles: Future[Seq[BundleInfo]] =
    for {
      connection <- connect(uri.authority.host.address(), uri.authority.port)(context.system, connectTimeout)
      response <- request(HttpRequest(GET, "/bundles"), connection)
      body <- Unmarshal(response.entity).to[String]
    } yield {
      val b = bodyOrThrow(response, body)
      Json.parse(b).as[Seq[BundleInfo]]
    }

  private def bodyOrThrow(response: HttpResponse, body: String): String =
    if (response.status.isSuccess())
      body
    else
      throw new IllegalStateException(body)
}
