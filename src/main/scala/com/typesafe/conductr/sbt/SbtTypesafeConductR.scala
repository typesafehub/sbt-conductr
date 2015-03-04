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
import com.typesafe.sbt.bundle.SbtBundle
import com.typesafe.sbt.packager.Keys._
import org.scalactic.{ Accumulation, Bad, Good, One, Or }
import play.api.libs.json.{ JsString, Json }
import sbt._
import sbt.Keys._
import sbt.complete.DefaultParsers._
import sbt.complete.Parser
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.util.{ Failure, Success }

object Import {

  val loadBundle = inputKey[String]("Loads a bundle and an optional configuration to the ConductR")
  val startBundle = inputKey[String]("Starts a bundle given a bundle id with an optional scale")
  val stopBundle = inputKey[String]("Stops a bundle given a bundle id")
  val unloadBundle = inputKey[String]("Unloads a bundle given a bundle id")

  object ConductRKeys {
    val discoveredDist = TaskKey[File]("conductr-discovered-dist", "Any distribution produced by the current project")
    val conductrUrl = SettingKey[URL]("conductr-url", "The URL of the ConductR. Defaults to 'http://127.0.0.1:9005'")
    val conductrConnectTimeout = SettingKey[Timeout]("conductr-connect-timeout", "The timeout for ConductR communications when connecting")
    val conductrLoadTimeout = SettingKey[Timeout]("conductr-load-timeout", "The timeout for ConductR communications when loading")
    val conductrRequestTimeout = SettingKey[Timeout]("conductr-request-timeout", "The timeout for ConductR communications when requesting")
  }
}

/**
 * An sbt plugin that interact's with Typesafe ConductR's controller and potentially other components.
 */
object SbtTypesafeConductR extends AutoPlugin {

  import Import._
  import Import.ConductRKeys._
  import SbtBundle.autoImport._
  import sbinary.DefaultProtocol.FileFormat

  val autoImport = Import

  override def `requires`: Plugins =
    plugins.CorePlugin

  override def globalSettings: Seq[Setting[_]] =
    super.globalSettings ++ List(
      onLoad := onLoad.value.andThen(loadActorSystem).andThen(loadConductRController),
      onUnload := (unloadConductRController _).andThen(unloadActorSystem).andThen(onUnload.value),
      conductrUrl := new URL(s"http://127.0.0.1:9005"),
      conductrConnectTimeout := 30.seconds
    )

  override def projectSettings: Seq[Setting[_]] =
    List(
      commands ++= Seq(bundleInfo, conductr),
      discoveredDist <<= (dist in Bundle).storeAs(discoveredDist in Global).triggeredBy(dist in Bundle),
      loadBundle := loadBundleTask.value.evaluated,
      BundleKeys.system := (packageName in Universal).value,
      BundleKeys.roles := Set.empty,
      startBundle := startBundleTask.value.evaluated,
      stopBundle := stopBundleTask.value.evaluated,
      unloadBundle := unloadBundleTask.value.evaluated,
      conductrRequestTimeout in Global := 30.seconds,
      conductrLoadTimeout in Global := 10.minutes
    )

  // Input parsing and action

  private object Parsers {
    def bundle(bundle: Option[File]): Parser[URI] =
      Space ~> token(Uri(bundle.fold[Set[URI]](Set.empty)(f => Set(f.toURI))))

    def configuration: Parser[URI] = Space ~> token(basicUri)

    def bundleId(x: Seq[String]): Parser[String] = Space ~> (StringBasic examples (x: _*))

    def loadBundle = Defaults.loadForParser(discoveredDist in Global)((s, b) => bundle(b) ~ configuration.?)

    def scale: Parser[Int] = Space ~> IntBasic

    def startBundle = bundleId(List("fixme")) ~ scale.? // FIXME: Should default to last loadBundle result

    def stopBundle = bundleId(List("fixme")) // FIXME: Should default to last bundle started

    def unloadBundle = bundleId(Nil) // FIXME: Should default to last bundle loaded
  }

  private def bundleInfo: Command = Command.args("bundleInfo", "Refresh screen: -r") { (state, flags) =>
    withActorSystem(state)(withConductRController(state)(Console.bundleInfo(flags contains "-r")))
    state
  }

  private def conductr: Command = Command.single("conductr") { (prevState, url) =>
    val extracted = Project.extract(prevState)
    extracted.append(Seq(conductrUrl in Global := new URL(url)), prevState)
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
          val response = conductr.ask(request)(conductrLoadTimeout.value).mapTo[String]
          Await.ready(response, conductrLoadTimeout.value.duration)
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
      val (bundleId, scale) = Parsers.startBundle.parsed
      withConductRController(state.value) { conductr =>
        streams.value.log.info(s"Starting bundle $bundleId ...")
        val response = conductr.ask(StartBundle(bundleId, scale.getOrElse(1)))(conductrRequestTimeout.value).mapTo[String]
        Await.ready(response, conductrRequestTimeout.value.duration)
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

  private def stopBundleTask: Def.Initialize[InputTask[String]] =
    Def.inputTask {
      val bundleId = Parsers.stopBundle.parsed
      withConductRController(state.value) { conductr =>
        streams.value.log.info(s"Stopping all bundle $bundleId instances ...")
        val response = conductr.ask(StopBundle(bundleId))(conductrRequestTimeout.value).mapTo[String]
        Await.ready(response, conductrRequestTimeout.value.duration)
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
        val response = conductr.ask(UnloadBundle(bundleId))(conductrRequestTimeout.value).mapTo[String]
        Await.ready(response, conductrRequestTimeout.value.duration)
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

  // Actor system management and API

  private val actorSystemAttrKey = AttributeKey[ActorSystem]("sbt-typesafe-conductr-actor-system")

  private def loadActorSystem(state: State): State =
    state.get(actorSystemAttrKey).fold {
      state.log.debug(s"Creating actor system and storing it under key [${actorSystemAttrKey.label}]")
      val system = withActorSystemClassloader(ActorSystem("sbt-typesafe-conductr"))
      state.put(actorSystemAttrKey, system)
    }(_ => state)

  private def unloadActorSystem(state: State): State =
    state.get(actorSystemAttrKey).fold(state) { system =>
      system.shutdown()
      state.remove(actorSystemAttrKey)
    }

  private val conductrAttrKey = AttributeKey[ActorRef]("sbt-typesafe-conductr")

  private def loadConductRController(state: State): State =
    state.get(conductrAttrKey).fold {
      state.log.debug(s"Creating ConductRController actor and storing it under key [${conductrAttrKey.label}]")
      val conductr = withActorSystem(state) { implicit system =>
        val extracted = Project.extract(state)
        val settings = extracted.structure.data
        val conductr =
          for {
            url <- (conductrUrl in Global).get(settings)
            connectTimeout <- (conductrConnectTimeout in Global).get(settings)
          } yield system.actorOf(ConductRController.props(HttpUri(url.toString), connectTimeout))
        conductr.getOrElse(sys.error("Cannot establish the ConductRController actor: Check that you have conductrUrl and conductrConnectTimeout settings!"))
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
