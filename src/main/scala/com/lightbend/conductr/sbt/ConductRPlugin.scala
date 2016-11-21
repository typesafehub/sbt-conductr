/*
 * Copyright © 2016 Lightbend, Inc. All rights reserved.
 */

package com.lightbend.conductr.sbt

import sbt._
import sbt.Keys._
import sbt.complete.DefaultParsers._
import sbt.complete.Parser
import sbt.ProcessLogger
import com.typesafe.sbt.packager.Keys._

import language.postfixOps
import java.io.IOException

import scala.annotation.tailrec
import scala.concurrent.TimeoutException
import scala.concurrent.duration._

/**
 * An sbt plugin that interact's with ConductR's controller and potentially other components.
 */
object ConductrPlugin extends AutoPlugin {
  import BundlePlugin.autoImport._
  import ConductrImport._
  import sbinary.DefaultProtocol.FileFormat

  val autoImport = ConductrImport
  import ConductrKeys._

  override def trigger = allRequirements

  override def globalSettings: Seq[Setting[_]] =
    List(
      dist in Bundle := file(""),
      dist in BundleConfiguration := file(""),

      hasRpLicense := {
        // Same logic as in https://github.com/typesafehub/reactive-platform
        // Doesn't take reactive-platform as a dependency because it is not public.
        val isMeta = (ConductrKeys.isSbtBuild in LocalRootProject).value
        val base = (Keys.baseDirectory in LocalRootProject).value
        val propFile = if (isMeta) base / TypesafePropertiesName else base / "project" / TypesafePropertiesName
        propFile.exists
      }
    )

  override def projectSettings: Seq[Setting[_]] =
    List(
      // Here we try to detect what binary universe we exist inside, so we can
      // accurately grab artifact revisions.
      isSbtBuild := Keys.sbtPlugin.?.value.getOrElse(false) && (Keys.baseDirectory in ThisProject).value.getName == "project",

      discoveredDist <<= (dist in Bundle).storeAs(discoveredDist).triggeredBy(dist in Bundle),
      discoveredConfigDist <<= (dist in BundleConfiguration).storeAs(discoveredConfigDist).triggeredBy(dist in BundleConfiguration),

      conduct := conductTask.value.evaluated
    )

  override def buildSettings: Seq[Setting[_]] =
    List(
      Keys.aggregate in conduct := false,
      Keys.aggregate in generateInstallationScript := false,
      Keys.aggregate in install := false,
      Keys.aggregate in sandbox := false,

      sandbox := sandboxTask.value.evaluated,
      sandboxRunTaskInternal := sandboxRunTask(ScopeFilter(inAnyProject, inAnyConfiguration)).value,

      installationData := installationDataTask.value,
      generateInstallationScript := generateInstallationScriptTask().value,
      install := installTask().value
    )

  private final val LatestConductrVersion = "1.1.11"
  private final val LatestConductrDocVersion = LatestConductrVersion.dropRight(1) :+ "x" // 1.0.0 to 1.0.x

  private final val TypesafePropertiesName = "typesafe.properties"
  private final val SandboxRunArgsAttrKey = AttributeKey[SandboxRunArgs]("conductr-sandbox-run-args")
  private final val sandboxRunTaskInternal = taskKey[Unit]("Internal Helper to call sandbox run task.")

