/*
 * Copyright © 2014-2015 Typesafe, Inc. All rights reserved.
 */

package com.typesafe.conductr.sbt

import java.net.URL

import akka.actor.{ ActorRef, ActorSystem }
import akka.http.scaladsl.model.{ Uri => HttpUri }
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.conductr.client.ConductRController
import com.typesafe.conductr.client.ConductRController.{ LoadBundle, RunBundle, StopBundle, UnloadBundle }
import org.scalactic.{ Accumulation, Bad, Good, One, Or }
import play.api.libs.json.{ JsError, JsSuccess, JsString, Json }
import sbt._
import scala.concurrent.Await
import scala.util.{ Failure, Success }

private[conductr] object ConductR {
  import com.typesafe.sbt.bundle.SbtBundle.autoImport._
  import ConductRKeys._

  final val DefaultConductrProtocol = "http"
  final val DefaultConductrHost = "127.0.0.1"
  final val DefaultConductrPort = 9005
  val conductrAttrKey = AttributeKey[ActorRef]("sbt-conductr")
  val actorSystemAttrKey = AttributeKey[ActorSystem]("sbt-conductr-actor-system")

  def loadBundle(bundle: URI, config: Option[URI], stm: String, roles: Set[String],
    loadTimeout: Timeout, state: State): String =
    {
      def get[A](key: SettingKey[A]) =
        Project.extract(state).getOpt(key)
          .fold(Bad(One(s"Setting ${key.key.label} must be defined!")): A Or One[String])(Good(_))
      def doLoadBundle(nrOfCpus: Double, memory: Bytes, diskSpace: Bytes) = {
        withConductRController(state) { conductr =>
          state.log.info("Loading bundle to ConductR ...")
          val request =
            LoadBundle(
              HttpUri(bundle.toString),
              config map (u => HttpUri(u.toString)),
              stm,
              nrOfCpus,
              memory.underlying,
              diskSpace.underlying,
              roles
            )
          val response = conductr.ask(request)(loadTimeout).mapTo[String]
          Await.ready(response, loadTimeout.duration)
          response.value.get match {
            case Success(s) =>
              (Json.parse(s) \ "bundleId").validate[String] match {
                case JsSuccess(bundleId, _) =>
                  state.log.info(s"Upload completed. Use 'conduct run $bundleId' to run.")
                  bundleId
                case e: JsError =>
                  sys.error(s"Unexpected response: $e")
              }
            case Failure(e) =>
              sys.error(s"Problem loading the bundle: ${e.getMessage}")
          }
        }
      }
      Accumulation.withGood(get(BundleKeys.nrOfCpus), get(BundleKeys.memory), get(BundleKeys.diskSpace))(doLoadBundle).fold(
        identity,
        errors => sys.error(errors.mkString(f"%n"))
      )
    }

  def runBundle(bundleId: String, scale: Option[Int],
    requestTimeout: Timeout, state: State): String =
    withConductRController(state) { conductr =>
      state.log.info(s"Running bundle $bundleId ...")
      val response = conductr.ask(RunBundle(bundleId, scale.getOrElse(1)))(requestTimeout).mapTo[String]
      Await.ready(response, requestTimeout.duration)
      response.value.get match {
        case Success(s) =>
          (Json.parse(s) \ "requestId").validate[String] match {
            case JsSuccess(requestId, _) =>
              state.log.info(s"Request for running has been delivered with id: $requestId")
              requestId
            case e: JsError =>
              sys.error(s"Unexpected response: $e")
          }
        case Failure(e) =>
          sys.error(s"Problem running the bundle: ${e.getMessage}")
      }
    }

  def stopBundle(bundleId: String, requestTimeout: Timeout, state: State): String =
    withConductRController(state) { conductr =>
      state.log.info(s"Stopping all bundle $bundleId instances ...")
      val response = conductr.ask(StopBundle(bundleId))(requestTimeout).mapTo[String]
      Await.ready(response, requestTimeout.duration)
      response.value.get match {
        case Success(s) =>
          (Json.parse(s) \ "requestId").validate[String] match {
            case JsSuccess(requestId, _) =>
              state.log.info(s"Request for stopping has been delivered with id: $requestId")
              requestId
            case e: JsError =>
              sys.error(s"Unexpected response: $e")
          }
        case Failure(e) =>
          sys.error(s"Problem stopping the bundle: ${e.getMessage}")
      }
    }

  def unloadBundleTask(bundleId: String, requestTimeout: Timeout, state: State): String =
    withConductRController(state) { conductr =>
      state.log.info(s"Unloading bundle $bundleId ...")
      val response = conductr.ask(UnloadBundle(bundleId))(requestTimeout).mapTo[String]
      Await.ready(response, requestTimeout.duration)
      response.value.get match {
        case Success(s) =>
          (Json.parse(s) \ "requestId").validate[String] match {
            case JsSuccess(requestId, _) =>
              state.log.info(s"Request for unloading has been delivered with id: $requestId")
              requestId
            case e: JsError =>
              sys.error(s"Unexpected response: $e")
          }
        case Failure(e) =>
          sys.error(s"Problem unloading the bundle: ${e.getMessage}")
      }
    }

  def info(s: State): Unit =
    withActorSystem(s)(withConductRController(s)(console.Console.bundleInfo(refresh = false)))

  def events(bundleId: String, s: State): Unit =
    println("This command is not yet available. Consult ConductR logs.")
  //withActorSystem(s)(withConductRController(s)(console.Console.events(bundleId, refresh = false))) FIXME

  def logs(bundleId: String, s: State): Unit =
    println("This command is not yet available. Consult your application logs.")
  //withActorSystem(s)(withConductRController(s)(console.Console.logs(bundleId, refresh = false))) FIXME

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

  // Actor system management and API

  def loadActorSystem(state: State): State =
    state.get(actorSystemAttrKey).fold {
      state.log.debug(s"Creating actor system and storing it under key [${actorSystemAttrKey.label}]")
      val system = withActorSystemClassloader(ActorSystem("sbt-conductr"))
      state.put(actorSystemAttrKey, system)
    }(_ => state)

  def unloadActorSystem(state: State): State =
    state.get(actorSystemAttrKey).fold(state) { system =>
      system.shutdown()
      state.remove(actorSystemAttrKey)
    }

  def loadConductRController(state: State): State =
    state.get(conductrAttrKey).fold {
      state.log.debug(s"Creating ConductRController actor and storing it under key [${conductrAttrKey.label}]")
      val conductr = withActorSystem(state) { implicit system =>
        val extracted = Project.extract(state)
        val settings = extracted.structure.data
        val conductr =
          for {
            conductrUrl <- (conductrControlServerUrl in Global).get(settings)
            loggingQueryUrl <- (conductrLoggingQueryUrl in Global).get(settings)
            connectTimeout <- (conductrConnectTimeout in Global).get(settings)
          } yield {
            state.log.info(s"Control Protocol set for $conductrUrl. Use `controlServer` to set an alternate address.")
            system.actorOf(ConductRController.props(HttpUri(conductrUrl.toString), HttpUri(loggingQueryUrl.toString), connectTimeout))
          }
        conductr.getOrElse(sys.error("Cannot establish the ConductRController actor: Check that you have conductrControlServerUrl and conductrConnectTimeout settings!"))
      }
      state.put(conductrAttrKey, conductr)
    }(as => state)

  def unloadConductRController(state: State): State =
    state.get(conductrAttrKey).fold(state)(_ => state.remove(conductrAttrKey))

  // We will get an exception if there is no known actor system - which is a good thing because
  // there absolutely has to be at this point.
  def withActorSystem[T](state: State)(block: ActorSystem => T): T =
    block(state.get(actorSystemAttrKey).get)

  // We will get an exception if there is no actor representing the ConductR - which is a good thing because
  // there needs to be and it is probably because the plugin has been mis-configured.
  def withConductRController[T](state: State)(block: ActorRef => T): T =
    block(state.get(conductrAttrKey).get)

  def withActorSystemClassloader[A](action: => A): A = {
    val newLoader = ActorSystem.getClass.getClassLoader
    val thread = Thread.currentThread
    val oldLoader = thread.getContextClassLoader
    thread.setContextClassLoader(newLoader)
    try
      action
    finally
      thread.setContextClassLoader(oldLoader)
  }
}
