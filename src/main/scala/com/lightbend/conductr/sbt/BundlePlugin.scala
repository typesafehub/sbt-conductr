/*
 * Copyright © 2014-2016 Lightbend, Inc. All rights reserved.
 */

package com.lightbend.conductr.sbt

import sbt._
import sbt.Keys._
import com.typesafe.sbt.SbtNativePackager
import com.typesafe.sbt.packager.universal.Archives
import SbtNativePackager.Universal
import java.io.{ FileInputStream, BufferedInputStream }
import java.security.MessageDigest
import scala.util.matching.Regex
import scala.annotation.tailrec
import scala.concurrent.duration._

object BundlePlugin extends AutoPlugin {
  import BundleImport._
  import BundleKeys._
  import SbtNativePackager.autoImport._

  val autoImport = BundleImport

  override def requires = SbtNativePackager

  override def trigger = AllRequirements

  override def projectSettings =
    bundleSettings(Bundle) ++ configurationSettings(BundleConfiguration) ++
      Seq(
        bundleConfVersion := BundleConfVersions.V_1_1_0,
        bundleType := Universal,
        checkInitialDelay := 3.seconds,
        checks := Seq.empty,
        compatibilityVersion := (version in Bundle).value.takeWhile(_ != '.'),
        configurationName := "default",
        endpoints := DefaultEndpoints,
        javaOptions in Bundle ++= Seq(
          s"-J-Xms${(memory in Bundle).value.round1k.underlying}",
          s"-J-Xmx${(memory in Bundle).value.round1k.underlying}"
        ),
        projectTarget := target.value,
        roles := Set("web"),
        system := (normalizedName in Bundle).value,
        systemVersion := (compatibilityVersion in Bundle).value,
        startCommand := Seq((executableScriptPath in Bundle).value) ++ (javaOptions in Bundle).value
      )

  override def projectConfigurations: Seq[Configuration] =
    Seq(
      Bundle,
      BundleConfiguration,
      Universal // `Universal` is added here due to this issue: (https://github.com/sbt/sbt-native-packager/issues/676
    )

  /**
   * Build out the bundle settings for a given sbt configuration.
   */
  def bundleSettings(config: Configuration): Seq[Setting[_]] =
    inConfig(config)(Seq(
      bundleConf := getConfig(config, forAllSettings = true).value,
      executableScriptPath := (file((normalizedName in config).value) / "bin" / (executableScriptName in config).value).getPath,
      NativePackagerKeys.packageName := (normalizedName in config).value + "-v" + (compatibilityVersion in config).value,
      NativePackagerKeys.dist := Def.taskDyn {
        Def.task {
          createDist(config, (bundleType in config).value)
        }.value
      }.value,
      NativePackagerKeys.stage := Def.taskDyn {
        Def.task {
          stageBundle(config, (bundleType in config).value)
        }.value
      }.value,
      NativePackagerKeys.stagingDirectory := (target in config).value / "stage",
      target := projectTarget.value / "bundle"
    )) ++ configNameSettings(config)

  /**
   * Build out the bundle configuration settings for a given sbt configuration.
   */
  def configurationSettings(config: Configuration): Seq[Setting[_]] =
    inConfig(config)(Seq(
      bundleConf := getConfig(config, forAllSettings = false).value,
      checks := Seq.empty,
      compatibilityVersion := (version in config).value.takeWhile(_ != '.'),
      executableScriptPath := (file((normalizedName in config).value) / "bin" / (executableScriptName in config).value).getPath,
      NativePackagerKeys.dist := Def.taskDyn {
        Def.task {
          createConfiguration(config, "Bundle configuration has been created")
        }.value
      }.value,
      NativePackagerKeys.stage := Def.taskDyn {
        Def.task {
          stageConfiguration(config)
        }.value
      }.value,
      NativePackagerKeys.stagingDirectory := (target in config).value / "stage",
      target := projectTarget.value / "bundle-configuration",
      sourceDirectory := (sourceDirectory in config).value.getParentFile / "bundle-configuration"
    )) ++ configNameSettings(config)