  private def installationDataTask: Def.Initialize[Task[Seq[InstallationData]]] = Def.taskDyn {
    val rootProjectStructure = Project.extract(state.value).structure
    val allProjects = rootProjectStructure.allProjects
    val bundleInstallationScriptsTasks =
      allProjects.map { project =>
        val projectRef = ProjectRef(rootProjectStructure.root, project.id)

        val bundleConfiguration = project.configurations.find(c => c.name == Bundle.name)
        val bundleConfigurationConfiguration = project.configurations.find(c => c.name == BundleConfiguration.name)

        (bundleConfiguration, bundleConfigurationConfiguration) match {
          case (Some(b), Some(bc)) =>
            Def.taskDyn {
              val bundleName = (normalizedName in b in projectRef).value
              val bundleFile = (dist in b in projectRef).value.toPath
              val bundleConfigContents = (BundleKeys.bundleConf in bc in projectRef).value
              val bundleConfigSrcDir = (sourceDirectory in bc in projectRef).value / (BundleKeys.configurationName in bc in projectRef).value
              if (bundleConfigContents.nonEmpty || bundleConfigSrcDir.exists()) {
                Def.task {
                  val bundleConfigFile = (dist in bc in projectRef).value.toPath
                  Some(InstallationData(bundleName, Right(bundleFile), Some(bundleConfigFile))): Option[InstallationData]
                }
              } else {
                Def.task[Option[InstallationData]] {
                  Some(InstallationData(bundleName, Right(bundleFile), None))
                }
              }
            }
          case _ =>
            Def.taskDyn(Def.task[Option[InstallationData]](None))
        }
      }

    def fold(a: Seq[InstallationData])(tasks: Seq[Def.Initialize[Task[Option[InstallationData]]]]): Def.Initialize[Task[Seq[InstallationData]]] =
      tasks match {
        case Nil =>
          Def.task(a)
        case x :: xs =>
          Def.taskDyn {
            x.value match {
              case Some(v) => fold(a :+ v)(xs)
              case None    => fold(a)(xs)
            }
          }
      }

    fold(List.empty)(bundleInstallationScriptsTasks)
  }

  private def generateInstallationScriptTask(): Def.Initialize[Task[File]] = Def.task {
    val installDir = (target in LocalRootProject).value
    val installationScript = installDir / "install.sh"
    val installPath = installDir.toPath
    val installationScriptContents = installationData.value.map {
      case InstallationData(bundleName, bundle, bundleConfigFile) =>
        val bundleIdEnv = bundleName.replaceAll("\\W", "_").toUpperCase + "_BUNDLE_ID"
        val bundleArg = InstallationData.nameOrPath(
          bundle match {
            case Left(b)  => Left(b)
            case Right(b) => Right(installPath.relativize(b))
          }
        )
        val bundleConfigArg = bundleConfigFile.map(installPath.relativize).mkString("", "", " ")
        s"""
           |echo "Deploying $bundleName..."
           |$bundleIdEnv=$$(conduct load $bundleArg $bundleConfigArg--long-ids -q)
           |conduct run $${$bundleIdEnv} --no-wait -q
                 """.stripMargin
    }
    IO.write(
      installationScript,
      installationScriptContents.mkString(
        s"""#!/usr/bin/env bash
            |cd "$$( dirname "$${BASH_SOURCE[0]}" )"
            |""".stripMargin,
        "",
        """
          |echo 'Your system is deployed. Running "conduct info" to observe the cluster.'
          |conduct info
          |""".stripMargin
      ),
      Utf8
    )
    println("\nThe ConductR installation script has been successfully created at:\n  " + installationScript)
    installationScript.setExecutable(true)
    installationScript
  }

  private def installTask(): Def.Initialize[Task[Unit]] = Def.task {
    withProcessHandling {
      val nrOfContainers = """docker ps -q --filter="name=cond-"""".lines_!.size
      if (nrOfContainers > 0) {
        println("Restarting ConductR to ensure a clean state...")
        "docker exec cond-0 rm /opt/conductr/conf/seed-nodes".! // Allow the first node to form the new cluster
        for (n <- 0 until nrOfContainers) s"docker restart -t 0 cond-$n".!
      } else {
        throw new IllegalStateException("Please first start the sandbox using 'sandbox run'.")
      }
    }(sys.error("There was a problem re/starting the sandbox."))

    withProcessHandling {
      waitForConductr()

      installationData.value.foreach {
        case InstallationData(bundleName, bundle, bundleConfigFile) =>
          println(s"Deploying $bundleName...")
          val bundleArg = InstallationData.nameOrPath(bundle)
          val bundleConfigArg = bundleConfigFile.mkString("", "", " ")
          s"conduct load $bundleArg $bundleConfigArg--long-ids -q".lines_!.headOption match {
            case Some(bundleId) =>
              if (s"conduct run $bundleId --no-wait -q".! != 0)
                throw new IllegalStateException(s"Bundle $bundleArg could not run")
            case None =>
              throw new IllegalStateException(s"Bundle $bundleArg could not be loaded")
          }
      }

    }(sys.error("There was a problem installing the project to ConductR."))

    println
    println("""Your system is deployed. Running "conduct info" to observe the cluster.""")
    "conduct info".!
    println
  }

