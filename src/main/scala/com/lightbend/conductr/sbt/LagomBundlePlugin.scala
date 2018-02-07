package com.lightbend.conductr.sbt

import sbt._
import sbt.Keys._
import java.io.InputStream
import java.util.jar.{ JarEntry, JarFile }

import com.typesafe.sbt.SbtNativePackager
import SbtNativePackager.Universal
import com.lightbend.conductr.sbt.LagomBundleImport.LagomBundleKeys

import scala.collection.JavaConverters._
import play.api.libs.json._

import scala.collection.immutable.ListSet
import scala.util.{ Failure, Success }

/**
 * Bundle support for a Play service inside Lagom
 */
object LagomPlayBundlePlugin extends AutoPlugin {

  import LagomBundleImport._
  import BundlePlugin.autoImport._

  val autoImport = LagomBundleImport

  private val classLoader = this.getClass.getClassLoader

  override def requires =
    withContextClassloader(classLoader) { loader =>
      Reflection.getSingletonObject[Plugins.Basic](classLoader, "com.lightbend.lagom.sbt.LagomPlay$") match {
        case Failure(_)         => NoOpPlugin
        case Success(lagomPlay) => BundlePlugin && lagomPlay
      }
    }

  override def trigger = allRequirements

  override def projectSettings =
    Seq(
      javaOptions in Universal ++= Seq(
        s"-J-Xms${PlayBundleKeyDefaults.heapMemory.round1k.underlying}",
        s"-J-Xmx${PlayBundleKeyDefaults.heapMemory.round1k.underlying}"
      ),
      BundleKeys.nrOfCpus := PlayBundleKeyDefaults.nrOfCpus,
      BundleKeys.memory := PlayBundleKeyDefaults.residentMemory,
      BundleKeys.diskSpace := PlayBundleKeyDefaults.diskSpace,
      BundleKeys.endpoints := BundlePlugin.getDefaultWebEndpoints(Bundle).value,
      LagomBundleKeys.conductrBundleLibVersion := Version.conductrBundleLib
    )
}

/**
 * Provides support for Lagom Play Java projects
 */
object LagomPlayJavaBundlePlugin extends AutoPlugin {
  private val classLoader = this.getClass.getClassLoader

  override def requires =
    withContextClassloader(classLoader) { loader =>
      Reflection.getSingletonObject[Plugins.Basic](classLoader, "com.lightbend.lagom.sbt.LagomPlayJava$") match {
        case Failure(_)             => NoOpPlugin
        case Success(lagomPlayJava) => BundlePlugin && lagomPlayJava
      }
    }

  override def trigger = allRequirements

  override def projectSettings: Seq[Setting[_]] = Seq(
    libraryDependencies +=
      Library.lagomConductrBundleLib("java", LagomVersion.current, scalaBinaryVersion.value, LagomBundleKeys.conductrBundleLibVersion.value)
  )
}

/**
 * Provides support for Lagom Play Scala projects
 */
object LagomPlayScalaBundlePlugin extends AutoPlugin {
  private val classLoader = this.getClass.getClassLoader

  override def requires =
    withContextClassloader(classLoader) { loader =>
      Reflection.getSingletonObject[Plugins.Basic](classLoader, "com.lightbend.lagom.sbt.LagomPlayScala$") match {
        case Failure(_)              => NoOpPlugin
        case Success(lagomPlayScala) => BundlePlugin && lagomPlayScala
      }
    }

  override def trigger = allRequirements

  override def projectSettings: Seq[Setting[_]] = Seq(
    libraryDependencies +=
      Library.lagomConductrBundleLib("scala", LagomVersion.current, scalaBinaryVersion.value, LagomBundleKeys.conductrBundleLibVersion.value)
  )
}

/**
 * Bundle support for a Lagom service
 */
object LagomBundlePlugin extends AutoPlugin {

  import LagomBundleImport._
  import SbtNativePackager.autoImport._
  import BundlePlugin.autoImport._

  val autoImport = LagomBundleImport

  private val classLoader = this.getClass.getClassLoader

  override def requires =
    withContextClassloader(classLoader) { loader =>
      Reflection.getSingletonObject[Plugins.Basic](classLoader, "com.lightbend.lagom.sbt.Lagom$") match {
        case Failure(_)     => NoOpPlugin
        case Success(lagom) => BundlePlugin && lagom
      }
    }

  override def trigger = allRequirements

  // Configuration to add api tools library dependencies
  private val ApiToolsConfig = config("api-tools").hide

  override def projectSettings =
    bundleSettings(Bundle) ++ Seq(
      javaOptions in Universal ++= Seq(
        s"-J-Xms${PlayBundleKeyDefaults.heapMemory.round1k.underlying}",
        s"-J-Xmx${PlayBundleKeyDefaults.heapMemory.round1k.underlying}"
      ),
      BundleKeys.nrOfCpus := PlayBundleKeyDefaults.nrOfCpus,
      BundleKeys.memory := PlayBundleKeyDefaults.residentMemory,
      BundleKeys.diskSpace := PlayBundleKeyDefaults.diskSpace,
      ivyConfigurations += ApiToolsConfig,
      // scalaBinaryVersion.value uses the binary compatible scala version from the Lagom project
      LagomBundleKeys.conductrBundleLibVersion := Version.conductrBundleLib,
      LagomBundleKeys.endpointsPort := 9000,
      libraryDependencies += LagomImport.component("api-tools") % ApiToolsConfig,
      manageClasspath(ApiToolsConfig)
    )