  private val projectTarget = settingKey[File]("")

  private val bundleTypeConfigName = taskKey[(Configuration, Option[String])]("")
  private val diskSpaceConfigName = taskKey[(Bytes, Option[String])]("")
  private val endpointsConfigName = taskKey[(Map[String, Endpoint], Option[String])]("")
  private val memoryConfigName = taskKey[(Bytes, Option[String])]("")
  private val projectInfoConfigName = taskKey[(ModuleInfo, Option[String])]("")
  private val rolesConfigName = taskKey[(Set[String], Option[String])]("")
  private val startCommandConfigName = taskKey[(Seq[String], Option[String])]("")
  private val checksConfigName = taskKey[(Seq[URI], Option[String])]("")
  private val compatibilityVersionConfigName = taskKey[(String, Option[String])]("")
  private val normalizedNameConfigName = taskKey[(String, Option[String])]("")
  private val nrOfCpusConfigName = taskKey[(Double, Option[String])]("")
  private val systemConfigName = taskKey[(String, Option[String])]("")
  private val systemVersionConfigName = taskKey[(String, Option[String])]("")

  private def configNameSettings(config: Configuration): Seq[Setting[_]] =
    inConfig(config)(Seq(
      overrideEndpoints := None,
      bundleTypeConfigName := (bundleType in config).value -> toConfigName(bundleType in (thisProjectRef.value, config), state.value),
      diskSpaceConfigName := (diskSpace in config).value -> toConfigName(diskSpace in (thisProjectRef.value, config), state.value),
      endpointsConfigName := collectEndpoints(config).value -> toConfigName(endpoints in (thisProjectRef.value, config), state.value),
      memoryConfigName := (memory in config).value -> toConfigName(memory in (thisProjectRef.value, config), state.value),
      projectInfoConfigName := (projectInfo in config).value -> toConfigName(projectInfo in (thisProjectRef.value, config), state.value),
      rolesConfigName := (roles in config).value -> toConfigName(roles in (thisProjectRef.value, config), state.value),
      startCommandConfigName := (startCommand in config).value -> toConfigName(startCommand in (thisProjectRef.value, config), state.value),
      checksConfigName := (checks in config).value -> toConfigName(checks in (thisProjectRef.value, config), state.value),
      compatibilityVersionConfigName := (compatibilityVersion in config).value -> toConfigName(compatibilityVersion in (thisProjectRef.value, config), state.value),
      normalizedNameConfigName := (normalizedName in config).value -> toConfigName(normalizedName in (thisProjectRef.value, config), state.value),
      nrOfCpusConfigName := (nrOfCpus in config).value -> toConfigName(nrOfCpus in (thisProjectRef.value, config), state.value),
      systemConfigName := (system in config).value -> toConfigName(system in (thisProjectRef.value, config), state.value),
      systemVersionConfigName := (systemVersion in config).value -> toConfigName(systemVersion in (thisProjectRef.value, config), state.value)
    ))

  private def toConfigName(scoped: Scoped, state: State): Option[String] = {
    val extracted = Project.extract(state)
    extracted.structure.data.definingScope(scoped.scope, scoped.key).flatMap(_.config.toOption.map(_.name))
  }

  private def createDist(config: Configuration, bundleTypeConfig: Configuration): Def.Initialize[Task[File]] = Def.task {
    val bundleTarget = (target in config).value
    val configTarget = bundleTarget / config.name / "tmp"
    def relParent(p: (File, String)): (File, String) =
      (p._1, (normalizedName in config).value + java.io.File.separator + p._2)
    val configFile = writeConfig(configTarget, (bundleConf in config).value)
    val bundleMappings =
      configFile.pair(relativeTo(configTarget)) ++ (mappings in bundleTypeConfig).value.map(relParent)
    shazar(
      bundleTarget,
      (packageName in config).value,
      bundleMappings,
      f => streams.value.log.info(s"Bundle has been created: $f")
    )
  }

