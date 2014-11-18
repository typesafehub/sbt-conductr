/*
 * Copyright © 2014 Typesafe, Inc. All rights reserved.
 */

package com.typesafe.reactiveruntime.sbt

import akka.actor.{ ActorRef, ActorSystem }
import akka.http.Http
import akka.http.model.{ Uri => HttpUri }
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.reactiveruntime.ConductorController
import com.typesafe.reactiveruntime.ConductorController.{ LoadBundle, StartBundle, StopBundle, UnloadBundle }
import com.typesafe.reactiveruntime.console.Console
import com.typesafe.sbt.bundle.SbtBundle
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

  val loadBundle = inputKey[String]("Loads a bundle and an optional configuration to the conductor")
  val startBundle = inputKey[String]("Starts a bundle given a bundle id with an optional scale")
  val stopBundle = inputKey[String]("Stops a bundle given a bundle id")
  val unloadBundle = inputKey[String]("Unloads a bundle given a bundle id")

  object ReactiveRuntimeKeys {
    val nrOfCpus = SettingKey[Double]("rr-nr-of-cpus", "The number of cpus required to run the bundle.")
    val memory = SettingKey[Long]("rr-memory", "The amount of memory required to run the bundle.")
    val diskSpace = SettingKey[Long]("rr-disk-space", "The amount of disk space required to host an expanded bundle and configuration.")
    val roles = SettingKey[Set[String]]("rr-roles", "The types of node in the cluster that this bundle can be deployed to.")

    val discoveredDist = TaskKey[File]("rr-discovered-dist", "Any distribution produced by the current project")
    val conductorUrl = SettingKey[URL]("rr-conductor-url", "The URL of the Conductor. Defaults to 'http://${HOSTNAME}:9005' (if HOSTNAME is defined) or  'http://127.0.0.1:9005'")
    val conductorConnectTimeout = SettingKey[Timeout]("rr-conductor-connect-timeout", "The timeout for conductor communications when connecting")
    val conductorLoadTimeout = SettingKey[Timeout]("rr-conductor-load-timeout", "The timeout for conductor communications when loading")
    val conductorRequestTimeout = SettingKey[Timeout]("rr-conductor-request-timeout", "The timeout for conductor communications when requesting")
  }
}

/**
 * An sbt plugin that interact's with Reactive Runtime's conductor and potentially other components.
 */
object SbtReactiveRuntime extends AutoPlugin {

  import Import._
  import Import.ReactiveRuntimeKeys._
  import SbtBundle.autoImport._
  import sbinary.DefaultProtocol.FileFormat

  val autoImport = Import

  override def requires: Plugins =
    plugins.CorePlugin

  override def globalSettings: Seq[Setting[_]] =
    super.globalSettings ++ List(
      onLoad := onLoad.value.andThen(loadActorSystem).andThen(loadConductorController),
      onUnload := (unloadConductorController _).andThen(unloadActorSystem).andThen(onUnload.value),
      conductorRequestTimeout := 30.seconds,
      conductorLoadTimeout := 10.minutes
    )

