package com.lightbend.conductr.sbt

import sbt._
import sbt.Keys._
import java.io.InputStream
import java.util.jar.{ JarEntry, JarFile }
import com.typesafe.sbt.SbtNativePackager

import scala.collection.JavaConverters._
import play.api.libs.json._

import scala.util.{ Failure, Success }

object LagomBundlePlugin extends AutoPlugin {

  import LagomBundleImport._
  import LagomBundleKeys._
  import SbtNativePackager.autoImport._
  import BundlePlugin.autoImport._

  val autoImport = LagomBundleImport

  private val classLoader = this.getClass.getClassLoader

  override def requires =
    withContextClassloader(classLoader) { loader =>
      Reflection.getSingletonObject[Plugins.Basic](classLoader, "com.lightbend.lagom.sbt.LagomJava$") match {
        case Failure(_)         => NoOpPlugin
        case Success(lagomJava) => BundlePlugin
      }
    }

  override def trigger = allRequirements

  // Configuration to add api tools library dependencies
  private val apiToolsConfig = config("api-tools").hide

  override def projectSettings =
    bundleSettings(Bundle) ++ Seq(
      BundleKeys.bundleConfVersion := BundleConfVersions.V_1_2_0,
      BundleKeys.nrOfCpus := PlayBundleKeyDefaults.nrOfCpus,
      BundleKeys.memory := PlayBundleKeyDefaults.memory,
      BundleKeys.diskSpace := PlayBundleKeyDefaults.diskSpace,
      ivyConfigurations += apiToolsConfig,
      // scalaBinaryVersion.value uses the binary compatible scala version from the Lagom project
      conductrBundleLibVersion := "1.4.2",
      libraryDependencies ++= Seq(
        LagomImport.component("api-tools") % apiToolsConfig,
        Library.lagomConductrBundleLib(LagomVersion.current, scalaBinaryVersion.value, conductrBundleLibVersion.value)
      ),
      resolvers += Resolver.typesafeBintrayReleases,
      manageClasspath(apiToolsConfig)
    )

  override def buildSettings =
    super.buildSettings ++ cassandraConfigurationSettings(CassandraConfiguration)

  /**
   * Override bundle settings from sbt-bundle with the collected Lagom endpoints
   */
  private def bundleSettings(config: Configuration): Seq[Setting[_]] =
    inConfig(config)(
      Seq(
        BundleKeys.overrideEndpoints := Some(collectEndpoints(config).value + ("akka-remote" -> Endpoint("tcp"))),
        BundleKeys.startCommand ++= {
          val bindings = (for {
            endpoints <- BundleKeys.overrideEndpoints.value
            (serviceName, _) <- endpoints.filterNot(_._1 == "akka-remote").headOption
            formattedServiceName = envName(serviceName)
          } yield Seq(
            s"-Dhttp.address=$$${formattedServiceName}_BIND_IP",
            s"-Dhttp.port=$$${formattedServiceName}_BIND_PORT"
          )).toSeq.flatten
          // The application secret is not used by the Lagom project so the value doesn't really matter.
          // Therefore it is save to automatically generate one here. It is necessary though to set the key in prod mode.
          val applicationSecret = s"-Dplay.crypto.secret=${hash(s"${name.value}")}"
          bindings :+ applicationSecret
        }
      )
    )

  /** Ask SBT to manage the classpath for the given configuration. */
  private def manageClasspath(config: Configuration) =
    managedClasspath in config <<= (classpathTypes in config, update) map { (ct, report) =>
      Classpaths.managedJars(config, ct, report)
    }

  /**
   * Bundle configuration for cassandra.
   * Only one bundle configuration for the entire project is created. This configuration should be used for a scenario
   * in which multiple Lagom services can use the same Cassandra database, e.g. on ConductR sandbox
   * Note that each Lagom services has a separate keyspace on ConductR and is therefore logically separated
   * even there are using the same DB.
   */
  private def cassandraConfigurationSettings(config: Configuration): Seq[Setting[_]] =
    inConfig(config)(
      Seq(
        BundleKeys.configurationName := "cassandra-configuraton",
        target := (baseDirectory in LocalRootProject).value / "target" / "bundle-configuration",
        NativePackagerKeys.stagingDirectory := (target in config).value / "stage",
        NativePackagerKeys.stage := cassandraStageConfiguration(config).value,
        NativePackagerKeys.dist := BundlePlugin.createConfiguration(config, "Cassandra bundle configuration has been created").value
      ) ++ dontAggregate(NativePackagerKeys.stage, NativePackagerKeys.dist)
    )

  /**
   * Use this to not perform tasks for the aggregated projects, i.e sub projects.
   */
  private def dontAggregate(keys: Scoped*): Seq[Setting[_]] =
    keys.map(aggregate in _ := false)

