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
import akka.stream.scaladsl.{ Flow, ImplicitMaterializer, Sink, Source }
import akka.util.{ ByteString, Timeout }
import java.net.{ URI, URL }
import play.api.libs.json.Json
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import play.api.libs.json.Reads

object ConductRController {

  /**
   * Supported ConductR Control Protocol versions.
   */
  object ApiVersion extends Enumeration {
    val V10 = Value("1.0")
    val V11 = Value("1.1")
  }

  /**
   * The Props for an actor that represents the ConductR's control endpoint.
   * @param conductr The address to reach the ConductR at.
   * @param connectTimeout The amount of time to wait for establishing a connection with the conductor's control interface.
   */
  def props(conductr: Uri, loggingQuery: Uri, connectTimeout: Timeout) =
    Props(new ConductRController(conductr, loggingQuery, connectTimeout))

  /**
   * Load a bundle with optional configuration.
   * @param apiVersion the version of the protocol to use when communicating with ConductR
   * @param name the name of the bundle
   * @param compatibilityVersion the compatibility of the bundle (determined by an app developer)
   * @param system the system associated with a bundle - can be used for grouping different bundles
   * @param systemVersion the version associated with the system (determined by an app developer)
   * @param nrOfCpus the number of cpus required to run.
   * @param memory the memory required to run.
   * @param diskSpace the amount of disk space required to load.
   * @param roles the types of node that the bundle can run on.
   * @param bundle The address of the bundle.
   * @param config An optional configuration that will override any configuration found within the bundle.
   */
  case class LoadBundle(
    apiVersion: ApiVersion.Value,
    name: String,
    compatibilityVersion: String,
    system: String,
    systemVersion: String,
    nrOfCpus: Double,
    memory: Long,
    diskSpace: Long,
    roles: Set[String],
    bundle: Uri,
    config: Option[Uri])

  /**
   * Run a bundle/config combination. Returns a request id for tracking purposes.
   * @param bundleId The bundle/config combination to start
   * @param scale The number of instances to scale up or down to.
   */
  case class RunBundle(apiVersion: ApiVersion.Value, bundleId: String, scale: Int)

  /**
   * Stop a bundle/config combination. Returns a request id for tracking purposes.
   * @param bundleId The bundle/config combination to stop
   */
  case class StopBundle(apiVersion: ApiVersion.Value, bundleId: String)

  /**
   * Unload a bundle from the storage managed by the RR.
   * @param bundleId the bundle id to unload
   */
  case class UnloadBundle(apiVersion: ApiVersion.Value, bundleId: String)

  /**
   * Request for a [[BundleInfo]] stream.
   */
  case class GetBundleInfoStream(apiVersion: ApiVersion.Value)

  case class GetEventStream(apiVersion: ApiVersion.Value, bundleId: String)

  case class GetLogStream(apiVersion: ApiVersion.Value, bundleId: String)

  /**
   * A flow of data for the screen. Needs to be materialized after attaching sink.
   */
  case class DataSource[A](source: Source[A, Cancellable])

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
    bundleExecutions: Set[BundleExecution],
    hasError: Boolean)

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

  case class Event(
    time: String,
    event: String,
    description: String)

  case class Log(
    time: String,
    host: String,
    log: String)

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
}

/**
 * An actor that represents the ConductR's control endpoint.
 */