  private def sandboxTask: Def.Initialize[InputTask[Unit]] = Def.inputTask {
    verifyCliInstallation()

    Parsers.Sandbox.subtask.parsed match {
      case SandboxHelp                 => sandboxHelp()
      case SandboxSubtaskHelp(command) => sandboxSubHelp(command)
      case SandboxRunSubtask(args)     => Project.extract(state.value).runTask(sandboxRunTaskInternal, state.value.put(SandboxRunArgsAttrKey, args))
      case SandboxStopSubtask          => sandboxStop()
      case SandboxVersionSubtask       => sandboxVersion()
    }
  }

  private def conductTask: Def.Initialize[InputTask[Unit]] = Def.inputTask {
    verifyCliInstallation()

    Parsers.Conduct.subtask.parsed match {
      case ConductHelp                          => conductHelp()
      case ConductSubtaskHelp(command)          => conductSubHelp(command)
      case ConductSubtaskSuccess(command, args) => conductSubtask(command, args)
    }
  }

  private def verifyCliInstallation(): Unit =
    withProcessHandling {
      s"conduct".!(NoProcessLogging)
    }(sys.error(s"The conductr-cli has not been installed. Follow the instructions on http://conductr.lightbend.com/docs/$LatestConductrDocVersion/CLI to install the CLI."))

  private def sandboxHelp(): Unit =
    s"sandbox --help".!

  private def sandboxSubHelp(command: String): Unit =
    s"sandbox $command --help".!

  // FIXME: The filter must be passed in presently: https://github.com/sbt/sbt/issues/1095
  private def sandboxRunTask(filter: ScopeFilter): Def.Initialize[Task[Unit]] = Def.task {
    val projectImageVersion = if (hasRpLicense.value) Some(LatestConductrVersion) else None
    val overrideEndpoints = (BundleKeys.overrideEndpoints in Bundle).?.map(_.flatten.getOrElse(Map.empty)).all(filter).value.flatten
    val endpoints = (BundleKeys.endpoints in Bundle).?.map(_.getOrElse(Map.empty)).all(filter).value.flatten
    val endpointsToUse = if (overrideEndpoints.nonEmpty) overrideEndpoints else endpoints
    val bundlePorts =
      endpointsToUse
        .map(_._2)
        .toSet
        .flatMap { endpoint: Endpoint =>
          endpoint.services.getOrElse(Set.empty).map { uri =>
            if (uri.getHost != null) uri.getPort else uri.getAuthority.drop(1).toInt
          }.collect {
            case port if port >= 0 => port
          }
        }

    val args = state.value.get(SandboxRunArgsAttrKey).getOrElse(SandboxRunArgs())
    sandboxRun(
      conductrImageVersion = args.imageVersion orElse projectImageVersion,
      conductrImage = args.image,
      nrOfContainers = args.nrOfContainers,
      features = args.features,
      ports = args.ports ++ bundlePorts,
      logLevel = args.logLevel,
      conductrRoles = args.conductrRoles,
      envs = args.envs
    )
  }