  /**
   * Override bundle settings from sbt-bundle with the collected Lagom endpoints
   */
  private def bundleSettings(config: Configuration): Seq[Setting[_]] =
    inConfig(config)(
      Seq(
        BundleKeys.overrideEndpoints := Some(collectEndpoints(config).value + ("akka-remote" -> Endpoint("tcp"))),
        BundleKeys.startCommand ++= {
          val bindings = (for {
            endpoints <- (BundleKeys.overrideEndpoints in config).value
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
    (managedClasspath in config) := {
      val ct = (classpathTypes in config).value
      val report = update.value

      Classpaths.managedJars(config, ct, report)
    }

  /**
   * Use this to not perform tasks for the aggregated projects, i.e sub projects.
   */
  private def dontAggregate(keys: Scoped*): Seq[Setting[_]] =
    keys.map(aggregate in _ := false)

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
      val scalaInstanceLoader = scalaInstance.value.loader
      val managedClassPathValue = (managedClasspath in ApiToolsConfig).value
      val fullClassPathValue = (fullClasspath in Compile).value
      if (manualEndpoints != BundlePlugin.getDefaultEndpoints(config).value)
        manualEndpoints
      else {
        val classpath = toClasspathUrls(
          // managed classpath in api tools config contains the api tools library dependencies
          managedClassPathValue ++
            // full classpath containing the Lagom services, Lagom framework and all its dependencies
            fullClassPathValue
        )
        // Create class loader based on a classpath that contains all project related + api tools library classes
        val classLoader = new java.net.URLClassLoader(classpath, scalaInstanceLoader)
        // Lookup Lagom services
        val servicesAsString = ServiceDetector.services(classLoader)
        // Convert services string to `Map[String, Endpoint]`
        toConductrEndpoints(servicesAsString, (BundleKeys.enableAcls in config).value, (LagomBundleKeys.endpointsPort in config).value)
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
  private def toConductrEndpoints(services: String, asAcls: Boolean, endpointsPort: Int): Map[String, Endpoint] = {
    def toEndpoint(serviceNameAndPath: (String, Seq[String])): (String, Endpoint) = {
      def asAclEndpoint =
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
      def asServiceEndpoint =
        serviceNameAndPath match {
          case (serviceName, pathBegins) =>
            val uris = pathBegins.map(p => URI(s"http://:$endpointsPort$p")).to[ListSet] // ListSet makes it easier to test
            serviceName -> Endpoint("http", services = uris + URI(s"http://:$endpointsPort/$serviceName"))
        }

      if (asAcls) asAclEndpoint else asServiceEndpoint
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
            val path = if (pathBegin.endsWith("/")) pathBegin.dropRight(1) else pathBegin
            if (asAcls) path else path + "?preservePath"
        }
      pathlessServiceName -> pathBegins
    }
    serviceNamesAndPaths
      .map(toEndpoint)
      .foldLeft(Map.empty[String, Endpoint])(mergeEndpoint)
  }
}

/**
 * Provides support for Lagom Java projects
 */
object LagomJavaBundlePlugin extends AutoPlugin {
  private val classLoader = this.getClass.getClassLoader

  override def requires =
    withContextClassloader(classLoader) { loader =>
      Reflection.getSingletonObject[Plugins.Basic](classLoader, "com.lightbend.lagom.sbt.LagomJava$") match {
        case Failure(_)         => NoOpPlugin
        case Success(lagomJava) => BundlePlugin && lagomJava
      }
    }

  override def trigger = allRequirements

  override def projectSettings: Seq[Setting[_]] = Seq(
    libraryDependencies +=
      Library.lagomConductrBundleLib("java", LagomVersion.current, scalaBinaryVersion.value, LagomBundleKeys.conductrBundleLibVersion.value)
  )
}

/**
 * Provides support for Lagom Scala projects
 */
object LagomScalaBundlePlugin extends AutoPlugin {
  private val classLoader = this.getClass.getClassLoader

  override def requires =
    withContextClassloader(classLoader) { loader =>
      Reflection.getSingletonObject[Plugins.Basic](classLoader, "com.lightbend.lagom.sbt.LagomScala$") match {
        case Failure(_)          => NoOpPlugin
        case Success(lagomScala) => BundlePlugin && lagomScala
      }
    }

  override def trigger = allRequirements

  override def projectSettings: Seq[Setting[_]] = Seq(
    libraryDependencies +=
      Library.lagomConductrBundleLib("scala", LagomVersion.current, scalaBinaryVersion.value, LagomBundleKeys.conductrBundleLibVersion.value)
  )
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
        case Failure(t)               => sys.error("No mapping for Endpoints can not be resolved from Lagom project. Error: ${t.getMessage}")
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