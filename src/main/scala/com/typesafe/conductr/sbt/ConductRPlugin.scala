/*
 * Copyright © 2014 Typesafe, Inc. All rights reserved.
 */

package com.typesafe.conductr.sbt

import akka.actor.{ ActorRef, ActorSystem }
import akka.http.scaladsl.model.{ Uri => HttpUri }
import com.typesafe.conductr.client.ConductRController
import sbt._
import sbt.complete.DefaultParsers._
import sbt.complete.Parser
import com.typesafe.sbt.packager.Keys._
import scala.concurrent.duration.DurationInt
import language.postfixOps
import ConductR._
import scala.util.Try

/**
 * An sbt plugin that interact's with Typesafe ConductR's controller and potentially other components.
 */
object ConductRPlugin extends AutoPlugin {
  import com.typesafe.sbt.bundle.SbtBundle.autoImport._
  import Import._
  import sbinary.DefaultProtocol.FileFormat

  val autoImport = Import

  val conductrAttrKey = AttributeKey[ActorRef]("sbt-conductr")
  val actorSystemAttrKey = AttributeKey[ActorSystem]("sbt-conductr-actor-system")

  override def trigger = allRequirements

  override def globalSettings: Seq[Setting[_]] =
    super.globalSettings ++ List(
      Keys.onLoad := Keys.onLoad.value.andThen(loadActorSystem).andThen(loadConductRController),
      Keys.onUnload := (unloadConductRController _).andThen(unloadActorSystem).andThen(Keys.onUnload.value),

      Keys.aggregate in ConductRKeys.conduct := false,

      dist in Bundle := file(""),
      dist in BundleConfiguration := file(""),

      ConductRKeys.conductrConnectTimeout := 30.seconds,
      ConductRKeys.conductrLoadTimeout := 10.minutes,
      ConductRKeys.conductrRequestTimeout := 30.seconds,

      ConductRKeys.conductrControlServerUrl := envUrl("CONDUCTR_IP", DefaultConductrHost, "CONDUCTR_PORT", DefaultConductrPort, DefaultConductrProtocol),
      ConductRKeys.conductrLoggingQueryUrl := envUrl("LOGGING_QUERY_IP", DefaultConductrHost, "LOGGING_QUERY_PORT", DefaultConductrPort, DefaultConductrProtocol),

      ConductRKeys.conductrApiVersion := "1"
    )

  override def projectSettings: Seq[Setting[_]] =
    super.projectSettings ++ List(
      Keys.commands ++= Seq(controlServer),
      ConductRKeys.conduct := conductTask.value.evaluated,

      ConductRKeys.conductrDiscoveredDist <<=
        (dist in Bundle).storeAs(ConductRKeys.conductrDiscoveredDist)
        .triggeredBy(dist in Bundle),
      ConductRKeys.conductrDiscoveredConfigDist <<=
        (dist in BundleConfiguration).storeAs(ConductRKeys.conductrDiscoveredConfigDist)
        .triggeredBy(dist in BundleConfiguration)
    )

  def resolveDefaultHostIp(): String = {
    import com.typesafe.conductr.sbt.console.AnsiConsole.Implicits._
    def withDockerMachine(): String =
      "docker-machine ip default".!!.trim match {
        case "" =>
          println("Docker VM has not been started. Use 'docker-machine start default' in the terminal to start the VM and reload the sbt session.".asWarn)
          ""
        case ip => ip
      }
    def withBoot2Docker(): String =
      "boot2docker ip".!!.trim.reverse.takeWhile(_ != ' ').reverse
    def withHostname(): String =
      "hostname".!!.trim
    val WithLocalAddress: String =
      "127.0.0.1"

    Try(withDockerMachine())
      .getOrElse(Try(withBoot2Docker())
        .getOrElse(Try(withHostname())
          .getOrElse(WithLocalAddress)))
  }

  // Input parsing and action

  private def controlServer: Command = Command.single("controlServer") { (prevState, url) =>
    val extracted = Project.extract(prevState)
    extracted.append(Seq(ConductRKeys.conductrControlServerUrl in Global := prepareConductrUrl(url)), prevState)
  }