  /**
   * Executes the `sandbox run` command of the conductr-cli.
   */
  def sandboxRun(
    conductrImageVersion: Option[String],
    conductrImage: Option[String] = None,
    nrOfContainers: Option[Int] = None,
    features: Seq[Seq[String]] = Seq.empty,
    ports: Set[Int] = Set.empty,
    logLevel: Option[String] = None,
    conductrRoles: Seq[Set[String]] = Seq.empty,
    envs: Map[String, String] = Map.empty
  ): Unit = {
    import Parsers.Sandbox.Flags
    import Parsers.ArgumentConverters._
    import ProcessConverters._

    Process(
      Seq("sandbox", "run") ++
        conductrImageVersion.toSeq ++
        conductrImage.withFlag(Flags.image) ++
        nrOfContainers.withFlag(Flags.nrOfContainers) ++
        features.flatMap(Flags.feature +: _) ++
        ports.withFlag(Flags.port) ++
        logLevel.withFlag(Flags.logLevel) ++
        conductrRoles.map(_.asConsoleNArg).withFlag(Flags.conductrRole) ++
        envs.map(_.asConsolePairArg).withFlag(Flags.env)
    ).!
  }

  /**
   * Executes the `sandbox stop` command of the conductr-cli
   */
  def sandboxStop(): Unit =
    Process(Seq("sandbox", "stop")).!

  /**
   * Executes the `sandbox version` command of the conductr-cli
   */
  def sandboxVersion(): Unit =
    Process(Seq("sandbox", "version")).!

  /**
   * A convenience function that waits on ConductR to become available.
   *
   * @param duration the max time to wait.
   */
  def waitForConductr(implicit duration: FiniteDuration = 20.seconds): Unit = {
    print("Waiting for ConductR to start")

    val deadline = duration.fromNow

    @tailrec
    def loop(deadline: Deadline): Unit = {
      print(".")
      if (deadline.isOverdue())
        throw new TimeoutException(s"ConductR has not been started within ${duration.toSeconds} seconds!")
      if (s"conduct info".!(NoProcessLogging) != 0) {
        Thread.sleep(500)
        loop(deadline)
      }
    }

    loop(deadline)
    println
  }

  private def conductHelp(): Unit =
    s"conduct --help".!

  private def conductSubHelp(command: String): Unit =
    s"conduct $command --help".!

  private def conductSubtask(command: String, args: Seq[String]): Unit =
    Process(Seq("conduct", command) ++ args).!

  private object Parsers {
    final val NSeparator = ' '
    final val PairSeparator = '='
    final val NonDashClass = charClass(_ != '-', "non-dash character")

    // Sandbox
    object Sandbox {
      import ArgumentConverters._

      val availableFeatures = Set("visualization", "logging", "monitoring")

      // Sandbox parser
      lazy val subtask: Def.Initialize[State => Parser[SandboxSubtask]] = Def.value {
        case _ =>
          (Space ~> (
            helpSubtask |
            runSubtask |
            stopSubtask |
            versionSubtask
          )) ?? SandboxHelp
      }

      // Sandbox help command (sandbox --help)
      def helpSubtask: Parser[SandboxHelp.type] =
        (hideAutoCompletion("-h") | token("--help"))
          .map { case _ => SandboxHelp }
          .!!! { "Usage: sandbox --help" }

      def runSubtask: Parser[SandboxRunSubtask] =
        token("run") ~> sandboxRunArgs
          .map { case args => SandboxRunSubtask(toRunArgs(args)) }
          .!!!("Usage: sandbox run --help")
      def sandboxRunArgs = (conductrRole | env | image | logLevel | nrOfContainers | port | feature | imageVersion).*
      def toRunArgs(args: Seq[SandboxRunArg]): SandboxRunArgs =
        args.foldLeft(SandboxRunArgs()) {
          case (currentArgs, arg) =>
            arg match {
              case ImageVersionArg(v)   => currentArgs.copy(imageVersion = Some(v))
              case ConductrRoleArg(v)   => currentArgs.copy(conductrRoles = currentArgs.conductrRoles :+ v)
              case EnvArg(v)            => currentArgs.copy(envs = currentArgs.envs + v)
              case ImageArg(v)          => currentArgs.copy(image = Some(v))
              case LogLevelArg(v)       => currentArgs.copy(logLevel = Some(v))
              case NrOfContainersArg(v) => currentArgs.copy(nrOfContainers = Some(v))
              case PortArg(v)           => currentArgs.copy(ports = currentArgs.ports + v)
              case FeatureArg(v)        => currentArgs.copy(features = currentArgs.features :+ v)
            }
        }
      def isRunArg(arg: String, flag: String): Boolean =
        arg.startsWith(flag)