  /**
   * Copies the default cassandra configuration from `sbt-conductr/src/main/resources/bundle-configuration/cassandra` to
   * the project's target root directory
   */
  private def cassandraStageConfiguration(config: Configuration): Def.Initialize[Task[File]] = Def.task {
    val configurationTarget = (NativePackagerKeys.stagingDirectory in config).value / config.name
    val resourcePath = "bundle-configuration/cassandra"
    val jarFile = new File(this.getClass.getProtectionDomain.getCodeSource.getLocation.getPath)
    if (jarFile.isFile) {
      val jar = new JarFile(jarFile)
      IO.createDirectory(configurationTarget)
      copyDirectoryFromJar(jar, configurationTarget, s"$resourcePath/")
    } else {
      val urlOpt = Option(this.getClass.getResource(s"/$resourcePath"))
      val url = urlOpt.getOrElse {
        sys.error("The cassandra configuration of LagomBundlePlugin can't be found. It should be either in the jar file or in the projects resources directory.")
      }
      IO.createDirectory(configurationTarget)
      IO.copyDirectory(new File(url.toURI), configurationTarget, overwrite = true, preserveLastModified = false)
    }

    configurationTarget
  }

  /**
   * Scans a jar file and filters files based on the `dirPrefix` parameter.
   * These files are then copied to the `targetDir`.
   */
  private def copyDirectoryFromJar(fromJarFile: JarFile, targetDir: File, dirPrefix: String): Unit = {
    fromJarFile.entries.asScala.foreach { entry =>
      if (entry.getName.startsWith(dirPrefix) && !entry.isDirectory) {
        val name = entry.getName.drop(dirPrefix.length)
        val toFile = targetDir / name
        withJarInputStream(fromJarFile, entry) { in =>
          IO.transfer(in, toFile)
        }
      }
    }
  }

  private def withJarInputStream[T](jarFile: JarFile, jarEntry: JarEntry)(block: InputStream => T): T = {
    val in = jarFile.getInputStream(jarEntry)
    try {
      block(in)
    } finally {
      in.close()
    }
  }

  /**
   * Collect the endpoints either from `BundleKeys.endpoints` or from Lagom
   * Use the `BundleKeys.endpoints` if they are different as the default sbt-bundle endpoints.
   * Otherwise collect the endpoints from Lagom by accessing the Lagom api tools library
   */
  private def collectEndpoints(config: Configuration): Def.Initialize[Task[Map[String, Endpoint]]] = Def.taskDyn {
    Def.task {
      val manualEndpoints = (BundleKeys.endpoints in config).value
      if (manualEndpoints != DefaultEndpoints)
        manualEndpoints
      else {
        val classpath = toClasspathUrls(
          // managed classpath in api tools config contains the api tools library dependencies
          (managedClasspath in apiToolsConfig).value ++
            // full classpath containing the Lagom services, Lagom framework and all its dependencies
            (fullClasspath in Compile).value
        )
        // Create class loader based on a classpath that contains all project related + api tools library classes
        val classLoader = new java.net.URLClassLoader(classpath, scalaInstance.value.loader)
        // Lookup Lagom services
        val servicesAsString = ServiceDetector.services(classLoader)
        // Convert services string to `Map[String, Endpoint]`
        toConductrEndpoints(servicesAsString)
      }
    }
  }

  private def toClasspathUrls(attributedFiles: Seq[Attributed[File]]): Array[URL] =
    attributedFiles.files.map(_.toURI.toURL).toArray

  // Matches strings that starts with sequence escaping, e.g. \Q/api/users/:id\E
  // The first sequence escaped substring that starts with a '/' is extracted as a variable
  // Examples:
  // /api/users                         => false
  // \Q/\E                              => true, variable = /
  // \Q/api/users\E                     => true, variable = /api/users
  // \Q/api/users/\E([^/]+)             => true, variable = /api/users/
  // \Q/api/users/\E([^/]+)\Q/friends\E => true, variable = /api/users/
  private val pathBeginExtractor = """^\\Q(\/.*?)\\E.*""".r

