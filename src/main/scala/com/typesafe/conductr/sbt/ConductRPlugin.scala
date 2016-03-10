/*
 * Copyright © 2016 Lightbend, Inc. All rights reserved.
 */

package com.typesafe.conductr.sbt

import sbt._
import sbt.complete.DefaultParsers._
import sbt.complete.Parser
import com.typesafe.sbt.packager.Keys._
import language.postfixOps
import java.io.IOException

/**
 * An sbt plugin that interact's with ConductR's controller and potentially other components.
 */
object ConductRPlugin extends AutoPlugin {
  import com.typesafe.sbt.bundle.SbtBundle.autoImport._
  import Import._
  import sbinary.DefaultProtocol.FileFormat

  val autoImport = Import

  override def trigger = allRequirements

  private final val LatestConductrDocVersion = "1.1.x"

  override def globalSettings: Seq[Setting[_]] =
    super.globalSettings ++ List(
      Keys.aggregate in ConductRKeys.conduct := false,

      dist in Bundle := file(""),
      dist in BundleConfiguration := file("")
    )

  override def projectSettings: Seq[Setting[_]] =
    super.projectSettings ++ List(
      Keys.commands ++= Seq.empty,
      ConductRKeys.conduct := conductTask.value.evaluated,

      ConductRKeys.conductrDiscoveredDist <<=
        (dist in Bundle).storeAs(ConductRKeys.conductrDiscoveredDist)
        .triggeredBy(dist in Bundle),
      ConductRKeys.conductrDiscoveredConfigDist <<=
        (dist in BundleConfiguration).storeAs(ConductRKeys.conductrDiscoveredConfigDist)
        .triggeredBy(dist in BundleConfiguration)
    )

  private object Parsers {
    lazy val subtask: Def.Initialize[State => Parser[ConductSubtask]] = {
      val init = Def.value { (bundle: Option[File], bundleConfig: Option[File]) =>
        (Space ~> (
          helpSubtask |
          subHelpSubtask |
          versionSubtask |
          loadSubtask(bundle, bundleConfig) |
          runSubtask |
          stopSubtask |
          unloadSubtask |
          infoSubtask |
          servicesSubtask |
          eventsSubtask |
          logsSubtask
        )) ?? ConductHelp
      }
      (Keys.resolvedScoped, init) { (ctx, parser) =>
        s: State =>
          val bundle = loadFromContext(ConductRKeys.conductrDiscoveredDist, ctx, s)
          val bundleConfig = loadFromContext(ConductRKeys.conductrDiscoveredConfigDist, ctx, s)
          parser(bundle, bundleConfig)
      }
    }

    // Conduct help command (conduct --help)
    def helpSubtask: Parser[ConductHelp.type] =
      token("--help")
        .map { case _ => ConductHelp }
        .!!! { "Usage: conduct --help" }

    // This parser is triggering the help of the conduct sub command if no option for this command is specified
    // Example: `conduct load` will execute `conduct load --help`
    def subHelpSubtask: Parser[ConductSubtaskHelp] =
      (token("load") | token("run") | token("stop") | token("unload") | token("events") | token("logs"))
        .map(ConductSubtaskHelp(_))

    // Sub command parsers
    def versionSubtask: Parser[ConductSubtaskSuccess] =
      (token("version") ~> commonOpts.?)
        .map { case opts => ConductSubtaskSuccess("version", optionalArgs(opts)) }
        .!!!("Usage: conduct version")

    def loadSubtask(availableBundle: Option[File], availableBundleConfiguration: Option[File]): Parser[ConductSubtaskSuccess] =
      (token("load") ~> commonOpts.? ~ (Space ~> bundle(availableBundle)) ~ bundleConfiguration(availableBundleConfiguration).?)
        .map { case ((opts, bundle), config) => ConductSubtaskSuccess("load", optionalArgs(opts) ++ Seq(bundle.toString) ++ optionalArgs(config)) }
        .!!! { "Usage: conduct load --help" }

    def runSubtask: Parser[ConductSubtaskSuccess] =
      // FIXME: Should default to last loadBundle result
      (token("run") ~> commonOpts.? ~ scale.? ~ affinity.? ~ (Space ~> bundleId(List("fixme"))))
        //.map { case ((b, scale), affinity) => RunSubtask(b, scale, affinity) }
        .map { case (((opts, scale), affinity), bundle) => ConductSubtaskSuccess("run", optionalArgs(opts) ++ optionalArgs(scale) ++ optionalArgs(affinity) ++ Seq(bundle)) }
        .!!!("Usage: conduct run --help")

    def stopSubtask: Parser[ConductSubtaskSuccess] =
      // FIXME: Should default to last bundle started
      (token("stop") ~> commonOpts.? ~ (Space ~> bundleId(List("fixme"))))
        .map { case (opts, bundleId) => ConductSubtaskSuccess("stop", optionalArgs(opts) ++ Seq(bundleId)) }
        .!!!("Usage: conduct stop --help")

    def unloadSubtask: Parser[ConductSubtaskSuccess] =
      // FIXME: Should default to last bundle loaded
      (token("unload") ~> commonOpts.? ~ (Space ~> bundleId(List("fixme"))))
        .map { case (opts, bundleId) => ConductSubtaskSuccess("unload", optionalArgs(opts) ++ Seq(bundleId)) }
        .!!!("Usage: conduct unload --help")

