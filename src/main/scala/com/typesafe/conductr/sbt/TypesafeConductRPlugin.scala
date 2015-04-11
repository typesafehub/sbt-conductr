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
import TypesafeConductR._

/**
 * An sbt plugin that interact's with Typesafe ConductR's controller and potentially other components.
 */
object TypesafeConductRPlugin extends AutoPlugin {
  import TypesafeConductRKeys._
  import com.typesafe.sbt.bundle.SbtBundle.autoImport._
  import sbinary.DefaultProtocol.FileFormat

  object autoImport {
    val conductr = TypesafeConductRKeys.conductr
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
      conductr := conductrTask.value.evaluated,
      conductrDiscoveredDist <<= (dist in Bundle).storeAs(conductrDiscoveredDist in Global).triggeredBy(dist in Bundle),
      BundleKeys.system := (packageName in Universal).value,
      BundleKeys.roles := Set.empty,
      conductrRequestTimeout := 30.seconds,
      conductrLoadTimeout := 10.minutes
    )

  private[sbt] sealed trait ConductrSubtask
  private[sbt] case class LoadSubtask(bundle: URI, config: Option[URI]) extends ConductrSubtask
  private[sbt] case class StartSubtask(bundleId: String, scale: Option[Int]) extends ConductrSubtask
  private[sbt] case class StopSubtask(bundleId: String) extends ConductrSubtask
  private[sbt] case class UnloadSubtask(bundleId: String) extends ConductrSubtask
  private[sbt] case class ControlServerSubtask(conductrHost: URL) extends ConductrSubtask
  private[sbt] case class InfoSubtask() extends ConductrSubtask

  // Input parsing and action
  private[sbt] object Parsers {
    lazy val subtask: Def.Initialize[State => Parser[Option[ConductrSubtask]]] =
      Defaults.loadForParser(conductrDiscoveredDist in Global)((s, b) =>
        (Space ~> (loadSubtask(b) | startSubtask | stopSubtask | unloadSubtask | controlServerSubtask | infoSubtask))?
      )
    def loadSubtask(b: Option[File]): Parser[LoadSubtask] =
      (token("load") ~> Space ~> bundle(b) ~ configuration.?) map { case (b, config) => LoadSubtask(b, config) }
    def startSubtask: Parser[StartSubtask] =
      // FIXME: Should default to last loadBundle result
      (token("start") ~> Space ~> bundleId(List("fixme")) ~ scale.?) map { case (b, scale) => StartSubtask(b, scale) }
    def stopSubtask: Parser[StopSubtask] =
      // FIXME: Should default to last bundle started
      (token("stop") ~> Space ~> bundleId(List("fixme"))) map { case b => StopSubtask(b) }
    def unloadSubtask: Parser[UnloadSubtask] =
      // FIXME: Should default to last bundle loaded
      (token("unload") ~> Space ~> bundleId(Nil)) map { case b => UnloadSubtask(b) }
    def controlServerSubtask: Parser[ControlServerSubtask] =
      token("controlServer") ~> Space ~> token(parseIpAddress).examples(s"$DefaultConductrHost:$DefaultConductrPort") map {
        case s => ControlServerSubtask(prepareConductrUrl(s))
      }
    def infoSubtask: Parser[InfoSubtask] =
      (token("info") map { case _ => InfoSubtask() })

    def bundle(bundle: Option[File]): Parser[URI] =
      token(Uri(bundle.fold[Set[URI]](Set.empty)(f => Set(f.toURI))))

    def configuration: Parser[URI] = Space ~> token(basicUri)

    def bundleId(x: Seq[String]): Parser[String] = Space ~> (StringBasic examples (x: _*))

    def scale: Parser[Int] = Space ~> IntBasic

    /** Naive ip address parser: parses 3 digit repeats with dots and optional port  */
    def parseIpAddress: Parser[String] = {
      def ipGroup: Parser[String] = Parser.repeat(Digit, 1, 3).map(intList => intList.mkString(""))
      def ipGroupDot: Parser[String] = (ipGroup ~ literal('.')) map { case (a, b) => a + b }
      def port: Parser[String] = (literal(':') ~
        Parser.repeat(Digit, 1, 6).map(intList => intList.mkString(""))) map { case (a, b) => a +: b }
      ipGroupDot ~ ipGroupDot ~ ipGroupDot ~ ipGroup ~ port.? map { case ((((a, b), c), d), e) => a + b + c + d + e.getOrElse("") }
    }
  }

  private def conductrTask: Def.Initialize[InputTask[Unit]] =
    Def.inputTask {
      val s = state.value
      val log = sLog.value
      val stm = BundleKeys.system.value
      val roles = BundleKeys.roles.value
      val loadTimeout = conductrLoadTimeout.value
      val requestTimeout = conductrRequestTimeout.value
      val subtaskOpt: Option[ConductrSubtask] = Parsers.subtask.parsed
      subtaskOpt match {
        case Some(LoadSubtask(b, config)) =>
          TypesafeConductR.loadBundle(b, config, stm, roles, loadTimeout, s, log)
        case Some(StartSubtask(b, scale)) =>
          TypesafeConductR.startBundle(b, scale, requestTimeout, s, log)
        case Some(StopSubtask(b)) =>
          TypesafeConductR.stopBundle(b, requestTimeout, s, log)
        case Some(UnloadSubtask(b)) =>
          TypesafeConductR.unloadBundleTask(b, requestTimeout, s, log)
        case Some(ControlServerSubtask(host)) =>
          TypesafeConductR.setControlServer(host, s, log)
        case Some(InfoSubtask()) =>
          TypesafeConductR.info(s)
        case None =>
          println("Usage: conductr <subtask>")
      }
    }
}