  private object Parsers {
    lazy val subtask: Def.Initialize[State => Parser[ConductSubtask]] = {
      val init = Def.value { (bundle: Option[File], bundleConfig: Option[File]) =>
        (Space ~> (
          helpSubtask |
          loadSubtask(bundle, bundleConfig) |
          runSubtask |
          stopSubtask |
          unloadSubtask |
          infoSubtask |
          eventsSubtask |
          logsSubtask
        )) ?? HelpSubtask
      }
      (Keys.resolvedScoped, init) { (ctx, parser) =>
        s: State =>
          val bundle = loadFromContext(ConductRKeys.conductrDiscoveredDist, ctx, s)
          val bundleConfig = loadFromContext(ConductRKeys.conductrDiscoveredConfigDist, ctx, s)
          parser(bundle, bundleConfig)
      }
    }
    def helpSubtask: Parser[HelpSubtask.type] =
      token("help")
        .map { case _ => HelpSubtask }
        .!!!("usage: conduct help")
    def loadSubtask(availableBundle: Option[File], availableBundleConfiguration: Option[File]): Parser[LoadSubtask] =
      (token("load") ~> Space ~> bundle(availableBundle) ~ bundleConfiguration(availableBundleConfiguration).?)
        .map { case (b, config) => LoadSubtask(b, config) }
        .!!!("usage: conduct load BUNDLE")
    def runSubtask: Parser[RunSubtask] =
      // FIXME: Should default to last loadBundle result
      (token("run") ~> Space ~> bundleId(List("fixme")) ~ scale.? ~ affinity.?)
        .map { case ((b, scale), affinity) => RunSubtask(b, scale, affinity) }
        .!!!("usage: conduct run BUNDLE_ID [--scale SCALE] [--affinity BUNDLE_ID]")
    def stopSubtask: Parser[StopSubtask] =
      // FIXME: Should default to last bundle started
      (token("stop") ~> Space ~> bundleId(List("fixme")))
        .map { case b => StopSubtask(b) }
        .!!!("usage: conduct stop BUNDLE_ID")
    def unloadSubtask: Parser[UnloadSubtask] =
      // FIXME: Should default to last bundle loaded
      (token("unload") ~> Space ~> bundleId(List("fixme")))
        .map { case b => UnloadSubtask(b) }
        .!!!("usage: conduct unload BUNDLE")
    def infoSubtask: Parser[InfoSubtask.type] =
      token("info")
        .map { case _ => InfoSubtask }
        .!!!("usage: conduct info")
    def eventsSubtask: Parser[EventsSubtask] =
      (token("events") ~> Space ~> bundleId(List("fixme")) ~ lines.?)
        .map { case (b, lines) => EventsSubtask(b, lines) }
        .!!!("usage: conduct events BUNDLE_ID [--lines LINES]")
    def logsSubtask: Parser[ConductSubtask] =
      (token("logs") ~> Space ~> bundleId(List("fixme")) ~ lines.?)
        .map { case (b, lines) => LogsSubtask(b, lines) }
        .!!!("usage: conduct logs BUNDLE_ID [--lines LINES]")

    def bundle(bundle: Option[File]): Parser[URI] =
      token(Uri(bundle.fold[Set[URI]](Set.empty)(f => Set(f.toURI))))

    def bundleConfiguration(bundleConf: Option[File]): Parser[URI] = Space ~> bundle(bundleConf)

    def bundleId(x: Seq[String]): Parser[String] = StringBasic examples (x: _*)

    def positiveNumber: Parser[Int] = Space ~> NatBasic

    def scale: Parser[Int] = Space ~> "--scale" ~> positiveNumber

    def affinity: Parser[String] = Space ~> "--affinity" ~> Space ~> bundleId(List("fixme"))

    def lines: Parser[Int] = Space ~> "--lines" ~> positiveNumber
  }

