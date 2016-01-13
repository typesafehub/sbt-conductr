/*
 * Copyright © 2014-2015 Typesafe, Inc. All rights reserved.
 */

package com.typesafe.conductr.sbt

import java.net.URL

import akka.actor.ActorSystem
import akka.util.Timeout
import com.typesafe.conductr.akka.ConnectionContext
import com.typesafe.conductr.clientlib.akka.ControlClient
import com.typesafe.conductr.clientlib.scala.models._
import sbt._
import scala.concurrent.{ Future, Await }
import scala.util.{ Failure, Success }

private[conductr] object ConductRClient {
  final val DefaultConductrProtocol = "http"
  final val DefaultConductrHost = ConductRPlugin.resolveDefaultHostIp()
  final val DefaultConductrPort = 9005

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
}

private[conductr] class ConductRClient(controlClient: ControlClient)(implicit system: ActorSystem, cc: ConnectionContext, log: Logger) {

  import cc.context

  def loadBundle(bundle: URI, config: Option[URI], loadTimeout: Timeout): String = {
    log.info("Loading bundle to ConductR ...")
    println(s"bundle uri: $bundle")
    val response = controlClient.loadBundle(bundle, config)
    Await.ready(response, loadTimeout.duration)
    response.value.get match {
      case Success(BundleRequestSuccess(_, bundleId)) =>
        log.info(s"Upload completed. Use 'conduct run $bundleId' to run.")
        bundleId

      case Success(BundleRequestFailure(_, error)) =>
        sys.error(s"Unexpected response: $error")

      case Failure(error) =>
        sys.error(s"Problem loading the bundle: ${error.getMessage}")
    }
  }

  def runBundle(bundleId: String, scale: Option[Int], affinity: Option[String], requestTimeout: Timeout): String = {
    log.info(s"Running bundle $bundleId ...")
    val response = controlClient.runBundle(bundleId, scale, affinity)
    handleBundleRequestResult("running", response, requestTimeout)
  }

  def stopBundle(bundleId: String, requestTimeout: Timeout): String = {
    log.info(s"Stopping all bundle $bundleId instances ...")
    val response = controlClient.stopBundle(bundleId)
    handleBundleRequestResult("stopping", response, requestTimeout)
  }

  private def handleBundleRequestResult(requestType: String, response: Future[BundleRequestResult], requestTimeout: Timeout)(implicit log: Logger): String = {
    Await.ready(response, requestTimeout.duration)
    response.value.get match {
      case Success(BundleRequestSuccess(requestId, _)) =>
        log.info(s"Request for $requestType has been delivered with id: $requestId")
        requestId.toString

      case Success(BundleRequestFailure(_, error)) =>
        sys.error(s"Unexpected response: $error")

      case Failure(error) =>
        sys.error(s"Problem $requestType the bundle: ${error.getMessage}")
    }
  }

  def unloadBundle(bundleId: String, requestTimeout: Timeout): String = {
    log.info(s"Unloading bundle $bundleId ...")
    val response = controlClient.unloadBundle(bundleId)
    handleBundleRequestResult("unloading", response, requestTimeout)
  }

  def info(timeout: Timeout): Unit = {
    val response = controlClient.getBundlesInfo()
    Await.ready(response, timeout.duration)
    response.value.get match {
      case Success(bundles: Seq[Bundle]) =>
        console.Console.bundleInfo(bundles, refresh = false)

      case Failure(error) =>
        sys.error(s"Problem retrieving bundles: ${error.getMessage}")
    }
  }

  def events(bundleId: String, lines: Option[Int], timeout: Timeout): Unit = {
    val response = controlClient.getBundleEvents(bundleId, lines)
    Await.ready(response, timeout.duration)
    response.value.get match {
      case Success(BundleEventsSuccess(events)) =>
        console.Console.bundleEvents(events, false)

      case Success(BundleEventsFailure(_, error)) =>
        sys.error(s"Unexpected response: $error")

      case Failure(error) =>
        sys.error(s"Problem retrieving bundle events: ${error.getMessage}")
    }
  }

  def logs(bundleId: String, lines: Option[Int], timeout: Timeout): Unit = {
    val response = controlClient.getBundleLogs(bundleId, lines)
    Await.ready(response, timeout.duration)
    response.value.get match {
      case Success(BundleLogsSuccess(logs)) =>
        console.Console.bundleLogs(logs, false)

      case Success(BundleLogsFailure(_, error)) =>
        sys.error(s"Unexpected response: $error")

      case Failure(error) =>
        sys.error(s"Problem retrieving bundle log messages: ${error.getMessage}")
    }
  }
}
