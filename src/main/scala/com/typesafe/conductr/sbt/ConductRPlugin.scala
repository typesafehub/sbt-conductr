/*
 * Copyright © 2014 Typesafe, Inc. All rights reserved.
 */

package com.typesafe.conductr.sbt

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

      ConductRKeys.conductrApiVersion := "1.0"
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

  def resolveDefaultHostIp: String =
    Try("docker-machine ip default".!!.trim.reverse.takeWhile(_ != ' ').reverse).getOrElse {
      Try("boot2docker ip".!!.trim.reverse.takeWhile(_ != ' ').reverse).getOrElse {
        "hostname".!!.trim
      }
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
      val apiVersion = ConductRKeys.conductrApiVersion.value
      val state = Keys.state.value
      val loadTimeout = ConductRKeys.conductrLoadTimeout.value
      val requestTimeout = ConductRKeys.conductrRequestTimeout.value

      Parsers.subtask.parsed match {
        case HelpSubtask                           => conductUsage
        case LoadSubtask(bundle, config)           => ConductR.loadBundle(apiVersion, bundle, config, loadTimeout, state)
        case RunSubtask(bundleId, scale, affinity) => ConductR.runBundle(apiVersion, bundleId, scale, affinity, requestTimeout, state)
        case StopSubtask(bundleId)                 => ConductR.stopBundle(apiVersion, bundleId, requestTimeout, state)
        case UnloadSubtask(bundleId)               => ConductR.unloadBundleTask(apiVersion, bundleId, requestTimeout, state)
        case InfoSubtask                           => ConductR.info(apiVersion, state)
        case EventsSubtask(bundleId, lines)        => ConductR.events(apiVersion, bundleId, lines, state)
        case LogsSubtask(bundleId, lines)          => ConductR.logs(apiVersion, bundleId, lines, state)
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
}