  private sealed trait ConductSubtask
  private case object HelpSubtask extends ConductSubtask
  private case class LoadSubtask(bundle: URI, config: Option[URI]) extends ConductSubtask
  private case class RunSubtask(bundleId: String, scale: Option[Int], affinity: Option[String]) extends ConductSubtask
  private case class StopSubtask(bundleId: String) extends ConductSubtask
  private case class UnloadSubtask(bundleId: String) extends ConductSubtask
  private case object InfoSubtask extends ConductSubtask
  private case class EventsSubtask(bundleId: String, lines: Option[Int]) extends ConductSubtask
  private case class LogsSubtask(bundleId: String, lines: Option[Int]) extends ConductSubtask

  private def conductTask: Def.Initialize[InputTask[Unit]] =
    Def.inputTask {
      implicit val apiVersion = toApiVersion(ConductRKeys.conductrApiVersion.value)
      val state = Keys.state.value
      val loadTimeout = ConductRKeys.conductrLoadTimeout.value
      val requestTimeout = ConductRKeys.conductrRequestTimeout.value
      implicit val log = state.log

      withActorSystem(state) { implicit actorSystem =>
        withConductRController(state) { implicit conductrController =>
          Parsers.subtask.parsed match {
            case HelpSubtask                           => conductUsage
            case LoadSubtask(bundle, config)           => ConductR.loadBundle(bundle, config, loadTimeout)
            case RunSubtask(bundleId, scale, affinity) => ConductR.runBundle(bundleId, scale, affinity, requestTimeout)
            case StopSubtask(bundleId)                 => ConductR.stopBundle(bundleId, requestTimeout)
            case UnloadSubtask(bundleId)               => ConductR.unloadBundleTask(bundleId, requestTimeout)
            case InfoSubtask                           => ConductR.info()
            case EventsSubtask(bundleId, lines)        => ConductR.events(bundleId, lines)
            case LogsSubtask(bundleId, lines)          => ConductR.logs(bundleId, lines)
          }
        }
      }
    }

  private def conductUsage = {
    val output =
      s"""
         |usage: conduct {help, info, services, load, run, stop, unload, events, logs}
         |
         |subcommands:
         |  help                print usage information of conduct command
         |  info                print bundle information
         |  services            print service information
         |  load                load a bundle
         |  run                 run a bundle
         |  stop                stop a bundle
         |  unload              unload a bundle
         |  events              show bundle events
         |  logs                show bundle logs
       """.stripMargin
    println(output)
  }

  // Actor system management and API

  private def loadActorSystem(state: State): State =
    state.get(actorSystemAttrKey).fold {
      state.log.debug(s"Creating actor system and storing it under key [${actorSystemAttrKey.label}]")
      val system = withActorSystemClassloader(ActorSystem("sbt-conductr"))
      state.put(actorSystemAttrKey, system)
    }(_ => state)

  private def unloadActorSystem(state: State): State =
    state.get(actorSystemAttrKey).fold(state) { system =>
      system.shutdown()
      state.remove(actorSystemAttrKey)
    }

  private def loadConductRController(state: State): State =
    state.get(conductrAttrKey).fold {
      state.log.debug(s"Creating ConductRController actor and storing it under key [${conductrAttrKey.label}]")
      val conductr = withActorSystem(state) { implicit system =>
        val extracted = Project.extract(state)
        val settings = extracted.structure.data
        val conductr =
          for {
            conductrUrl <- (ConductRKeys.conductrControlServerUrl in Global).get(settings)
            loggingQueryUrl <- (ConductRKeys.conductrLoggingQueryUrl in Global).get(settings)
            connectTimeout <- (ConductRKeys.conductrConnectTimeout in Global).get(settings)
          } yield {
            state.log.info(s"Control Protocol set for $conductrUrl. Use 'controlServer {ip-address}' to set an alternate address.")
            system.actorOf(ConductRController.props(HttpUri(conductrUrl.toString), HttpUri(loggingQueryUrl.toString), connectTimeout))
          }
        conductr.getOrElse(sys.error("Cannot establish the ConductRController actor: Check that you have conductrControlServerUrl and conductrConnectTimeout settings!"))
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
