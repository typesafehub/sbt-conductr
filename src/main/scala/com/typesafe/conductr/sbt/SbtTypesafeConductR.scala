/*
 * Copyright © 2014 Typesafe, Inc. All rights reserved.
 */

package com.typesafe.conductr.sbt

import java.net.URL

import akka.actor.{ ActorRef, ActorSystem }
import akka.http.model.{ Uri => HttpUri }
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.conductr.client.ConductRController
import com.typesafe.conductr.client.ConductRController.{ LoadBundle, StartBundle, StopBundle, UnloadBundle }
import com.typesafe.conductr.sbt.console.Console
import com.typesafe.sbt.SbtNativePackager.Universal
import com.typesafe.sbt.packager.Keys._
import org.scalactic.{ Accumulation, Bad, Good, One, Or }
import play.api.libs.json.{ JsString, Json }
import sbt.Keys._
import sbt._
import sbt.complete.DefaultParsers._
import sbt.complete.Parser

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.util.{ Failure, Success }

object Import {

  val Conductr = config("conductr")

  val controlServer = inputKey[sbt.URL]("Sets the ConductR Control Server's location (can be just IP, default port (9005) will be added automatically)")

  val load = inputKey[String]("Loads a bundle and an optional configuration to the ConductR")
  val start = inputKey[String]("Starts a bundle given a bundle id with an optional scale")
  val stop = inputKey[String]("Stops a bundle given a bundle id")
  val info = inputKey[Unit]("Shows information about bundles in conductr")
  val unload = inputKey[String]("Unloads a bundle given a bundle id")

  object Keys {
    val discoveredDist = TaskKey[File]("conductr-discovered-dist", "Any distribution produced by the current project")
    val controlServerUrl = SettingKey[URL]("conductr-url", "The URL of the ConductR. Defaults to the env variables 'CONDUCTR_IP:[CONDUCTR_PORT]', otherwise uses the default: 'http://127.0.0.1:9005'")
    val connectTimeout = SettingKey[Timeout]("conductr-connect-timeout", "The timeout for ConductR communications when connecting")
    val loadTimeout = SettingKey[Timeout]("conductr-load-timeout", "The timeout for ConductR communications when loading")
    val requestTimeout = SettingKey[Timeout]("conductr-request-timeout", "The timeout for ConductR communications when requesting")
  }
}

/**
 * An sbt plugin that interact's with Typesafe ConductR's controller and potentially other components.
 */
object SbtTypesafeConductR extends AutoPlugin {

  import com.typesafe.conductr.sbt.Import.Keys._
  import com.typesafe.conductr.sbt.Import._
  import com.typesafe.sbt.bundle.SbtBundle.autoImport._
  import sbinary.DefaultProtocol.FileFormat

  val autoImport = Import

  val DefaultConductrProtocol = "http"
  val DefaultConductrHost = "127.0.0.1"
  val DefaultConductrPort = 9005

  override def `requires`: Plugins =
    plugins.CorePlugin

  override def globalSettings: Seq[Setting[_]] =
    super.globalSettings ++ List(
      onLoad := onLoad.value.andThen(loadActorSystem).andThen(loadConductRController),
      onUnload := (unloadConductRController _).andThen(unloadActorSystem).andThen(onUnload.value),
      controlServerUrl := envConductrUrl getOrElse new URL(s"$DefaultConductrProtocol://$DefaultConductrHost:$DefaultConductrPort"),
      connectTimeout := 30.seconds
    )

  override def projectSettings: Seq[Setting[_]] =
    List(
      discoveredDist <<= (dist in Bundle).storeAs(discoveredDist in Global).triggeredBy(dist in Bundle),
      controlServer in Conductr := setConductrTask.value.evaluated,
      info in Conductr := infoTask.value,
      load in Conductr := loadBundleTask.value.evaluated,
      start in Conductr := startBundleTask.value.evaluated,
      stop in Conductr := stopBundleTask.value.evaluated,
      unload in Conductr := unloadBundleTask.value.evaluated,
      BundleKeys.system := (packageName in Universal).value,
      BundleKeys.roles := Set.empty,
      requestTimeout in Conductr := 30.seconds,
      loadTimeout in Conductr := 10.minutes
    )

  // Input parsing and action

