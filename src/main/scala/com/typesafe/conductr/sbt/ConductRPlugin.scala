/*
 * Copyright © 2014 Typesafe, Inc. All rights reserved.
 */

package com.typesafe.conductr.sbt

import sbt.Keys._
import sbt._
import sbt.complete.DefaultParsers._
import sbt.complete.Parser

import com.typesafe.sbt.SbtNativePackager
import SbtNativePackager.Universal
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

  override def `requires`: Plugins = SbtNativePackager && UniversalPlugin && JavaAppPackaging

  override def globalSettings: Seq[Setting[_]] =
    super.globalSettings ++ List(
      onLoad := onLoad.value.andThen(loadActorSystem).andThen(loadConductRController),
      onUnload := (unloadConductRController _).andThen(unloadActorSystem).andThen(onUnload.value),
      conductrControlServerUrl := envConductrUrl getOrElse new URL(s"$DefaultConductrProtocol://$DefaultConductrHost:$DefaultConductrPort"),
      conductrConnectTimeout := 30.seconds
    )

  override def projectSettings: Seq[Setting[_]] =
    List(
      commands ++= Seq(controlServer),
      conduct := conductTask.value.evaluated,
      conductrDiscoveredDist <<=
        (dist in Bundle).storeAs(conductrDiscoveredDist in Global)
          .triggeredBy(dist in Bundle),
      conductrDiscoveredConfigDist <<=
        (dist in BundleConfiguration).storeAs(conductrDiscoveredConfigDist in Global)
        .triggeredBy(dist in BundleConfiguration),
      BundleKeys.system := (packageName in Universal).value,
      BundleKeys.roles := Set.empty,
      conductrRequestTimeout := 30.seconds,
      conductrLoadTimeout := 10.minutes
    )

  // Input parsing and action

  private def controlServer: Command = Command.single("controlServer") { (prevState, url) =>
    val extracted = Project.extract(prevState)
    extracted.append(Seq(conductrControlServerUrl in Global := prepareConductrUrl(url)), prevState)
  }

  private object Parsers {
    lazy val subtask: Def.Initialize[State => Parser[Option[ConductSubtask]]] =
      Defaults.loadForParser(conductrDiscoveredDist in Global) { (state, bundle) =>
        val configSetting = conductrDiscoveredConfigDist in Global
        val bundleConfig = Defaults.loadFromContext(configSetting, configSetting.scopedKey, state)
        (Space ~> (loadSubtask(bundle, bundleConfig) | runSubtask | stopSubtask | unloadSubtask | infoSubtask)) ?
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
    def infoSubtask: Parser[InfoSubtask] =
      token("info") map { case _ => InfoSubtask() }

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
  private case class InfoSubtask() extends ConductSubtask

  private def conductTask: Def.Initialize[InputTask[Unit]] =
    Def.inputTask {
      val s = state.value
      val stm = BundleKeys.system.value
      val roles = BundleKeys.roles.value
      val loadTimeout = conductrLoadTimeout.value
      val requestTimeout = conductrRequestTimeout.value
      val subtaskOpt: Option[ConductSubtask] = Parsers.subtask.parsed
      subtaskOpt match {
        case Some(LoadSubtask(b, config)) =>
          ConductR.loadBundle(b, config, stm, roles, loadTimeout, s)
        case Some(RunSubtask(b, scale)) =>
          ConductR.runBundle(b, scale, requestTimeout, s)
        case Some(StopSubtask(b)) =>
          ConductR.stopBundle(b, requestTimeout, s)
        case Some(UnloadSubtask(b)) =>
          ConductR.unloadBundleTask(b, requestTimeout, s)
        case Some(InfoSubtask()) =>
          ConductR.info(s)
        case None =>
          println("Usage: conduct <subtask>")
      }
    }
}