  /**
   * Convert services string to `Map[String, Endpoint]` by using the Play json library
   */
  private def toConductrEndpoints(services: String): Map[String, Endpoint] = {
    def toEndpoint(serviceNameAndPath: (String, Seq[String])): (String, Endpoint) =
      serviceNameAndPath match {
        case (serviceName, pathBegins) =>
          val endpoint = if (pathBegins.nonEmpty) {
            val pathBeginAcls = pathBegins
              .distinct
              .map {
                case emptyPath @ "" =>
                  Http.Request(None, Right("^/".r), None)
                case pathBegin =>
                  Http.Request(None, Right(s"^$pathBegin".r), None)
              }
            Endpoint("http", 0, serviceName, RequestAcl(Http(pathBeginAcls: _*)))
          } else
            Endpoint("http", 0, serviceName)

          serviceName -> endpoint
      }
    def mergeEndpoint(endpoints: Map[String, Endpoint], endpointEntry: (String, Endpoint)): Map[String, Endpoint] =
      endpointEntry match {
        case (serviceName, endpoint) =>
          val mergedEndpoint =
            endpoints.get(serviceName)
              .fold(endpoint) { prevEndpoint =>
                val mergedServices = (prevEndpoint.services, endpoint.services) match {
                  case (Some(prevServices), Some(newServices)) => Some(prevServices ++ newServices)
                  case (Some(prevServices), None)              => Some(prevServices)
                  case (None, Some(newServices))               => Some(newServices)
                  case (None, None)                            => None
                }
                val mergedRequestAcl = (prevEndpoint.acls, endpoint.acls) match {
                  case (Some(prevAcls), Some(newAcls)) => Some(prevAcls ++ newAcls)
                  case (Some(prevAcls), None)          => Some(prevAcls)
                  case (None, Some(newAcls))           => Some(newAcls)
                  case (None, None)                    => None
                }
                prevEndpoint.copy(services = mergedServices, acls = mergedRequestAcl)
              }
          endpoints + (serviceName -> mergedEndpoint)
      }

    val json = Json.parse(services)
    val serviceNamesAndPaths = json.as[List[JsObject]].map { o =>
      val serviceName = (o \ "name").as[String]
      val pathlessServiceName = if (serviceName.startsWith("/")) serviceName.drop(1) else serviceName
      val pathBegins = (o \ "acls" \\ "pathPattern")
        .map(_.as[String])
        .collect {
          case pathBeginExtractor(pathBegin) =>
            if (pathBegin.endsWith("/")) pathBegin.dropRight(1) else pathBegin
        }
      pathlessServiceName -> pathBegins
    }
    serviceNamesAndPaths
      .map(toEndpoint)
      .foldLeft(Map.empty[String, Endpoint])(mergeEndpoint)
  }

  private def envName(name: String) =
    name.replaceAll("\\W", "_").toUpperCase
}

/**
 * Mirrors the ServiceDetector class from the Lagom api tools library.
 * By declaring the public methods from the Lagom api tools library `ServiceDetector` it is possible to "safely"
 * call the class via reflection.
 */
private object ServiceDetector {

  import scala.language.reflectiveCalls

  // `ServiceDetector` mirror from the Lagom api tools library.
  // The method signature equals the signature from the api tools `ServiceDetector`
  type ServiceDetector = {
    def services(classLoader: ClassLoader): String
  }

  /**
   * Calls the Lagom api tools library `ServicesDetector.services` method by using reflection
   */
  def services(classLoader: ClassLoader): String =
    withContextClassloader(classLoader) { loader =>
      Reflection.getSingletonObject[ServiceDetector](loader, "com.lightbend.lagom.internal.api.tools.ServiceDetector$") match {
        case Failure(t)               => fail(s"Endpoints can not be resolved from Lagom project. Error: ${t.getMessage}")
        case Success(serviceDetector) => serviceDetector.services(loader)
      }
    }
}

/**
 * Mirrors the LagomVersion class of `com.lightbend.lagom.sbt.LagomImport`
 * By declaring the public methods from Lagom it is possible to "safely"
 * call the class via reflection.
 */
private object LagomImport {

  import scala.language.reflectiveCalls

  val classLoader = this.getClass.getClassLoader

  // The method signature equals the signature of `com.lightbend.lagom.sbt.LagomImport`
  type LagomImport = {
    def component(id: String): ModuleID
  }

  def component(id: String): ModuleID =
    withContextClassloader(classLoader) { loader =>
      Reflection.getSingletonObject[LagomImport](loader, "com.lightbend.lagom.sbt.LagomImport$") match {
        case Failure(t)           => sys.error(s"The LagomImport class can not be resolved. Error: ${t.getMessage}")
        case Success(lagomImport) => lagomImport.component(id)
      }
    }
}

/**
 * Mirrors the LagomVersion class of `com.lightbend.lagom.core.LagomVersion`
 * By declaring the public methods from Lagom it is possible to "safely"
 * call the class via reflection.
 */
private object LagomVersion {

  import scala.language.reflectiveCalls

  val classLoader = this.getClass.getClassLoader

  // The method signature equals the signature of `com.lightbend.lagom.core.LagomVersion`
  type LagomVersion = {
    def current: String
  }

  val current: String =
    withContextClassloader(classLoader) { loader =>
      Reflection.getSingletonObject[LagomVersion](loader, "com.lightbend.lagom.core.LagomVersion$") match {
        case Failure(t)            => sys.error(s"The LagomVersion class can not be resolved. Error: ${t.getMessage}")
        case Success(lagomVersion) => lagomVersion.current
      }
    }
}