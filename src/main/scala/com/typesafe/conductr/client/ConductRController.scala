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
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.pattern.pipe
import akka.stream.actor.ActorPublisher
import akka.stream.scaladsl.{ Flow, ImplicitMaterializer, Sink, Source }
import akka.util.{ ByteString, Timeout }
import java.net.{ URI, URL }
import play.api.libs.json.{ Reads, Json }
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.concurrent.duration._

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
   * @param apiVersion The HTTP API version of ConductR
   * @param bundleId The bundle/config combination to start
   * @param scale The number of instances to scale up or down to.
   */
  case class RunBundle(apiVersion: ApiVersion.Value, bundleId: String, scale: Int)

  /**
   * Stop a bundle/config combination. Returns a request id for tracking purposes.
   * @param apiVersion The HTTP API version of ConductR
   * @param bundleId The bundle/config combination to stop
   */
  case class StopBundle(apiVersion: ApiVersion.Value, bundleId: String)

  /**
   * Unload a bundle from the storage managed by the RR.
   * @param apiVersion The HTTP API version of ConductR
   * @param bundleId the bundle id to unload
   */
  case class UnloadBundle(apiVersion: ApiVersion.Value, bundleId: String)

  /**
   * Request for a [[BundleInfo]] stream.
   * @param apiVersion The HTTP API version of ConductR
   */
  case class GetBundleInfoStream(apiVersion: ApiVersion.Value)

  /**
   * Retrieve events by bundle
   * @param apiVersion The HTTP API version of ConductR
   * @param bundleId the bundle id to retrieve events from
   * @param lines Number of lines to display
   */
  case class GetBundleEvents(apiVersion: ApiVersion.Value, bundleId: String, lines: Int)

  /**
   * Retrieve log messages by bundle
   * @param apiVersion The HTTP API version of ConductR
   * @param bundleId the bundle id to retrieve log messages from
   * @param lines Number of lines to display
   */
  case class GetBundleLogs(apiVersion: ApiVersion.Value, bundleId: String, lines: Int)

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
    timestamp: String,
    event: String,
    description: String)

  case class Log(
    timestamp: String,
    host: String,
    message: String)

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
    case request: GetBundleInfoStream => getBundleInfo(request)
    case request: GetBundleEvents     => getBundleEvents(request)
    case request: GetBundleLogs       => getBundleLogs(request)
    case request: LoadBundle          => loadBundle(request)
    case request: RunBundle           => runBundle(request)
    case request: StopBundle          => stopBundle(request)
    case request: UnloadBundle        => unloadBundle(request)
  }

  private def apiVersionPath(version: ApiVersion.Value): String =
    if (version == ApiVersion.V10) "" else "/v" + version

  protected def request(request: HttpRequest, connection: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]]): Future[HttpResponse] =
    Source.single(request).via(connection).runWith(Sink.head)

  private def httpRequest[A: Reads](method: HttpMethod, uri: String, entity: Option[RequestEntity] = None, parse: Boolean = false): Future[A] = {
    def bodyOrThrow(response: HttpResponse, body: String): String =
      if (response.status.isSuccess() && response.status != StatusCodes.MultipleChoices) body else throw new IllegalStateException(body)

    val req = entity.map(e => HttpRequest(method, uri, entity = e)).getOrElse(HttpRequest(method, uri))
    for {
      connection <- connect(conductr.authority.host.address(), conductr.authority.port)(context.system, connectTimeout)
      response <- request(req, connection)
      body <- Unmarshal(response.entity).to[String]
    } yield {
      val b = bodyOrThrow(response, body)
      if (parse) Json.parse(b).as[A] else b.asInstanceOf[A]
    }
  }

  private def loadBundle(loadBundle: LoadBundle): Unit = {
    val uri = s"${apiVersionPath(loadBundle.apiVersion)}/bundles"
    val bundleFileBodyPart = fileBodyPart("bundle", filename(loadBundle.bundle), publisher(loadBundle.bundle))
    val configFileBodyPart = loadBundle.config.map(config => fileBodyPart("configuration", filename(config), publisher(config)))
    val pendingResponse = for {
      entity <- Marshal(FormData(formBodyParts(loadBundle, bundleFileBodyPart, configFileBodyPart))).to[RequestEntity]
      response <- httpRequest[String](POST, uri, entity = Some(entity))
    } yield response

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
    val uri = s"${apiVersionPath(runBundle.apiVersion)}/bundles/${runBundle.bundleId}?scale=${runBundle.scale}"
    httpRequest[String](PUT, uri).pipeTo(sender())
  }

  private def stopBundle(stopBundle: StopBundle): Unit = {
    val uri = s"${apiVersionPath(stopBundle.apiVersion)}/bundles/${stopBundle.bundleId}?scale=0"
    httpRequest[String](PUT, uri).pipeTo(sender())
  }

  private def unloadBundle(unloadBundle: UnloadBundle): Unit = {
    val uri = s"${apiVersionPath(unloadBundle.apiVersion)}/bundles/${unloadBundle.bundleId}"
    httpRequest[String](DELETE, uri).pipeTo(sender())
  }

  private def getBundleInfo(getBundleInfoStream: GetBundleInfoStream): Unit = {
    // TODO this needs to be driven by SSE and not by the timer
    val uri = s"${apiVersionPath(getBundleInfoStream.apiVersion)}/bundles"
    val source = Source(100.millis, 2.seconds, () => ()).mapAsync(1)(_ => httpRequest[Seq[BundleInfo]](GET, uri, parse = true))
    sender() ! DataSource(source)
  }

  private def getBundleEvents(getBundleEvents: GetBundleEvents): Unit = {
    // TODO this needs to be driven by SSE and not by the timer
    val uri = s"${apiVersionPath(getBundleEvents.apiVersion)}/bundles/${getBundleEvents.bundleId}/events?count=${getBundleEvents.lines}"
    val source = Source(100.millis, 2.seconds, () => ()).mapAsync(1)(_ => httpRequest[Seq[Event]](GET, uri, parse = true))
    sender() ! DataSource(source)
  }

  private def getBundleLogs(getBundleLogs: GetBundleLogs): Unit = {
    // TODO this needs to be driven by SSE and not by the timer
    val uri = s"${apiVersionPath(getBundleLogs.apiVersion)}/bundles/${getBundleLogs.bundleId}/logs?count=${getBundleLogs.lines}"
    val source = Source(100.millis, 2.seconds, () => ()).mapAsync(1)(_ => httpRequest[Seq[Log]](GET, uri, parse = true))
    sender() ! DataSource(source)
  }
}