  /**
   * Creates a bundle configuration in the specified config target directory
   *
   * @param config under which the bundle configuration is created
   * @param message that is printed if the bundle configuration has been successfully created.
   * @return the created bundle configuration file
   */
  def createConfiguration(config: Configuration, message: String): Def.Initialize[Task[File]] = Def.task {
    val bundleTarget = (target in config).value
    val configurationTarget = (NativePackagerKeys.stage in config).value
    val configChildren = recursiveListFiles(Array(configurationTarget), NonDirectoryFilter)
    val bundleMappings: Seq[(File, String)] = configChildren.flatMap(_.pair(relativeTo(configurationTarget)))
    shazar(
      bundleTarget,
      (configurationName in config).value,
      bundleMappings,
      f => streams.value.log.info(s"$message: $f")
    )
  }

  // By default use the BundleKeys.endpoints settings key as endpoints
  // Override this method to change behaviour how to collect the endpoints
  private def collectEndpoints(config: Configuration): Def.Initialize[Task[Map[String, Endpoint]]] = Def.task {
    (overrideEndpoints in config).value.getOrElse((endpoints in config).value)
  }

  private def shazar(
    archiveTarget: File,
    archiveName: String,
    bundleMappings: Seq[(File, String)],
    logMessage: File => Unit
  ): File = {
    val archived = Archives.makeZip(archiveTarget, archiveName, bundleMappings, Some(archiveName), Seq.empty)
    val exti = archived.name.lastIndexOf('.')
    val hashName = archived.name.take(exti) + "-" + hash(digestFile(archived)) + archived.name.drop(exti)
    val hashArchive = archived.getParentFile / hashName
    IO.move(archived, hashArchive)
    logMessage(hashArchive)
    hashArchive
  }

  private def digestFile(f: File): Array[Byte] = {
    val digest = MessageDigest.getInstance(Sha256)
    val in = new BufferedInputStream(new FileInputStream(f))
    val buf = Array.ofDim[Byte](8192)
    try {
      @tailrec
      def readAndUpdate(r: Int): Unit =
        if (r != -1) {
          digest.update(buf, 0, r)
          readAndUpdate(in.read(buf))
        }
      readAndUpdate(in.read(buf))
      digest.digest
    } finally {
      in.close()
    }
  }

  object PathMatching {
    val ValidUriCharacters = """^[a-zA-Z0-9-._]+$"""

    private[sbt] def pathConfigKeyValue(path: Either[String, Regex]): (String, String) = {
      path match {
        case Left(value) =>
          "path" -> value

        case Right(value) if value.pattern.pattern.startsWith("^/") && !value.pattern.pattern.endsWith("$") =>
          val pathBeg = value.pattern.pattern.tail

          val isValidPathBegPattern = !pathBeg.contains("//") &&
            pathBeg.split("/").forall(v => v.isEmpty || v.matches(ValidUriCharacters))

          if (isValidPathBegPattern)
            "path-beg" -> pathBeg
          else
            throw new IllegalArgumentException(s"${value.pattern.pattern} is not a valid pattern for path beg")

        case Right(value) =>
          "path-regex" -> value.pattern.pattern
      }
    }
  }