  private object Parsers {
    def bundle(bundle: Option[File]): Parser[URI] =
      Space ~> token(Uri(bundle.fold[Set[URI]](Set.empty)(f => Set(f.toURI))))

    def setConductr: Parser[String] = Space ~> StringBasic

    def info: Parser[String] = Space ~> StringBasic

    def configuration: Parser[URI] = Space ~> token(basicUri)

    def bundleId(x: Seq[String]): Parser[String] = Space ~> (StringBasic examples (x: _*))

    def loadBundle = Defaults.loadForParser(discoveredDist in Global)((s, b) => bundle(b) ~ configuration.?)

    def scale: Parser[Int] = Space ~> IntBasic

    def startBundle = bundleId(List("fixme")) ~ scale.? // FIXME: Should default to last loadBundle result

    def stopBundle = bundleId(List("fixme")) // FIXME: Should default to last bundle started

    def unloadBundle = bundleId(Nil) // FIXME: Should default to last bundle loaded
  }

  private def setConductrTask: Def.Initialize[InputTask[sbt.URL]] =
    Def.inputTask {
      val urlString = Parsers.setConductr.parsed
      require(urlString.nonEmpty, "ConductR URL must NOT be empty!")
      prepareConductrUrl(urlString)
    }

  private[conductr] def prepareConductrUrl(url: String): sbt.URL = {
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

  private def loadBundleTask: Def.Initialize[InputTask[String]] =
    Def.inputTask {
      def get[A](key: SettingKey[A]) =
        Project.extract(state.value).getOpt(key)
          .fold(Bad(One(s"Setting ${key.key.label} must be defined!")): A Or One[String])(Good(_))
      def loadBundle(nrOfCpus: Double, memory: Bytes, diskSpace: Bytes) = {
        val (bundle, config) = Parsers.loadBundle.parsed
        withConductRController(state.value) { conductr =>
          streams.value.log.info("Loading bundle to ConductR ...")
          val request =
            LoadBundle(
              HttpUri(bundle.toString),
              config map (u => HttpUri(u.toString)),
              BundleKeys.system.value,
              nrOfCpus,
              memory.underlying,
              diskSpace.underlying,
              BundleKeys.roles.value
            )
          val response = conductr.ask(request)((loadTimeout in Conductr).value).mapTo[String]
          Await.ready(response, (loadTimeout in Conductr).value.duration)
          response.value.get match {
            case Success(s) =>
              Json.parse(s) \ "bundleId" match {
                case JsString(bundleId) =>
                  streams.value.log.info(s"Upload completed. Use 'startBundle $bundleId' to start.")
                  bundleId
                case other =>
                  sys.error(s"Unexpected response: $other")
              }
            case Failure(e) =>
              sys.error(s"Problem loading the bundle: ${e.getMessage}")
          }
        }
      }
      Accumulation.withGood(get(BundleKeys.nrOfCpus), get(BundleKeys.memory), get(BundleKeys.diskSpace))(loadBundle).fold(
        identity,
        errors => sys.error(errors.mkString(f"%n"))
      )
    }

  private def startBundleTask: Def.Initialize[InputTask[String]] =
    Def.inputTask {
      val log = streams.value.log
      val (bundleId, scale) = Parsers.startBundle.parsed

      withConductRController(state.value) { conductr =>
        log.info(s"Starting bundle $bundleId ...")
        val response = conductr.ask(StartBundle(bundleId, scale.getOrElse(1)))((requestTimeout in Conductr).value).mapTo[String]
        Await.ready(response, (requestTimeout in Conductr).value.duration)
        response.value.get match {
          case Success(s) =>
            Json.parse(s) \ "requestId" match {
              case JsString(requestId) =>
                streams.value.log.info(s"Request for starting has been delivered with id: $requestId")
                requestId
              case other =>
                sys.error(s"Unexpected response: $other")
            }
          case Failure(e) =>
            sys.error(s"Problem starting the bundle: ${e.getMessage}")
        }
      }
    }

  private def infoTask: Def.Initialize[Task[Unit]] =
    Def.task {
      val s = state.value
      //      val flags = Parsers.info.parsed
      //      withActorSystem(s)(withConductRController(s)(Console.bundleInfo(flags contains "-r")))
      withActorSystem(s)(withConductRController(s)(Console.bundleInfo(refresh = false)))
    }

  private def stopBundleTask: Def.Initialize[InputTask[String]] =
    Def.inputTask {
      val bundleId = Parsers.stopBundle.parsed
      withConductRController(state.value) { conductr =>
        streams.value.log.info(s"Stopping all bundle $bundleId instances ...")
        val response = conductr.ask(StopBundle(bundleId))((requestTimeout in Conductr).value).mapTo[String]
        Await.ready(response, (requestTimeout in Conductr).value.duration)
        response.value.get match {
          case Success(s) =>
            Json.parse(s) \ "requestId" match {
              case JsString(requestId) =>
                streams.value.log.info(s"Request for stopping has been delivered with id: $requestId")
                requestId
              case other =>
                sys.error(s"Unexpected response: $other")
            }
          case Failure(e) =>
            sys.error(s"Problem stopping the bundle: ${e.getMessage}")
        }
      }
    }

  private def unloadBundleTask: Def.Initialize[InputTask[String]] =
    Def.inputTask {
      val bundleId = Parsers.stopBundle.parsed
      withConductRController(state.value) { conductr =>
        streams.value.log.info(s"Unloading bundle $bundleId ...")
        val response = conductr.ask(UnloadBundle(bundleId))((requestTimeout in Conductr).value).mapTo[String]
        Await.ready(response, (requestTimeout in Conductr).value.duration)
        response.value.get match {
          case Success(s) =>
            Json.parse(s) \ "requestId" match {
              case JsString(requestId) =>
                streams.value.log.info(s"Request for unloading has been delivered with id: $requestId")
                requestId
              case other =>
                sys.error(s"Unexpected response: $other")
            }
          case Failure(e) =>
            sys.error(s"Problem unloading the bundle: ${e.getMessage}")
        }
      }
    }

  private def envConductrUrl(): Option[URL] = {
    val ip = sys.env.get("CONDUCTR_IP")
    val port = sys.env.getOrElse("CONDUCTR_PORT", 9005)

    ip.map(i => new URL(s"http://$i:$port"))
  }

  // Actor system management and API

  private val actorSystemAttrKey = AttributeKey[ActorSystem]("sbt-typesafe-setConductrTask-actor-system")

  private def loadActorSystem(state: State): State =
    state.get(actorSystemAttrKey).fold {
      state.log.debug(s"Creating actor system and storing it under key [${actorSystemAttrKey.label}]")
      val system = withActorSystemClassloader(ActorSystem("sbt-typesafe-set-conductr-task"))
      state.put(actorSystemAttrKey, system)
    }(_ => state)

  private def unloadActorSystem(state: State): State =
    state.get(actorSystemAttrKey).fold(state) { system =>
      system.shutdown()
      state.remove(actorSystemAttrKey)
    }

  private val conductrAttrKey = AttributeKey[ActorRef]("sbt-typesafe-set-conductr-task")

  private def loadConductRController(state: State): State =
    state.get(conductrAttrKey).fold {
      state.log.debug(s"Creating ConductRController actor and storing it under key [${conductrAttrKey.label}]")
      val conductr = withActorSystem(state) { implicit system =>
        val extracted = Project.extract(state)
        val settings = extracted.structure.data
        val conductr =
          for {
            url <- (controlServerUrl in Global).get(settings)
            connectTimeout <- (connectTimeout in Global).get(settings)
          } yield system.actorOf(ConductRController.props(HttpUri(url.toString), connectTimeout))
        conductr.getOrElse(sys.error("Cannot establish the ConductRController actor: Check that you have conductr:url and ConnectTimeout settings!"))
      }
      state.put(conductrAttrKey, conductr)
    }(as => state)

  private def unloadConductRController(state: State): State =
    state.get(conductrAttrKey).fold(state)(_ => state.remove(conductrAttrKey))

  // We will get an exception if there is no known actor system - which is a good thing because
  // there absolutely has to be at this point.
  private def withActorSystem[T](state: State)(block: ActorSystem => T): T =
    block(state.get(actorSystemAttrKey).get)

  // We will get an exception if there is no actor representing the ConductR - which is a good thing because
  // there needs to be and it is probably because the plugin has been mis-configured.
  private def withConductRController[T](state: State)(block: ActorRef => T): T =
    block(state.get(conductrAttrKey).get)

  private def withActorSystemClassloader[A](action: => A): A = {
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
