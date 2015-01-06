/*
 * Copyright © 2014 Typesafe, Inc. All rights reserved.
 */

package com.typesafe.typesafeconductr

import akka.actor.{ Actor, ActorRef, ActorRefFactory, ActorSystem, Props }
import akka.contrib.stream.InputStreamPublisher
import akka.http.Http
import akka.http.marshalling.Marshal
import akka.http.model.HttpEntity.IndefiniteLength
import akka.http.model.HttpMethods._
import akka.http.model.Multipart.FormData
import akka.http.model.{ HttpRequest, HttpResponse, MediaTypes, RequestEntity, Uri }
import akka.http.unmarshalling.Unmarshal
import akka.pattern.{ ask, pipe }
import akka.stream.FlowMaterializer
import akka.stream.actor.ActorPublisher
import akka.stream.scaladsl.{ ImplicitFlowMaterializer, Sink, Source }
import akka.util.{ ByteString, Timeout }
import java.net.URL
import play.api.libs.json.{ JsPath, Json }
import scala.concurrent.Future
import scala.concurrent.duration.Duration

object ConductRController {
  /**
   * The Props for an actor that represents the ConductR's control endpoint.
   * @param address The address to reach the ConductR at.
   * @param connectTimeout The amount of time to wait for establishing a connection with the conductor's control interface.
   * @param httpIO The IO(Http) actor to use for IO.
   */
  def props(address: Uri, connectTimeout: Timeout, httpIO: ActorRef) =
    Props(new ConductRController(address, connectTimeout, httpIO))

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
    nrOfCpus: Double,
    memory: Long,
    diskSpace: Long,
    roles: Set[String])

  /**
   * Start a bundle/config combination. Returns a request id for tracking purposes.
   * @param bundleId The bundle/config combination to start
   * @param scale The number of instances to scale up or down to.
   */
  case class StartBundle(bundleId: String, scale: Int)

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
  case class BundleInfosSource(source: Source[Seq[BundleInfo]])

  /**
   * Represent a bundle execution - ignores the endpoint info for now as we
   * do not render it.
   */
  case class BundleExecution(host: String)

  /**
   * Representation of bundle in the runtime.
   */
  case class BundleInfo(
    bundleId: String,
    bundleDigest: String,
    configDigest: Option[String],
    nodeBundleFiles: Seq[NodeBundleFile],
    schedulingRequirement: SchedulingRequirement,
    bundleExecutions: Set[BundleExecution])

  /**
   * Representation of bundle requirements to be scheduled.
   */
  case class SchedulingRequirement(
    nrOfCpus: Double,
    memory: Long,
    diskSpace: Long,
    roles: Set[String])

  /**
   * Representation of node file in the runtime cluster.
   */
  case class NodeBundleFile(address: String, executing: Boolean)

  private val blockingIoDispatcher = "conductr-blocking-io-dispatcher"

  private def absolute(uri: Uri): Uri =
    if (uri.isAbsolute) uri else uri.withScheme("file")

  private def connect(
    httpIO: ActorRef,
    host: String,
    port: Int)(implicit system: ActorSystem, timeout: Timeout): Future[Http.OutgoingConnection] =
    (httpIO ? Http.Connect(host, port)).mapTo[Http.OutgoingConnection]

  private def fileBodyPart(name: String, filename: String, source: Source[ByteString]): FormData.BodyPart =
    FormData.BodyPart(
      name,
      IndefiniteLength(MediaTypes.`application/octet-stream`, source),
      Map("filename" -> filename)
    )

  private def filename(uri: Uri): String =
    uri.path.reverse.head.toString

  private def publisher(uri: Uri)(implicit actorRefFactory: ActorRefFactory): Source[ByteString] =
    Source(
      ActorPublisher[ByteString](
        actorRefFactory.actorOf(
          InputStreamPublisher.props(new URL(absolute(uri).toString()).openStream(), Duration.Undefined)
            .withDispatcher(blockingIoDispatcher)
        )
      )
    )

  private def request(request: HttpRequest, connection: Http.OutgoingConnection)(implicit mat: FlowMaterializer): Future[HttpResponse] = {
    Source(List(request -> None))
      .runWith(Sink(connection.requestSubscriber))
    Source(connection.responsePublisher)
      .map(_._1)
      .runWith(Sink.head)
  }
}

/**
 * An actor that represents the conductor's control endpoint.
 */