      def stopSubtask: Parser[SandboxStopSubtask.type] =
        token("stop")
          .map { case _ => SandboxStopSubtask }
          .!!!("Usage: sandbox stop")

      def versionSubtask: Parser[SandboxVersionSubtask.type] =
        token("version")
          .map { case _ => SandboxVersionSubtask }
          .!!!("Usage: sandbox version")

      // Sandbox command specific arguments
      def imageVersion: Parser[ImageVersionArg] =
        versionNumber("<conductr_version>").map(ImageVersionArg(_))

      def conductrRole: Parser[ConductrRoleArg] =
        Space ~> (token(Flags.conductrRole) | hideAutoCompletion("-r")) ~> nonArgStringWithText(s"Format: ${Flags.conductrRole} role1 role2").+
          .map(roles => ConductrRoleArg(roles.toSet))

      def env: Parser[EnvArg] =
        Space ~> (token(Flags.env) | hideAutoCompletion("-e")) ~> pairStringWithText(s"Format: ${Flags.env} key=value")
          .map(keyAndValue => EnvArg(keyAndValue.asScalaPairArg))

      def image: Parser[ImageArg] =
        Space ~> (token(Flags.image) | hideAutoCompletion("-i")) ~> nonArgStringWithText("<conductr_image>").map(ImageArg(_))

      def logLevel: Parser[LogLevelArg] =
        Space ~> (token(Flags.logLevel) | hideAutoCompletion("-l")) ~> nonArgStringWithText("<log-level>").map(LogLevelArg(_))

      def nrOfContainers: Parser[NrOfContainersArg] =
        Space ~> (token(Flags.nrOfContainers) | hideAutoCompletion("-n")) ~> numberWithText("<nr-of-containers>").map(NrOfContainersArg(_))

      def port: Parser[PortArg] =
        Space ~> (token(Flags.port) | hideAutoCompletion("-p")) ~> numberWithText("<port>").map(PortArg(_))

      def feature: Parser[FeatureArg] =
        Space ~> (token(Flags.feature) | hideAutoCompletion("-f")) ~> (featureExamples ~ nonArgStringWithText("<feature_arg>").*)
          .map { case (feature, args) => FeatureArg(feature +: args) }

      def featureExamples: Parser[String] =
        Space ~> token(StringBasic.examples(availableFeatures))

      object Flags {
        val imageVersion = "--image-version"
        val conductrRole = "--conductr-role"
        val env = "--env"
        val image = "--image"
        val logLevel = "--log-level"
        val nrOfContainers = "--nr-of-containers"
        val port = "--port"
        val feature = "--feature"
      }
    }