  private def formatHttpRequestMapping(requestMapping: Http.Request): String = {
    val (pathConfigKey, pathConfigValue) = PathMatching.pathConfigKeyValue(requestMapping.path)
    val lines =
      Seq(
        Option(s"""$pathConfigKey = "$pathConfigValue""""),
        requestMapping.method.map(v => s"""method = "$v""""),
        requestMapping.rewrite.map(v => s"""rewrite = "$v"""")
      )
        .collect {
          case Some(value) => value
        }
        .map(v => s"                  $v")
        .mkString("\n")

    s"""                {
        |$lines
        |                }""".stripMargin
  }

  private def formatHttpRequestMappings(requestMappings: Http): String =
    s"""            ${requestMappings.protocolFamily} = {
        |              requests = [
        |${requestMappings.requestMappings.map(formatHttpRequestMapping).mkString(",\n")}
        |              ]
        |            }""".stripMargin

  private def formatTcpRequestMappings(requestMappings: Tcp): String =
    s"""            ${requestMappings.protocolFamily} = {
        |              requests = [${requestMappings.requestMappings.map(_.port).mkString(", ")}]
        |            }""".stripMargin

  private def formatUdpRequestMappings(requestMappings: Udp): String =
    s"""            ${requestMappings.protocolFamily} = {
        |              requests = [${requestMappings.requestMappings.map(_.port).mkString(", ")}]
        |            }""".stripMargin

  private def formatRequestMappings(requestMappings: ProtocolFamilyRequestMapping): String =
    requestMappings match {
      case v: Http => formatHttpRequestMappings(v)
      case v: Tcp  => formatTcpRequestMappings(v)
      case v: Udp  => formatUdpRequestMappings(v)
    }

  private def formatAcl(acl: RequestAcl): String =
    if (acl.protocolFamilyRequestMappings.isEmpty)
      ""
    else
      s"""          {
          |${acl.protocolFamilyRequestMappings.map(formatRequestMappings).mkString(",\n")}
          |          }""".stripMargin

  private def formatAcls(acls: Set[RequestAcl]): String =
    s"""        acls          = [
        |${acls.map(formatAcl).mkString(",\n")}
        |        ]""".stripMargin

  private def formatSeq(strings: Iterable[String]): String =
    strings.map(s => s""""$s"""").mkString("[", ", ", "]")

  private def formatServices(services: Set[URI]): String =
    s"        services      = ${formatSeq(services.map(_.toString))}"

  private def formatServiceName(serviceName: String): String =
    s"""        service-name  = "$serviceName""""

  private def formatEndpoints(bundleConfVersion: BundleConfVersions.Value, endpoints: Map[String, Endpoint]): String = {
    val formatted =
      for {
        (label, Endpoint(bindProtocol, bindPort, services, serviceName, acls)) <- endpoints
      } yield {
        if (acls.exists(_.nonEmpty) && bundleConfVersion != BundleConfVersions.V_1_2_0)
          throw new IllegalArgumentException(s"Invalid configuration for endpoint $label - request ACL may only be specified for bundle.conf version 1.2.0")
        else if (acls.exists(_.nonEmpty) && services.exists(_.nonEmpty))
          throw new IllegalArgumentException(s"Invalid configuration for endpoint $label - either Services or Request ACL can be set")

        val servicesOrAcls =
          Seq(
            serviceName.map(formatServiceName),
            services.map(formatServices),
            acls.map(formatAcls)
          ).collect {
              case Some(value) => value
            }
            .mkString("\n")

        s"""|      "$label" = {
            |        bind-protocol = "$bindProtocol"
            |        bind-port     = $bindPort
            |$servicesOrAcls
            |      }""".stripMargin
      }

    formatted.mkString(f"{%n", f",%n", f"%n    }")
  }

  private def getConfig(config: Configuration, forAllSettings: Boolean): Def.Initialize[Task[String]] = Def.task {
    val checkComponents = (checksConfigName in config).value match {
      case (value, configName) if (forAllSettings || configName.isDefined) && value.nonEmpty =>
        val checkInitialDelayValue = (checkInitialDelay in config).value
        val checkInitialDelayInSeconds =
          if (checkInitialDelayValue.toMillis % 1000 > 0)
            checkInitialDelayValue.toSeconds + 1 // always round up
          else
            checkInitialDelayValue.toSeconds
        Seq(
          value.map(uri => s""""$uri"""").mkString(
            s"""components = {
                |  ${(normalizedName in config).value}-status = {
                |    description      = "Status check for the bundle component"
                |    file-system-type = "universal"
                |    start-command    = ["check", "--initial-delay", "$checkInitialDelayInSeconds", """.stripMargin,
            ", ",
            s"""]
                |    endpoints        = {}
                |  }
                |}""".stripMargin
          )
        )
      case _ =>
        Seq.empty[String]
    }

    def formatValue[T](format: String, valueAndConfigName: (T, Option[String])): Seq[String] =
      valueAndConfigName match {
        case (value, configName) if forAllSettings || configName.isDefined => Seq(format.format(value))
        case _                                                             => Seq.empty[String]
      }

    def toString[T](valueAndConfigName: (T, Option[String]), f: T => String): (String, Option[String]) =
      f(valueAndConfigName._1) -> valueAndConfigName._2

    val declarations =
      Seq(s"""version              = "${bundleConfVersion.value}"""") ++
        formatValue("""name                 = "%s"""", (normalizedNameConfigName in config).value) ++
        formatValue("""compatibilityVersion = "%s"""", (compatibilityVersionConfigName in config).value) ++
        formatValue("""system               = "%s"""", (systemConfigName in config).value) ++
        formatValue("""systemVersion        = "%s"""", (systemVersionConfigName in config).value) ++
        formatValue("nrOfCpus             = %s", (nrOfCpusConfigName in config).value) ++
        formatValue("memory               = %s", toString((memoryConfigName in config).value, (v: Bytes) => v.underlying.toString)) ++
        formatValue("diskSpace            = %s", toString((diskSpaceConfigName in config).value, (v: Bytes) => v.underlying.toString)) ++
        formatValue(s"roles                = %s", toString((rolesConfigName in config).value, (v: Set[String]) => formatSeq(v))) ++
        Seq("components = {", s"  ${(normalizedName in config).value} = {") ++
        formatValue(s"""    description      = "%s"""", toString((projectInfoConfigName in config).value, (v: ModuleInfo) => v.description)) ++
        formatValue(s"""    file-system-type = "%s"""", (bundleTypeConfigName in config).value) ++
        formatValue(s"""    start-command    = %s""", toString((startCommandConfigName in config).value, (v: Seq[String]) => formatSeq(v))) ++
        formatValue(s"""    endpoints = %s""", toString((endpointsConfigName in config).value, (v: Map[String, Endpoint]) => formatEndpoints(bundleConfVersion.value, v))) ++
        Seq("  }", "}") ++
        checkComponents

    declarations.mkString("\n")
  }

  private def stageBundle(config: Configuration, bundleTypeConfig: Configuration): Def.Initialize[Task[File]] = Def.task {
    val bundleTarget = (NativePackagerKeys.stagingDirectory in config).value / config.name
    writeConfig(bundleTarget, (bundleConf in config).value)
    val componentTarget = bundleTarget / (normalizedName in config).value
    IO.copy((mappings in bundleTypeConfig).value.map(p => (p._1, componentTarget / p._2)))
    componentTarget
  }

  private def stageConfiguration(config: Configuration): Def.Initialize[Task[File]] = Def.task {
    val configurationTarget = (NativePackagerKeys.stagingDirectory in config).value / config.name
    val generatedConf = (bundleConf in config).value
    val srcDir = (sourceDirectory in config).value / (configurationName in config).value
    if (generatedConf.isEmpty && !srcDir.exists()) sys.error(
      s"""Directory $srcDir does not exist.
          | Specify the desired configuration directory name
          |  with the 'configurationName' setting given that it is not "default"""".stripMargin
    )
    IO.createDirectory(configurationTarget)
    if (generatedConf.nonEmpty) writeConfig(configurationTarget, generatedConf)
    IO.copyDirectory(srcDir, configurationTarget, overwrite = true, preserveLastModified = true)
    configurationTarget
  }

  private def writeConfig(target: File, contents: String): File = {
    val configFile = target / "bundle.conf"
    IO.write(configFile, contents, Utf8)
    configFile
  }
}