class ConductRController(uri: Uri, connectTimeout: Timeout, httpIO: ActorRef)
    extends Actor
    with ImplicitFlowMaterializer {

  import ConductRController._
  import context.dispatcher

  override def receive: Receive = {
    case GetBundleInfoStream   => fetchBundleFlow(sender())
    case request: LoadBundle   => loadBundle(request)
    case request: StartBundle  => startBundle(request)
    case request: StopBundle   => stopBundle(request)
    case request: UnloadBundle => unloadBundle(request)
  }

  private def loadBundle(loadBundle: LoadBundle): Unit = {
    val bodyParts =
      Source(
        List(
          FormData.BodyPart.Strict("nrOfCpus", loadBundle.nrOfCpus.toString),
          FormData.BodyPart.Strict("memory", loadBundle.memory.toString),
          FormData.BodyPart.Strict("diskSpace", loadBundle.diskSpace.toString),
          FormData.BodyPart.Strict("roles", loadBundle.roles.mkString(" ")),
          fileBodyPart("bundle", filename(loadBundle.bundle), publisher(loadBundle.bundle))
        ) ++
          loadBundle.config.map(config => fileBodyPart("config", filename(config), publisher(config)))
      )
    val pendingResponse =
      for {
        connection <- connect(httpIO, uri.authority.host.address(), uri.authority.port)(context.system, connectTimeout)
        entity <- Marshal(FormData(bodyParts)).to[RequestEntity]
        response <- request(HttpRequest(POST, "/bundles", entity = entity), connection)
        body <- Unmarshal(response.entity).to[String]
      } yield bodyOrThrow(response, body)
    pendingResponse.pipeTo(sender())
  }

  private def startBundle(startBundle: StartBundle): Unit = {
    val pendingResponse =
      for {
        connection <- connect(httpIO, uri.authority.host.address(), uri.authority.port)(context.system, connectTimeout)
        response <- request(HttpRequest(PUT, s"/bundles/${startBundle.bundleId}?scale=${startBundle.scale}"), connection)
        body <- Unmarshal(response.entity).to[String]
      } yield bodyOrThrow(response, body)
    pendingResponse.pipeTo(sender())
  }

  private def stopBundle(stopBundle: StopBundle): Unit = {
    val pendingResponse =
      for {
        connection <- connect(httpIO, uri.authority.host.address(), uri.authority.port)(context.system, connectTimeout)
        response <- request(HttpRequest(PUT, s"/bundles/${stopBundle.bundleId}?scale=0"), connection)
        body <- Unmarshal(response.entity).to[String]
      } yield bodyOrThrow(response, body)
    pendingResponse.pipeTo(sender())
  }

  private def unloadBundle(unloadBundle: UnloadBundle): Unit = {
    val pendingResponse =
      for {
        connection <- connect(httpIO, uri.authority.host.address(), uri.authority.port)(context.system, connectTimeout)
        response <- request(HttpRequest(DELETE, s"/bundles/${unloadBundle.bundleId}"), connection)
        body <- Unmarshal(response.entity).to[String]
      } yield bodyOrThrow(response, body)
    pendingResponse.pipeTo(sender())
  }

  private def fetchBundleFlow(originalSender: ActorRef): Unit = {
    import scala.concurrent.duration._
    // TODO this needs to be driven by SSE and not by the timer
    val source = Source(0.millis, 2.seconds, () => ()).mapAsync(_ => getBundles)
    originalSender ! BundleInfosSource(source)
  }

  private def getBundles: Future[Seq[BundleInfo]] = {
    implicit val schedulingRequirementReads = Json.reads[SchedulingRequirement]
    implicit val nodeReads = (JsPath \ "node").read[String].map(NodeBundleFile(_, executing = false))
    implicit val bundleExecutionReads = Json.reads[BundleExecution]
    implicit val bundleInfoReads = Json.reads[BundleInfo]

    val pendingResponse = for {
      connection <- connect(httpIO, uri.authority.host.address(), uri.authority.port)(context.system, connectTimeout)
      response <- request(HttpRequest(GET, "/bundles"), connection)
      body <- Unmarshal(response.entity).to[String]
    } yield bodyOrThrow(response, body)

    pendingResponse.map { body =>
      val bundles = (Json.parse(body) \ "bundles").as[Seq[BundleInfo]]
      bundles.map { bundle =>
        val nodesWithExecution = bundle.nodeBundleFiles.map { node =>
          node.copy(executing = bundle.bundleExecutions.exists(be => node.address.contains(be.host)))
        }
        bundle.copy(nodeBundleFiles = nodesWithExecution)
      }
    }
  }

  private def bodyOrThrow(response: HttpResponse, body: String): String =
    if (response.status.isSuccess())
      body
    else
      throw new IllegalStateException(body)
}