    // Conduct
    object Conduct {
      // Conduct parser
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
            serviceNamesSubtask |
            aclsSubtask |
            eventsSubtask(bundleNames) |
            logsSubtask(bundleNames)
          )) ?? ConductHelp
        }
        (Keys.resolvedScoped, init) { (ctx, parser) => s: State =>
          val bundle = loadFromContext(discoveredDist, ctx, s)
          val bundleConfig = loadFromContext(discoveredConfigDist, ctx, s)
          val bundleNames =
            withProcessHandling {
              "conduct info"
                .lines_!(NoProcessLogging)
                .slice(1, 11) //                         No more than this number of lines please, and no header...
                .flatMap(_.split("\\s+").slice(1, 2)) // Just the second column i.e. the name
                .toSet
            }(Set.empty[String])
          parser(bundle, bundleConfig, bundleNames)
        }
      }

      // Conduct help command (conduct --help)
      def helpSubtask: Parser[ConductHelp.type] =
        (hideAutoCompletion("-h") | token("--help"))
          .map { case _ => ConductHelp }
          .!!! { "Usage: conduct --help" }

      // This parser is triggering the help of the conduct sub command if no argument for this command is specified
      // Example: `conduct load` will execute `conduct load --help`
      def subHelpSubtask: Parser[ConductSubtaskHelp] =
        (token("load") | token("run") | token("stop") | token("unload") | token("events") | token("logs") | token("acls"))
          .map(ConductSubtaskHelp)

      // Sub command parsers
      def versionSubtask: Parser[ConductSubtaskSuccess] =
        (token("version") ~> commonArgs.?)
          .map { case args => ConductSubtaskSuccess("version", optionalArgs(args)) }
          .!!!("Usage: conduct version")

      def loadSubtask(availableBundle: Option[File], availableBundleConfiguration: Option[File]): Parser[ConductSubtaskSuccess] =
        token("load") ~> withArgs(loadArgs)(bundle(availableBundle) ~ bundle(availableBundleConfiguration).?)
          .mapArgs { case (args, (bundle, config)) => ConductSubtaskSuccess("load", optionalArgs(args) ++ Seq(bundle.toString) ++ optionalArgs(config)) }
          .!!! { "Usage: conduct load --help" }
      def loadArgs = hideAutoCompletion(commonArgs | resolveCacheDir | waitTimeout | noWait).*.map(seqToString).?

      def runSubtask(bundleNames: Set[String]): Parser[ConductSubtaskSuccess] =
        token("run") ~> withArgs(runArgs(bundleNames))(bundleId(bundleNames))
          .mapArgs { case (args, bundle) => ConductSubtaskSuccess("run", optionalArgs(args) ++ Seq(bundle)) }
          .!!!("Usage: conduct run --help")
      def runArgs(bundleNames: Set[String]) = hideAutoCompletion(commonArgs | waitTimeout | noWait | scale | affinity(bundleNames)).*.map(seqToString).?

      def stopSubtask(bundleNames: Set[String]): Parser[ConductSubtaskSuccess] =
        token("stop") ~> withArgs(stopArgs)(bundleId(bundleNames))
          .mapArgs { case (args, bundleId) => ConductSubtaskSuccess("stop", optionalArgs(args) ++ Seq(bundleId)) }
          .!!!("Usage: conduct stop --help")
      def stopArgs = (waitTimeout | noWait).examples("--no-wait", "--wait-timeout").*.map(seqToString).?

      def unloadSubtask(bundleNames: Set[String]): Parser[ConductSubtaskSuccess] =
        token("unload") ~> withArgs(unloadArgs)(bundleId(bundleNames))
          .mapArgs { case (args, bundleId) => ConductSubtaskSuccess("unload", optionalArgs(args) ++ Seq(bundleId)) }
          .!!!("Usage: conduct unload --help")
      def unloadArgs = hideAutoCompletion(commonArgs | waitTimeout | noWait).*.map(seqToString).?

      def infoSubtask: Parser[ConductSubtaskSuccess] =
        token("info" ~> infoArgs)
          .map { case args => ConductSubtaskSuccess("info", optionalArgs(args)) }
          .!!!("Usage: conduct info")
      def infoArgs = hideAutoCompletion(commonArgs).*.map(seqToString).?

      def serviceNamesSubtask: Parser[ConductSubtaskSuccess] =
        token("service-names" ~> servicesArgs)
          .map { case args => ConductSubtaskSuccess("service-names", optionalArgs(args)) }
          .!!!("Usage: conduct service-names")
      def servicesArgs = hideAutoCompletion(commonArgs).*.map(seqToString).?

      def aclsSubtask: Parser[ConductSubtaskSuccess] =
        token("acls") ~> withArgs(aclArgs)(protocolFamily)
          .mapArgs { case (opts, protocolFamily) => ConductSubtaskSuccess("acls", optionalArgs(opts) ++ Seq(protocolFamily)) }
          .!!!("Usage: conduct acls --help")
      def aclArgs = hideAutoCompletion(commonArgs).*.map(seqToString).?

      def eventsSubtask(bundleNames: Set[String]): Parser[ConductSubtaskSuccess] =
        (token("events") ~> withArgs(eventsArgs)(bundleId(bundleNames)))
          .mapArgs { case (args, bundleId) => ConductSubtaskSuccess("events", optionalArgs(args) ++ Seq(bundleId)) }
          .!!!("Usage: conduct events --help")
      def eventsArgs = hideAutoCompletion(commonArgs | waitTimeout | noWait | date | utc | lines).*.map(seqToString).?

      def logsSubtask(bundleNames: Set[String]): Parser[ConductSubtaskSuccess] =
        token("logs") ~> withArgs(logsArgs)(bundleId(bundleNames))
          .mapArgs { case (args, bundleId) => ConductSubtaskSuccess("logs", optionalArgs(args) ++ Seq(bundleId)) }
          .!!!("Usage: conduct logs --help")
      def logsArgs = hideAutoCompletion(commonArgs | waitTimeout | noWait | date | utc | lines).*.map(seqToString).?

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
      def protocolFamily: Parser[String] =
        Space ~> (httpProtocolFamily | tcpProtocolFamily)
      def httpProtocolFamily: Parser[String] =
        token("http")
      def tcpProtocolFamily: Parser[String] =
        token("tcp")

      // Common optional options
      def commonArgs: Parser[String] =
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
      def withArgs[A, B](optionalArgs: Parser[A])(positionalArgs: Parser[B]): Parser[Either[(A, B), (B, A)]] =
        (optionalArgs ~ positionalArgs) || (positionalArgs ~ optionalArgs)
      implicit class ParserOps[A, B](parser: Parser[Either[(A, B), (B, A)]]) {
        def mapArgs[T <: SubtaskSuccess](f: (A, B) => T): Parser[T] =
          parser map {
            case Left((optionalArgs, positionalArgs))  => f(optionalArgs, positionalArgs)
            case Right((positionalArgs, optionalArgs)) => f(optionalArgs, positionalArgs)
          }
      }

      // Converts optional arguments to a `Seq[String]`, meaning the `args` parameter can have 1 to n words
      // Each word is converted to a new element in the `Seq`
      // Example: Some("--help --verbose --scale 5") results in Seq("--help", "--verbose", "--scale", 5)
      // The returned format is ideal to use in `scala.sys.Process()`
      def optionalArgs[T](args: Option[T]): Seq[String] =
        args.fold(Seq.empty[String])(_.toString.split(" "))

      // Convert Tuple[A,B] to String by using a whitespace separator
      def pairToString[A, B](pair: (A, B)): String =
        s"${pair._1} ${pair._2}"

      // Convert Seq[String] to String by using a whitespace separator
      def seqToString(seq: Seq[String]): String =
        seq.mkString(" ")
    }

    // Utility parsers
    def basicString: Parser[String] =
      Space ~> StringBasic
    def basicStringWithText(completionText: String): Parser[String] =
      Space ~> withCompletionText(StringBasic, completionText)
    def nonArgStringWithText(completionText: String): Parser[String] = {
      val nonArgString = identifier(NonDashClass, NotSpaceClass)
      Space ~> withCompletionText(nonArgString, completionText)
    }
    def pairStringWithText(completionText: String): Parser[String] = {
      val pairString = StringBasic.filter(isPairString(_), _ => "Invalid key=value string")
      Space ~> withCompletionText(pairString, completionText)
    }
    def isPairString(s: String): Boolean =
      s.split(PairSeparator).length == 2
    def positiveNumber: Parser[Int] =
      Space ~> NatBasic
    def numberWithText(completionText: String): Parser[Int] =
      Space ~> token(IntBasic, completionText)
    def versionNumber(completionText: String): Parser[String] =
      Space ~> token(identifier(charClass(_.isDigit), charClass(c => c == '.' || c == '-' || c.isLetterOrDigit)), completionText)
    def withCompletionText(parser: Parser[String], completionText: String): Parser[String] =
      token(parser, completionText)

    // Hide auto completion in sbt session for the given parser
    def hideAutoCompletion[T](parser: Parser[T]): Parser[T] =
      token(parser, hide = _ => true)

    object ArgumentConverters {
      implicit class StringOps(val self: String) extends AnyVal {
        def asScalaPairArg: (String, String) = {
          if (!isPairString(self))
            sys.error(s"String '$self' can't be converted to a pair by using the separator $PairSeparator.")
          val parts = self.split(PairSeparator)
          parts(0) -> parts(1)
        }
      }

      implicit class SetOps[T](val self: Set[T]) extends AnyVal {
        def asConsoleNArg: String =
          self.mkString(" ")
      }

      implicit class PairOps[K, V](val self: (K, V)) extends AnyVal {
        def asConsolePairArg: String =
          s"${self._1}=${self._2}"
      }
    }
  }

  private def withProcessHandling[T](block: => T)(exceptionHandler: => T): T =
    try {
      block
    } catch {
      case ioe: IOException => exceptionHandler
    }

  private trait SubtaskSuccess
  private sealed trait SandboxSubtask
  private case class SandboxRunSubtask(args: SandboxRunArgs) extends SandboxSubtask with SubtaskSuccess
  private object SandboxStopSubtask extends SandboxSubtask with SubtaskSuccess
  private object SandboxVersionSubtask extends SandboxSubtask with SubtaskSuccess
  private case object SandboxHelp extends SandboxSubtask
  private case class SandboxSubtaskHelp(command: String) extends SandboxSubtask

  private sealed trait ConductSubtask
  private case class ConductSubtaskSuccess(command: String, args: Seq[String]) extends ConductSubtask with SubtaskSuccess
  private case object ConductHelp extends ConductSubtask
  private case class ConductSubtaskHelp(command: String) extends ConductSubtask

  private sealed trait SandboxRunArg extends Any
  private case class ImageVersionArg(value: String) extends AnyVal with SandboxRunArg
  private case class ConductrRoleArg(value: Set[String]) extends AnyVal with SandboxRunArg
  private case class EnvArg(value: (String, String)) extends AnyVal with SandboxRunArg
  private case class ImageArg(value: String) extends AnyVal with SandboxRunArg
  private case class LogLevelArg(value: String) extends AnyVal with SandboxRunArg
  private case class NrOfContainersArg(value: Int) extends AnyVal with SandboxRunArg
  private case class PortArg(value: Int) extends AnyVal with SandboxRunArg
  private case class FeatureArg(value: Seq[String]) extends AnyVal with SandboxRunArg
  private case class SandboxRunArgs(
    imageVersion: Option[String] = None,
    conductrRoles: Seq[Set[String]] = Seq.empty,
    envs: Map[String, String] = Map.empty,
    image: Option[String] = None,
    logLevel: Option[String] = None,
    nrOfContainers: Option[Int] = None,
    ports: Set[Int] = Set.empty,
    features: Seq[Seq[String]] = Seq.empty
  )

  private object NoProcessLogging extends ProcessLogger {
    override def info(s: => String): Unit = ()
    override def error(s: => String): Unit = ()
    override def buffer[T](f: => T): T = f
  }

  private object ProcessConverters {
    implicit class OptionOps[T](val self: Option[T]) extends AnyVal {
      def withFlag(flag: String): Seq[String] =
        self.fold(Seq.empty[String])(value => Seq(flag, value.toString))
    }

    implicit class TraversableOps[T](val self: Traversable[T]) extends AnyVal {
      def withFlag(flag: String): Seq[String] =
        self.foldLeft(Seq.empty[String]) {
          case (xs, x) => x.toString +: flag +: xs
        }.reverse
    }
  }
}