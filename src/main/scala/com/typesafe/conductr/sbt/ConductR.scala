/*
 * Copyright © 2014-2015 Typesafe, Inc. All rights reserved.
 */

package com.typesafe.conductr.sbt

import java.io.{ IOException, InputStreamReader }
import java.net.URL
import java.util.zip.{ ZipException, ZipFile }

import akka.actor.{ ActorRef, ActorSystem }
import akka.http.scaladsl.model.{ Uri => HttpUri }
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.conductr.client.ConductRController
import com.typesafe.conductr.client.ConductRController.{ LoadBundle, RunBundle, StopBundle, UnloadBundle, extractZipEntry }
import com.typesafe.config.{ Config, ConfigFactory }
import org.scalactic.{ Accumulation, Bad, One, Or }
import play.api.libs.json.{ JsError, JsSuccess, Json }
import sbt._
import scala.concurrent.Await
import scala.util.{ Failure, Success }
import collection.JavaConverters._

private[conductr] object ConductR {
  import com.typesafe.sbt.bundle.SbtBundle.autoImport._
  import Import._

  final val DefaultConductrProtocol = "http"
  final val DefaultConductrHost = ConductRPlugin.resolveDefaultHostIp()
  final val DefaultConductrPort = 9005

  def loadBundle(bundle: URI, config: Option[URI], loadTimeout: Timeout)(implicit apiVersion: ConductRController.ApiVersion.Value, log: Logger, conductrController: ActorRef): String =
    if (apiVersion == ConductRController.ApiVersion.V1)
      loadBundleV1(bundle, config, loadTimeout)
    else
      loadBundleV2(bundle, config, loadTimeout)

  def loadBundleV1(bundle: URI, config: Option[URI], loadTimeout: Timeout)(implicit apiVersion: ConductRController.ApiVersion.Value, log: Logger, conductrController: ActorRef): String = {
    def getSchedulingParams = {
      def parseBundleConfig(bundle: URI) =
        try {
          val bundleConf = extractZipEntry("bundle.conf", new File(bundle.getPath), IO.createTemporaryDirectory)
          val config = bundleConf.map(ConfigFactory.parseFile)
          Or.from(config, One(s"Zip file $bundle doesn't contain a bundle.conf file."))
        } catch {
          case _: IOException  => Bad(One(s"File $bundle doesn't exist."))
          case _: ZipException => Bad(One(s"File $bundle has not a valid zip format."))
        }
      def mergeSchedulingParams(baseConfig: Config, overlayConfigOpt: Option[Config]) = {
        val config = overlayConfigOpt.fold(baseConfig)(_.withFallback(baseConfig))
        (
          config.getString("name"),
          config.getString("compatibilityVersion"),
          config.getString("systemVersion"),
          config.getString("system"),
          config.getStringList("roles").asScala.toSet,
          config.getDouble("nrOfCpus"),
          config.getLong("memory"),
          config.getLong("diskSpace"))
      }
      val baseBundleConf = parseBundleConfig(bundle)
      val overlayBundleConf = Accumulation.convertOptionToCombinable(config.map(parseBundleConfig)).combined
      Accumulation.withGood(baseBundleConf, overlayBundleConf)(mergeSchedulingParams)
    }
    val doLoadBundle = (normalizedName: String, compatibilityVersion: String, systemVersion: String, system: String, roles: Set[String], nrOfCpus: Double, memory: Long, diskSpace: Long) => {
      log.info("Loading bundle to ConductR ...")
      val request =
        LoadBundle(
          apiVersion,
          normalizedName,
          compatibilityVersion,
          system,
          systemVersion,
          nrOfCpus,
          memory,
          diskSpace,
          roles,
          HttpUri(bundle.toString),
          config.map(u => HttpUri(u.toString))
        )
      val response = conductrController.ask(request)(loadTimeout).mapTo[String]
      Await.ready(response, loadTimeout.duration)
      response.value.get match {
        case Success(s) =>
          (Json.parse(s) \ "bundleId").validate[String] match {
            case JsSuccess(bundleId, _) =>
              log.info(s"Upload completed. Use 'conduct run $bundleId' to run.")
              bundleId
            case e: JsError =>
              sys.error(s"Unexpected response: $e")
          }
        case Failure(e) =>
          sys.error(s"Problem loading the bundle: ${e.getMessage}")
      }
    }
    getSchedulingParams.fold(
      params => doLoadBundle.tupled(params),
      errors => sys.error(errors.mkString(f"%n")))
  }

  def loadBundleV2(bundle: URI, config: Option[URI], loadTimeout: Timeout)(implicit apiVersion: ConductRController.ApiVersion.Value, log: Logger, conductrController: ActorRef): String = {
    log.info("Loading bundle to ConductR ...")
    val request = ConductRController.V2.LoadBundle(
      HttpUri(bundle.toString),
      config.map(u => HttpUri(u.toString))
    )
    val response = conductrController.ask(request)(loadTimeout).mapTo[String]
    Await.ready(response, loadTimeout.duration)
    response.value.get match {
      case Success(s) =>
        (Json.parse(s) \ "bundleId").validate[String] match {
          case JsSuccess(bundleId, _) =>
            log.info(s"Upload completed. Use 'conduct run $bundleId' to run.")
            bundleId
          case e: JsError =>
            sys.error(s"Unexpected response: $e")
        }
      case Failure(e) =>
        sys.error(s"Problem loading the bundle: ${e.getMessage}")
    }
  }

  def runBundle(bundleId: String, scale: Option[Int], affinity: Option[String], requestTimeout: Timeout)(implicit apiVersion: ConductRController.ApiVersion.Value, log: Logger, conductrController: ActorRef): String = {
    log.info(s"Running bundle $bundleId ...")
    val response = conductrController.ask(RunBundle(apiVersion, bundleId, scale.getOrElse(1), affinity))(requestTimeout).mapTo[String]
    Await.ready(response, requestTimeout.duration)
    response.value.get match {
      case Success(s) =>
        (Json.parse(s) \ "requestId").validate[String] match {
          case JsSuccess(requestId, _) =>
            log.info(s"Request for running has been delivered with id: $requestId")
            requestId
          case e: JsError =>
            sys.error(s"Unexpected response: $e")
        }
      case Failure(e) =>
        sys.error(s"Problem running the bundle: ${e.getMessage}")
    }
  }

  def stopBundle(bundleId: String, requestTimeout: Timeout)(implicit apiVersion: ConductRController.ApiVersion.Value, log: Logger, conductrController: ActorRef): String = {
    log.info(s"Stopping all bundle $bundleId instances ...")
    val response = conductrController.ask(StopBundle(apiVersion, bundleId))(requestTimeout).mapTo[String]
    Await.ready(response, requestTimeout.duration)
    response.value.get match {
      case Success(s) =>
        (Json.parse(s) \ "requestId").validate[String] match {
          case JsSuccess(requestId, _) =>
            log.info(s"Request for stopping has been delivered with id: $requestId")
            requestId
          case e: JsError =>
            sys.error(s"Unexpected response: $e")
        }
      case Failure(e) =>
        sys.error(s"Problem stopping the bundle: ${e.getMessage}")
    }
  }

  def unloadBundleTask(bundleId: String, requestTimeout: Timeout)(implicit apiVersion: ConductRController.ApiVersion.Value, log: Logger, conductrController: ActorRef): String = {
    log.info(s"Unloading bundle $bundleId ...")
    val response = conductrController.ask(UnloadBundle(apiVersion, bundleId))(requestTimeout).mapTo[String]
    Await.ready(response, requestTimeout.duration)
    response.value.get match {
      case Success(s) =>
        (Json.parse(s) \ "requestId").validate[String] match {
          case JsSuccess(requestId, _) =>
            log.info(s"Request for unloading has been delivered with id: $requestId")
            requestId
          case e: JsError =>
            sys.error(s"Unexpected response: $e")
        }
      case Failure(e) =>
        sys.error(s"Problem unloading the bundle: ${e.getMessage}")
    }
  }

  def info()(implicit system: ActorSystem, conductrController: ActorRef, apiVersion: ConductRController.ApiVersion.Value): Unit =
    console.Console.bundleInfo(refresh = false)

  def events(bundleId: String, lines: Option[Int])(implicit system: ActorSystem, conductrController: ActorRef, apiVersion: ConductRController.ApiVersion.Value): Unit =
    console.Console.bundleEvents(bundleId, lines.getOrElse(10), refresh = false)

  def logs(bundleId: String, lines: Option[Int])(implicit system: ActorSystem, conductrController: ActorRef, apiVersion: ConductRController.ApiVersion.Value): Unit =
    console.Console.bundleLogs(bundleId, lines.getOrElse(10), refresh = false)

  def envUrl(envIp: String, defaultIp: String, envPort: String, defaultPort: Int, defaultProto: String): URL = {
    val ip = sys.env.getOrElse(envIp, defaultIp)
    val port = sys.env.getOrElse(envPort, defaultPort)

    new URL(s"$defaultProto://$ip:$port")
  }

  def prepareConductrUrl(url: String): sbt.URL = {
    def insertPort(url: String, port: Int): String =
      url.indexOf("/", "http://".length) match {
        case -1             => s"""$url:$port"""
        case firstPathSlash => s"""${url.substring(0, firstPathSlash)}:$port${url.substring(firstPathSlash)}"""
      }
    val surl = if (url.contains("://")) url else s"$DefaultConductrProtocol://$url"
    val nurl = new sbt.URL(surl)

    nurl.getPort match {
      case -1 => new sbt.URL(insertPort(surl, DefaultConductrPort))
      case _  => nurl
    }
  }

  def toApiVersion(apiVersion: String): ConductRController.ApiVersion.Value =
    try
      ConductRController.ApiVersion.withName(apiVersion)
    catch {
      case _: NoSuchElementException => sys.error(s"Unknown API version: $apiVersion")
    }
}
