/*
 * Copyright © 2014 Typesafe, Inc. All rights reserved.
 */

package com.typesafe.conductr.sbt

import akka.actor.{ ActorSystem }
import com.typesafe.conductr.akka.ConnectionContext
import com.typesafe.conductr.clientlib.akka.ControlClient
import sbt._
import sbt.complete.DefaultParsers._
import sbt.complete.Parser
import com.typesafe.sbt.packager.Keys._
import scala.concurrent.duration.DurationInt
import language.postfixOps
import ConductRClient._
import scala.util.Try

/**
 * An sbt plugin that interact's with Typesafe ConductR's controller and potentially other components.
 */
object ConductRPlugin extends AutoPlugin {
  import com.typesafe.sbt.bundle.SbtBundle.autoImport._
  import Import._
  import sbinary.DefaultProtocol.FileFormat

  val autoImport = Import

  val actorSystemAttrKey = AttributeKey[ActorSystem]("sbt-conductr-actor-system")

  override def trigger = allRequirements

  override def globalSettings: Seq[Setting[_]] =
    super.globalSettings ++ List(
      Keys.onLoad := Keys.onLoad.value.andThen(loadActorSystem),
      Keys.onUnload := (unloadActorSystem _).andThen(Keys.onUnload.value),

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
      val state = Keys.state.value
      val loadTimeout = ConductRKeys.conductrLoadTimeout.value
      val requestTimeout = ConductRKeys.conductrRequestTimeout.value

      withActorSystem(state) { implicit actorSystem =>
        val conductrUrl = new java.net.URL(ConductRKeys.conductrControlServerUrl.value.toString)
        implicit val cc = ConnectionContext()
        implicit val log = state.log
        val conductrClient = initConductrClient(conductrUrl)
        Parsers.subtask.parsed match {
          case HelpSubtask                           => conductUsage
          case LoadSubtask(bundle, config)           => conductrClient.loadBundle(bundle, config, loadTimeout)
          case RunSubtask(bundleId, scale, affinity) => conductrClient.runBundle(bundleId, scale, affinity, requestTimeout)
          case StopSubtask(bundleId)                 => conductrClient.stopBundle(bundleId, requestTimeout)
          case UnloadSubtask(bundleId)               => conductrClient.unloadBundle(bundleId, requestTimeout)
          case InfoSubtask                           => conductrClient.info(requestTimeout)
          case EventsSubtask(bundleId, lines)        => conductrClient.events(bundleId, lines, requestTimeout)
          case LogsSubtask(bundleId, lines)          => conductrClient.logs(bundleId, lines, requestTimeout)
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

  private def initConductrClient(conductrUrl: URL)(implicit system: ActorSystem, cc: ConnectionContext, log: Logger): ConductRClient = {
    log.debug(s"Instantiating ConductR client")
    val controlClient = ControlClient(conductrUrl)
    new ConductRClient(controlClient)
  }

  // We will get an exception if there is no known actor system - which is a good thing because
  // there absolutely has to be at this point.
  private def withActorSystem[T](state: State)(block: ActorSystem => T): T =
    block(state.get(actorSystemAttrKey).get)

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
