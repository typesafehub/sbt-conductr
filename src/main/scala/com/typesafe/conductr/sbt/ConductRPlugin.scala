/*
 * Copyright © 2014 Typesafe, Inc. All rights reserved.
 */

package com.typesafe.conductr.sbt

import sbt.Keys._
import sbt._
import sbt.complete.DefaultParsers._
import sbt.complete.Parser

import com.typesafe.sbt.SbtNativePackager
import com.typesafe.sbt.packager.Keys._
import com.typesafe.sbt.packager.universal.UniversalPlugin
import com.typesafe.sbt.packager.archetypes.JavaAppPackaging
import scala.concurrent.duration.DurationInt
import language.postfixOps
import ConductR._

/**
 * An sbt plugin that interact's with Typesafe ConductR's controller and potentially other components.
 */
object ConductRPlugin extends AutoPlugin {
  import ConductRKeys._
  import com.typesafe.sbt.bundle.SbtBundle.autoImport._
  import sbinary.DefaultProtocol.FileFormat

  object autoImport {
    val conduct = ConductRKeys.conduct
  }

  override def trigger = allRequirements

  override def globalSettings: Seq[Setting[_]] =
    super.globalSettings ++ List(
      onLoad := onLoad.value.andThen(loadActorSystem).andThen(loadConductRController),
      onUnload := (unloadConductRController _).andThen(unloadActorSystem).andThen(onUnload.value),

      aggregate in conduct := false,

      dist in Bundle := file(""),
      dist in BundleConfiguration := file(""),

      conductrConnectTimeout := 30.seconds,
      conductrLoadTimeout := 10.minutes,
      conductrRequestTimeout := 30.seconds,

      conductrControlServerUrl := envUrl("CONDUCTR_IP", DefaultConductrHost, "CONDUCTR_PORT", DefaultConductrPort, DefaultConductrProtocol),
      conductrLoggingQueryUrl := envUrl("LOGGING_QUERY_IP", DefaultConductrHost, "LOGGING_QUERY_PORT", DefaultConductrPort, DefaultConductrProtocol)
    )

  override def projectSettings: Seq[Setting[_]] =
    super.projectSettings ++ List(
      commands ++= Seq(controlServer),
      conduct := conductTask.value.evaluated,

      conductrDiscoveredDist <<=
        (dist in Bundle).storeAs(conductrDiscoveredDist)
        .triggeredBy(dist in Bundle),
      conductrDiscoveredConfigDist <<=
        (dist in BundleConfiguration).storeAs(conductrDiscoveredConfigDist)
        .triggeredBy(dist in BundleConfiguration)
    )

  // Input parsing and action

  private def controlServer: Command = Command.single("controlServer") { (prevState, url) =>
    val extracted = Project.extract(prevState)
    extracted.append(Seq(conductrControlServerUrl in Global := prepareConductrUrl(url)), prevState)
  }

  private object Parsers {
    lazy val subtask: Def.Initialize[State => Parser[Option[ConductSubtask]]] = {
      val init = Def.value { (bundle: Option[File], bundleConfig: Option[File]) =>
        (Space ~> (
          loadSubtask(bundle, bundleConfig) |
          runSubtask |
          stopSubtask |
          unloadSubtask |
          infoSubtask |
          eventsSubtask |
          logsSubtask)) ?
      }
      (resolvedScoped, init) { (ctx, parser) =>
        s: State =>
          val bundle = loadFromContext(conductrDiscoveredDist, ctx, s)
          val bundleConfig = loadFromContext(conductrDiscoveredConfigDist, ctx, s)
          parser(bundle, bundleConfig)
      }
    }
    def loadSubtask(availableBundle: Option[File], availableBundleConfiguration: Option[File]): Parser[LoadSubtask] =
      (token("load") ~> Space ~> bundle(availableBundle) ~
        bundleConfiguration(availableBundleConfiguration).?) map { case (b, config) => LoadSubtask(b, config) }
    def runSubtask: Parser[RunSubtask] =
      // FIXME: Should default to last loadBundle result
      (token("run") ~> Space ~> bundleId(List("fixme")) ~ scale.?) map { case (b, scale) => RunSubtask(b, scale) }
    def stopSubtask: Parser[StopSubtask] =
      // FIXME: Should default to last bundle started
      (token("stop") ~> Space ~> bundleId(List("fixme"))) map { case b => StopSubtask(b) }
    def unloadSubtask: Parser[UnloadSubtask] =
      // FIXME: Should default to last bundle loaded
      (token("unload") ~> Space ~> bundleId(List("fixme"))) map { case b => UnloadSubtask(b) }
    def infoSubtask: Parser[InfoSubtask.type] =
      token("info") map { case _ => InfoSubtask }
    def eventsSubtask: Parser[EventsSubtask] =
      (token("events") ~> Space ~> bundleId(List("fixme"))) map { case b => EventsSubtask(b) }
    def logsSubtask: Parser[LogsSubtask] =
      (token("logs") ~> Space ~> bundleId(List("fixme"))) map { case b => LogsSubtask(b) }

    def bundle(bundle: Option[File]): Parser[URI] =
      token(Uri(bundle.fold[Set[URI]](Set.empty)(f => Set(f.toURI))))

    def bundleConfiguration(bundleConf: Option[File]): Parser[URI] = Space ~> bundle(bundleConf)

    def bundleId(x: Seq[String]): Parser[String] = StringBasic examples (x: _*)

    def scale: Parser[Int] = Space ~> IntBasic
  }

  private sealed trait ConductSubtask
  private case class LoadSubtask(bundle: URI, config: Option[URI]) extends ConductSubtask
  private case class RunSubtask(bundleId: String, scale: Option[Int]) extends ConductSubtask
  private case class StopSubtask(bundleId: String) extends ConductSubtask
  private case class UnloadSubtask(bundleId: String) extends ConductSubtask
  private case object InfoSubtask extends ConductSubtask
  private case class EventsSubtask(bundleId: String) extends ConductSubtask
  private case class LogsSubtask(bundleId: String) extends ConductSubtask

  private def conductTask: Def.Initialize[InputTask[Unit]] =
    Def.inputTask {
      val s = state.value
      val loadTimeout = conductrLoadTimeout.value
      val requestTimeout = conductrRequestTimeout.value
      val subtaskOpt: Option[ConductSubtask] = Parsers.subtask.parsed
      subtaskOpt match {
        case Some(LoadSubtask(b, config)) => ConductR.loadBundle(b, config, loadTimeout, s)
        case Some(RunSubtask(b, scale))   => ConductR.runBundle(b, scale, requestTimeout, s)
        case Some(StopSubtask(b))         => ConductR.stopBundle(b, requestTimeout, s)
        case Some(UnloadSubtask(b))       => ConductR.unloadBundleTask(b, requestTimeout, s)
        case Some(InfoSubtask)            => ConductR.info(s)
        case Some(EventsSubtask(b))       => ConductR.events(b, s)
        case Some(LogsSubtask(b))         => ConductR.logs(b, s)
        case None                         => println("Usage: conduct <subtask>")
      }
    }
}