  override def projectSettings: Seq[Setting[_]] =
    List(
      commands += bundleInfo,
      discoveredDist <<= (dist in ReactiveRuntime).storeAs(discoveredDist in Global).triggeredBy(dist in ReactiveRuntime),
      loadBundle := loadBundleTask.value.evaluated,
      roles := Set.empty,
      startBundle := startBundleTask.value.evaluated,
      stopBundle := stopBundleTask.value.evaluated,
      unloadBundle := unloadBundleTask.value.evaluated,
      conductorUrl := new URL(s"http://${Option(System.getenv("HOSTNAME")).getOrElse("127.0.0.1")}:9005"),
      conductorConnectTimeout := 30.seconds
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

  private def bundleInfo = Command.command("bundleInfo") { state =>
    withActorSystem(state)(withConductorController(state)(Console.bundleInfo))
    state
  }

  private def loadBundleTask: Def.Initialize[InputTask[String]] =
    Def.inputTask {
      def get[A](key: SettingKey[A]) =
        Project.extract(state.value).getOpt(key)
          .fold(Bad(One(s"Setting ${key.key.label} must be defined!")): A Or One[String])(Good(_))
      def loadBundle(nrOfCpus: Double, memory: Long, diskSpace: Long) = {
        val (bundle, config) = Parsers.loadBundle.parsed
        withConductorController(state.value) { conductor =>
          streams.value.log.info("Loading bundle to Conductor ...")
          val request =
            LoadBundle(
              HttpUri(bundle.toString),
              config map (u => HttpUri(u.toString)),
              nrOfCpus,
              memory,
              diskSpace,
              roles.value
            )
          val response = conductor.ask(request)(conductorLoadTimeout.value).mapTo[String]
          Await.ready(response, conductorLoadTimeout.value.duration)
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
      Accumulation.withGood(get(nrOfCpus), get(memory), get(diskSpace))(loadBundle).fold(
        identity,
        errors => sys.error(errors.mkString(f"%n"))
      )
    }

  private def startBundleTask: Def.Initialize[InputTask[String]] =
    Def.inputTask {
      val (bundleId, scale) = Parsers.startBundle.parsed
      withConductorController(state.value) { conductor =>
        streams.value.log.info(s"Starting bundle $bundleId ...")
        val response = conductor.ask(StartBundle(bundleId, scale.getOrElse(1)))(conductorRequestTimeout.value).mapTo[String]
        Await.ready(response, conductorRequestTimeout.value.duration)
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
      withConductorController(state.value) { conductor =>
        streams.value.log.info(s"Stopping all bundle $bundleId instances ...")
        val response = conductor.ask(StopBundle(bundleId))(conductorRequestTimeout.value).mapTo[String]
        Await.ready(response, conductorRequestTimeout.value.duration)
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
      withConductorController(state.value) { conductor =>
        streams.value.log.info(s"Unloading bundle $bundleId ...")
        val response = conductor.ask(UnloadBundle(bundleId))(conductorRequestTimeout.value).mapTo[String]
        Await.ready(response, conductorRequestTimeout.value.duration)
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

  private val actorSystemAttrKey = AttributeKey[ActorSystem]("sbt-reactive-runtime-actor-system")

  private def loadActorSystem(state: State): State =
    state.get(actorSystemAttrKey).fold {
      state.log.debug(s"Creating actor system and storing it under key [${actorSystemAttrKey.label}]")
      val system = withActorSystemClassloader(ActorSystem("sbt-reactive-runtime"))
      state.put(actorSystemAttrKey, system)
    }(_ => state)

  private def unloadActorSystem(state: State): State =
    state.get(actorSystemAttrKey).fold(state) { system =>
      system.shutdown()
      state.remove(actorSystemAttrKey)
    }

  private val conductorAttrKey = AttributeKey[ActorRef]("sbt-reactive-runtime-conductor")

  private def loadConductorController(state: State): State =
    state.get(conductorAttrKey).fold {
      state.log.debug(s"Creating ConductorController actor and storing it under key [${conductorAttrKey.label}]")
      val conductor = withActorSystem(state) { implicit system =>
        val extracted = Project.extract(state)
        val settings = extracted.structure.data
        val conductor =
          for {
            url <- (conductorUrl in extracted.currentRef).get(settings)
            connectTimeout <- (conductorConnectTimeout in extracted.currentRef).get(settings)
          } yield system.actorOf(ConductorController.props(HttpUri(url.toString), connectTimeout, akka.io.IO(Http)))
        conductor.getOrElse(sys.error("Cannot establish the ConductorController actor: Check that you have conductorUrl and conductorConnectTimeout settings!"))
      }
      state.put(conductorAttrKey, conductor)
    }(as => state)

  private def unloadConductorController(state: State): State =
    state.get(conductorAttrKey).fold(state)(_ => state.remove(conductorAttrKey))

  // We will get an exception if there is no known actor system - which is a good thing because
  // there absolutely has to be at this point.
  private def withActorSystem[T](state: State)(block: ActorSystem => T): T =
    block(state.get(actorSystemAttrKey).get)

  // We will get an exception if there is no actor representing the conductor - which is a good thing because
  // there needs to be and it is probably because the plugin has been mis-configured.
  private def withConductorController[T](state: State)(block: ActorRef => T): T =
    block(state.get(conductorAttrKey).get)

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