    def infoSubtask: Parser[ConductSubtaskSuccess] =
      token("info" ~> commonOpts.?)
        .map { case opts => ConductSubtaskSuccess("info", optionalArgs(opts)) }
        .!!!("Usage: conduct info")

    def servicesSubtask: Parser[ConductSubtaskSuccess] =
      token("services" ~> commonOpts.?)
        .map { case opts => ConductSubtaskSuccess("services", optionalArgs(opts)) }
        .!!!("Usage: conduct services")

    def eventsSubtask: Parser[ConductSubtaskSuccess] =
      (token("events") ~> commonOpts.? ~ lines.? ~ (Space ~> bundleId(List("fixme"))))
        .map { case ((opts, lines), bundleId) => ConductSubtaskSuccess("events", optionalArgs(opts) ++ optionalArgs(lines) ++ Seq(bundleId)) }
        .!!!("Usage: conduct events --help")

    def logsSubtask: Parser[ConductSubtaskSuccess] =
      (token("logs") ~> commonOpts.? ~ lines.? ~ (Space ~> bundleId(List("fixme"))))
        .map { case ((opts, lines), bundleId) => ConductSubtaskSuccess("logs", optionalArgs(opts) ++ optionalArgs(lines) ++ Seq(bundleId)) }
        .!!!("Usage: conduct logs --help")

    // Utility parser
    def basicString: Parser[String] =
      Space ~> StringBasic
    def positiveNumber: Parser[Int] =
      Space ~> NatBasic
    def bundleId(x: Seq[String]): Parser[String] =
      StringBasic examples (x: _*)

    // Common options
    def commonOpts: Parser[String] =
      hideAutoCompletion((help | quiet | ip | port | verbose | longsIds | apiVersion).*.map { case opts => opts.mkString(" ") })
    def help: Parser[String] = Space ~> "--help"
    def quiet: Parser[String] = Space ~> "-q"
    def verbose: Parser[String] = Space ~> "--verbose"
    def longsIds: Parser[String] = Space ~> "--long-ids"
    def apiVersion: Parser[String] = (Space ~> "--api-version" ~ positiveNumber).map(asString)
    def ip: Parser[String] = (Space ~> "--ip" ~ basicString).map(asString)
    def port: Parser[String] = (Space ~> "--port" ~ positiveNumber).map(asString)
    def settingsDir: Parser[String] = (Space ~> "--settings-dir" ~ basicString).map(asString)
    def customSettingsFile: Parser[String] = (Space ~> "--custom-settings-file" ~ basicString).map(asString)
    def customPluginsDirs: Parser[String] = (Space ~> "--custom-plugins-dir" ~ basicString).map(asString)
    def resolveCacheDir: Parser[String] = (Space ~> "--resolve-cache-dir" ~ basicString).map(asString)

    // Command specific options
    def bundle(bundle: Option[File]): Parser[URI] =
      token(basicUri examples (bundle.fold[Set[String]](Set.empty)(f => Set(f.toURI.getPath))))
    def bundleConfiguration(bundleConf: Option[File]): Parser[URI] =
      Space ~> bundle(bundleConf)
    def scale: Parser[String] =
      hideAutoCompletion(Space ~> "--scale" ~ positiveNumber).map(asString)
    def affinity: Parser[String] =
      hideAutoCompletion(Space ~> "--affinity" ~ (Space ~> bundleId(List("fixme")))).map(asString)
    def lines: Parser[String] =
      hideAutoCompletion(Space ~> "--lines" ~ positiveNumber).map(asString)

    // Hide auto completion in sbt session for the given parser
    def hideAutoCompletion[T](parser: Parser[T]): Parser[T] =
      token(parser, hide = _ => true)

    // Convert Tuple[A,B] to String by using a whitespace separator
    private def asString[A, B](pair: (A, B)): String =
      s"${pair._1} ${pair._2}"
  }

  private sealed trait ConductSubtask
  private case class ConductSubtaskSuccess(command: String, args: Seq[String]) extends ConductSubtask
  private case object ConductHelp extends ConductSubtask
  private case class ConductSubtaskHelp(command: String) extends ConductSubtask

  private def conductTask: Def.Initialize[InputTask[Unit]] = Def.inputTask {
    verifyCliInstallation()

    Parsers.subtask.parsed match {
      case ConductHelp                          => conductHelp()
      case ConductSubtaskHelp(command)          => conductSubHelp(command)
      case ConductSubtaskSuccess(command, args) => conduct(command, args)
    }
  }

  private def verifyCliInstallation(): Unit =
    try {
      s"conduct".!!
    } catch {
      case ioe: IOException =>
        sys.error(s"The conductr-cli has not been installed. Follow the instructions on http://conductr.lightbend.com/docs/$LatestConductrDocVersion/CLI to install the CLI.")
    }

  private def conductHelp(): Unit =
    s"conduct --help".!

  private def conductSubHelp(command: String): Unit =
    s"conduct $command --help".!

  private def conduct(command: String, args: Seq[String]): Unit =
    Process(Seq("conduct", command) ++ args).!

  // Converts optional arguments to a `Seq[String]`, meaning the `args` parameter can have 1 to n words
  // Each word is converted to a new element in the `Seq`
  // Example: Some("--help --verbose --scale 5") results in Seq("--help", "--verbose", "--scale", 5)
  // The returned format is ideal to use in `scala.sys.Process()`
  private def optionalArgs[T](args: Option[T]): Seq[String] =
    args.fold(Seq.empty[String])(_.toString.split(" "))
}