class ConductRController(conductr: Uri, loggingQuery: Uri, connectTimeout: Timeout)
    extends Actor
    with ImplicitMaterializer {

  import ConductRController._
  import context.dispatcher

  override def receive: Receive = {
    case request: GetBundleInfoStream => fetchBundleSource(sender(), request)
    case request: GetEventStream      => fetchLoggingQuerySource[Event](sender(), request.apiVersion, request.bundleId)
    case request: GetLogStream        => fetchLoggingQuerySource[Log](sender(), request.apiVersion, request.bundleId)
    case request: LoadBundle          => loadBundle(request)
    case request: RunBundle           => runBundle(request)
    case request: StopBundle          => stopBundle(request)
    case request: UnloadBundle        => unloadBundle(request)
  }

  private def apiVersionPath(version: ApiVersion.Value): String =
    if (version == ApiVersion.V10) "" else "/v" + version

  protected def request(request: HttpRequest, connection: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]]): Future[HttpResponse] =
    Source.single(request).via(connection).runWith(Sink.head)

  private def loadBundle(loadBundle: LoadBundle): Unit = {
    val pendingResponse =
      for {
        connection <- connect(conductr.authority.host.address(), conductr.authority.port)(context.system, connectTimeout)
        bundleFileBodyPart = fileBodyPart("bundle", filename(loadBundle.bundle), publisher(loadBundle.bundle))
        configFileBodyPart = loadBundle.config.map(config => fileBodyPart("configuration", filename(config), publisher(config)))
        entity <- Marshal(FormData(formBodyParts(loadBundle, bundleFileBodyPart, configFileBodyPart))).to[RequestEntity]
        response <- request(HttpRequest(POST, s"${apiVersionPath(loadBundle.apiVersion)}/bundles", entity = entity), connection)
        body <- Unmarshal(response.entity).to[String]
      } yield bodyOrThrow(response, body)
    pendingResponse.pipeTo(sender())
  }

  private def formBodyParts(
    loadBundle: LoadBundle,
    bundleFileBodyPart: FormData.BodyPart,
    configFileBodyPart: Option[FormData.BodyPart]): Source[FormData.BodyPart, Unit] = {
    val schedulingRequirements = loadBundle.apiVersion match {
      case ApiVersion.V10 =>
        List(
          FormData.BodyPart.Strict("system", loadBundle.system),
          FormData.BodyPart.Strict("nrOfCpus", loadBundle.nrOfCpus.toString),
          FormData.BodyPart.Strict("memory", loadBundle.memory.toString),
          FormData.BodyPart.Strict("diskSpace", loadBundle.diskSpace.toString),
          FormData.BodyPart.Strict("roles", loadBundle.roles.mkString(" ")),
          FormData.BodyPart.Strict("bundleName", loadBundle.name)
        )
      case ApiVersion.V11 =>
        List(
          FormData.BodyPart.Strict("system", loadBundle.system),
          FormData.BodyPart.Strict("systemVersion", loadBundle.systemVersion),
          FormData.BodyPart.Strict("nrOfCpus", loadBundle.nrOfCpus.toString),
          FormData.BodyPart.Strict("memory", loadBundle.memory.toString),
          FormData.BodyPart.Strict("diskSpace", loadBundle.diskSpace.toString),
          FormData.BodyPart.Strict("roles", loadBundle.roles.mkString(" ")),
          FormData.BodyPart.Strict("bundleName", loadBundle.name),
          FormData.BodyPart.Strict("compatibilityVersion", loadBundle.compatibilityVersion)
        )
    }
    Source((schedulingRequirements :+ bundleFileBodyPart) ++ configFileBodyPart)
  }

  private def runBundle(runBundle: RunBundle): Unit = {
    val pendingResponse =
      for {
        connection <- connect(conductr.authority.host.address(), conductr.authority.port)(context.system, connectTimeout)
        response <- request(HttpRequest(PUT, s"${apiVersionPath(runBundle.apiVersion)}/bundles/${runBundle.bundleId}?scale=${runBundle.scale}"), connection)
        body <- Unmarshal(response.entity).to[String]
      } yield bodyOrThrow(response, body)
    pendingResponse.pipeTo(sender())
  }

  private def stopBundle(stopBundle: StopBundle): Unit = {
    val pendingResponse =
      for {
        connection <- connect(conductr.authority.host.address(), conductr.authority.port)(context.system, connectTimeout)
        response <- request(HttpRequest(PUT, s"${apiVersionPath(stopBundle.apiVersion)}/bundles/${stopBundle.bundleId}?scale=0"), connection)
        body <- Unmarshal(response.entity).to[String]
      } yield bodyOrThrow(response, body)
    pendingResponse.pipeTo(sender())
  }

  private def unloadBundle(unloadBundle: UnloadBundle): Unit = {
    val pendingResponse =
      for {
        connection <- connect(conductr.authority.host.address(), conductr.authority.port)(context.system, connectTimeout)
        response <- request(HttpRequest(DELETE, s"${apiVersionPath(unloadBundle.apiVersion)}/bundles/${unloadBundle.bundleId}"), connection)
        body <- Unmarshal(response.entity).to[String]
      } yield bodyOrThrow(response, body)
    pendingResponse.pipeTo(sender())
  }

  private def fetchBundleSource(originalSender: ActorRef, getBundleInfoStream: GetBundleInfoStream): Unit = {
    import scala.concurrent.duration._
    // TODO this needs to be driven by SSE and not by the timer
    val source = Source(100.millis, 2.seconds, () => ()).mapAsync(1)(_ => getBundles(getBundleInfoStream))
    originalSender ! DataSource(source)
  }

  private def getBundles(getBundleInfoStream: GetBundleInfoStream): Future[Seq[BundleInfo]] =
    for {
      connection <- connect(conductr.authority.host.address(), conductr.authority.port)(context.system, connectTimeout)
      response <- request(HttpRequest(GET, s"${apiVersionPath(getBundleInfoStream.apiVersion)}/bundles"), connection)
      body <- Unmarshal(response.entity).to[String]
    } yield {
      val b = bodyOrThrow(response, body)
      Json.parse(b).as[Seq[BundleInfo]]
    }

  private def fetchLoggingQuerySource[A: Reads](originalSender: ActorRef, apiVersion: ApiVersion.Value, bundleId: String): Unit = {
    import scala.concurrent.duration._
    // TODO this needs to be driven by SSE and not by the timer
    val source = Source(100.millis, 2.seconds, () => ()).mapAsync(1)(_ => getLoggingQuery[A](apiVersion, bundleId))
    originalSender ! DataSource(source)
  }

  private def getLoggingQuery[A: Reads](apiVersion: ApiVersion.Value, bundleId: String): Future[Seq[A]] =
    for {
      connection <- connect(loggingQuery.authority.host.address(), loggingQuery.authority.port)(context.system, connectTimeout)
      response <- request(HttpRequest(GET, s"${apiVersionPath(apiVersion)}/events/$bundleId"), connection)
      body <- Unmarshal(response.entity).to[String]
    } yield {
      val b = bodyOrThrow(response, body)
      Json.parse(b).as[Seq[A]]
    }

  private def bodyOrThrow(response: HttpResponse, body: String): String =
    if (response.status.isSuccess())
      body
    else
      throw new IllegalStateException(body)
}
