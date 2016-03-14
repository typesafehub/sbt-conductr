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
      ConductRKeys.conduct := conductTask.value.evaluated,

      ConductRKeys.conductrDiscoveredDist <<=
        (dist in Bundle).storeAs(ConductRKeys.conductrDiscoveredDist)
        .triggeredBy(dist in Bundle),
      ConductRKeys.conductrDiscoveredConfigDist <<=
        (dist in BundleConfiguration).storeAs(ConductRKeys.conductrDiscoveredConfigDist)
        .triggeredBy(dist in BundleConfiguration)
    )

  private[sbt] object Parsers {
    lazy val subtask: Def.Initialize[State => Parser[ConductSubtask]] = {
      val init = Def.value { (bundle: Option[File], bundleConfig: Option[File], bundleNames: Set[String]) =>
        (Space ~> (
          helpSubtask |
          subHelpSubtask |
          versionSubtask |
          loadSubtask(bundle, bundleConfig) |
          runSubtask(bundleNames) |
          stopSubtask(bundleNames) |
          unloadSubtask(bundleNames) |
          infoSubtask |
          servicesSubtask |
          eventsSubtask(bundleNames) |
          logsSubtask(bundleNames)
        )) ?? ConductHelp
      }
      (Keys.resolvedScoped, init) { (ctx, parser) =>
        s: State =>
          val bundle = loadFromContext(ConductRKeys.conductrDiscoveredDist, ctx, s)
          val bundleConfig = loadFromContext(ConductRKeys.conductrDiscoveredConfigDist, ctx, s)
          val bundleNames: Set[String] =
            withProcessHandling {
              (Process("conduct info") #| Process(Seq("awk", "{print $2}")) #| Process(Seq("awk", "{if(NR>1)print}"))).lines(NoProcessLogging).toSet
            }(Set.empty)
          parser(bundle, bundleConfig, bundleNames)
      }
    }

    // Conduct help command (conduct --help)
    def helpSubtask: Parser[ConductHelp.type] =
      (hideAutoCompletion("-h") | token("--help"))
        .map { case _ => ConductHelp }
        .!!! { "Usage: conduct --help" }

    // This parser is triggering the help of the conduct sub command if no option for this command is specified
    // Example: `conduct load` will execute `conduct load --help`
    def subHelpSubtask: Parser[ConductSubtaskHelp] =
      (token("load") | token("run") | token("stop") | token("unload") | token("events") | token("logs"))
        .map(ConductSubtaskHelp)

    // Sub command parsers
    def versionSubtask: Parser[ConductSubtaskSuccess] =
      (token("version") ~> commonOpts.?)
        .map { case opts => ConductSubtaskSuccess("version", optionalArgs(opts)) }
        .!!!("Usage: conduct version")

    def loadSubtask(availableBundle: Option[File], availableBundleConfiguration: Option[File]): Parser[ConductSubtaskSuccess] =
      token("load") ~> withOpts(loadOpts)(bundle(availableBundle) ~ bundle(availableBundleConfiguration).?)
        .mapOpts { case (opts, (bundle, config)) => ConductSubtaskSuccess("load", optionalArgs(opts) ++ Seq(bundle.toString) ++ optionalArgs(config)) }
        .!!! { "Usage: conduct load --help" }
    def loadOpts = hideAutoCompletion(commonOpts | resolveCacheDir | waitTimeout | noWait).*.map(seqToString).?

    def runSubtask(bundleNames: Set[String]): Parser[ConductSubtaskSuccess] =
      token("run") ~> withOpts(runOpts(bundleNames))(bundleId(bundleNames))
        .mapOpts { case (opts, bundle) => ConductSubtaskSuccess("run", optionalArgs(opts) ++ Seq(bundle)) }
        .!!!("Usage: conduct run --help")
    def runOpts(bundleNames: Set[String]) = hideAutoCompletion(commonOpts | waitTimeout | noWait | scale | affinity(bundleNames)).*.map(seqToString).?

    def stopSubtask(bundleNames: Set[String]): Parser[ConductSubtaskSuccess] =
      token("stop") ~> withOpts(stopOpts)(bundleId(bundleNames))
        .mapOpts { case (opts, bundleId) => ConductSubtaskSuccess("stop", optionalArgs(opts) ++ Seq(bundleId)) }
        .!!!("Usage: conduct stop --help")
    def stopOpts = (waitTimeout | noWait).examples("--no-wait", "--wait-timeout").*.map(seqToString).?

    def unloadSubtask(bundleNames: Set[String]): Parser[ConductSubtaskSuccess] =
      token("unload") ~> withOpts(unloadOpts)(bundleId(bundleNames))
        .mapOpts { case (opts, bundleId) => ConductSubtaskSuccess("unload", optionalArgs(opts) ++ Seq(bundleId)) }
        .!!!("Usage: conduct unload --help")
    def unloadOpts = hideAutoCompletion(commonOpts | waitTimeout | noWait).*.map(seqToString).?

    def infoSubtask: Parser[ConductSubtaskSuccess] =
      token("info" ~> infoOpts)
        .map { case opts => ConductSubtaskSuccess("info", optionalArgs(opts)) }
        .!!!("Usage: conduct info")
    def infoOpts = hideAutoCompletion(commonOpts).*.map(seqToString).?

    def servicesSubtask: Parser[ConductSubtaskSuccess] =
      token("services" ~> servicesOpts)
        .map { case opts => ConductSubtaskSuccess("services", optionalArgs(opts)) }
        .!!!("Usage: conduct services")
    def servicesOpts = hideAutoCompletion(commonOpts).*.map(seqToString).?

    def eventsSubtask(bundleNames: Set[String]): Parser[ConductSubtaskSuccess] =
      (token("events") ~> withOpts(eventsOpts)(bundleId(bundleNames)))
        .mapOpts { case (opts, bundleId) => ConductSubtaskSuccess("events", optionalArgs(opts) ++ Seq(bundleId)) }
        .!!!("Usage: conduct events --help")
    def eventsOpts = hideAutoCompletion(commonOpts | waitTimeout | noWait | date | utc | lines).*.map(seqToString).?

    def logsSubtask(bundleNames: Set[String]): Parser[ConductSubtaskSuccess] =
      token("logs") ~> withOpts(logsOpts)(bundleId(bundleNames))
        .mapOpts { case (opts, bundleId) => ConductSubtaskSuccess("logs", optionalArgs(opts) ++ Seq(bundleId)) }
        .!!!("Usage: conduct logs --help")
    def logsOpts = hideAutoCompletion(commonOpts | waitTimeout | noWait | date | utc | lines).*.map(seqToString).?

    // Command specific options
    def bundle(file: Option[File]): Parser[URI] =
      Space ~> (basicUri examples file.fold[Set[String]](Set.empty)(f => Set(f.toURI.getPath)))
    def bundleId(bundleNames: Set[String]): Parser[String] =
      Space ~> (StringBasic examples bundleNames)
    def scale: Parser[String] =
      (Space ~> token("--scale" ~ positiveNumber)).map(pairToString)
    def affinity(bundleNames: Set[String]): Parser[String] =
      (Space ~> token("--affinity" ~ bundleId(bundleNames))).map(pairToString)
    def lines: Parser[String] =
      (Space ~> (token("-n" ~ positiveNumber) | token("--lines" ~ positiveNumber))).map(pairToString)
    def date: Parser[String] =
      Space ~> token("--date")
    def utc: Parser[String] =
      Space ~> token("--utc")
    def resolveCacheDir: Parser[String] =
      (Space ~> token("--resolve-cache-dir" ~ basicString)).map(pairToString)
    def waitTimeout: Parser[String] =
      (Space ~> token("--wait-timeout" ~ positiveNumber)).map(pairToString)
    def noWait: Parser[String] =
      Space ~> token("--no-wait")

    // Common optional options
    def commonOpts: Parser[String] =
      (
        help |
        quiet |
        verbose |
        longsIds |
        localConnection |
        apiVersion |
        ip |
        port |
        settingsDir |
        customSettingsFile |
        customPluginsDirs
      )
    def help: Parser[String] = Space ~> (token("--help") | token("-h"))
    def quiet: Parser[String] = Space ~> token("-q")
    def verbose: Parser[String] = Space ~> (token("-v") | token("--verbose"))
    def longsIds: Parser[String] = Space ~> token("--long-ids")
    def localConnection: Parser[String] = Space ~> token("--local-connection")
    def apiVersion: Parser[String] = (Space ~> token("--api-version" ~ positiveNumber)).map(pairToString)
    def ip: Parser[String] = (Space ~> (token("-i" ~ basicString) | token("--ip" ~ basicString))).map(pairToString)
    def port: Parser[String] = (Space ~> (token("-p" ~ positiveNumber) | token("--port" ~ positiveNumber))).map(pairToString)
    def settingsDir: Parser[String] = (Space ~> token("--settings-dir" ~ basicString)).map(pairToString)
    def customSettingsFile: Parser[String] = (Space ~> token("--custom-settings-file" ~ basicString)).map(pairToString)
    def customPluginsDirs: Parser[String] = (Space ~> token("--custom-plugins-dir" ~ basicString)).map(pairToString)

    // Option helpers
    def withOpts[T](optionalOpts: Parser[Option[String]])(positionalOpts: Parser[T]): Parser[Either[(Option[String], T), (T, Option[String])]] =
      ((optionalOpts ~ positionalOpts) || (positionalOpts ~ optionalOpts))
    implicit class ParserOps[T](parser: Parser[Either[(Option[String], T), (T, Option[String])]]) {
      def mapOpts(f: (Option[String], T) => ConductSubtaskSuccess): Parser[ConductSubtaskSuccess] =
        parser map {
          case Left((optionalOpts, positionalOpts))  => f(optionalOpts, positionalOpts)
          case Right((positionalOpts, optionalOpts)) => f(optionalOpts, positionalOpts)
        }
    }

    // Utility parsers
    def basicString: Parser[String] =
      Space ~> StringBasic
    def positiveNumber: Parser[Int] =
      Space ~> NatBasic

    // Hide auto completion in sbt session for the given parser
    def hideAutoCompletion[T](parser: Parser[T]): Parser[T] =
      token(parser, hide = _ => true)

    // Convert Tuple[A,B] to String by using a whitespace separator
    private def pairToString[A, B](pair: (A, B)): String =
      s"${pair._1} ${pair._2}"

    // Convert Seq[String] to String by using a whitespace separator
    private def seqToString(seq: Seq[String]): String =
      seq.mkString(" ")
  }

  private[sbt] sealed trait ConductSubtask
  private[sbt] case class ConductSubtaskSuccess(command: String, args: Seq[String]) extends ConductSubtask
  private[sbt] case object ConductHelp extends ConductSubtask
  private[sbt] case class ConductSubtaskHelp(command: String) extends ConductSubtask

  private def conductTask: Def.Initialize[InputTask[Unit]] = Def.inputTask {
    verifyCliInstallation()

    Parsers.subtask.parsed match {
      case ConductHelp                          => conductHelp()
      case ConductSubtaskHelp(command)          => conductSubHelp(command)
      case ConductSubtaskSuccess(command, args) => conduct(command, args)
    }
  }

  private def verifyCliInstallation(): Unit =
    withProcessHandling {
      s"conduct".!(NoProcessLogging)
    }(sys.error(s"The conductr-cli has not been installed. Follow the instructions on http://conductr.lightbend.com/docs/$LatestConductrDocVersion/CLI to install the CLI."))

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

  private def withProcessHandling[T](block: => T)(exceptionHandler: => T): T =
    try {
      block
    } catch {
      case ioe: IOException => exceptionHandler
    }

  private object NoProcessLogging extends ProcessLogger {
    override def info(s: => String): Unit = ()
    override def error(s: => String): Unit = ()
    override def buffer[T](f: => T): T = f
  }
}
