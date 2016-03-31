/*
 * Copyright © 2014-2016 Lightbend, Inc. All rights reserved.
 */

package com.lightbend.conductr.sbt

import sbt._
import com.typesafe.sbt.SbtNativePackager.Universal
import scala.concurrent.duration.FiniteDuration
import scala.util.matching.Regex

object BundleImport {

  /**
   * Common interface which describes request mapping.
   */
  trait RequestMapping

  /**
   * Common interface which describes HTTP based request mapping
   */
  trait HttpRequestMapping extends RequestMapping {
    def method: Option[String]
    def rewrite: Option[String]
  }

  /**
   * Common interface which describes request mapping(s) that can be exposed by a service endpoint, i.e. sequence of
   * HTTP paths in case of HTTP-based endpoint, or set of ports in case of TCP-based endpoint
   */
  trait ProtocolFamilyRequestMapping {
    def protocolFamily: String
  }

  object Http {
    import scala.language.implicitConversions

    /**
     * Represents HTTP methods
     */
    object Method extends Enumeration {
      val GET, POST, PUT, DELETE, HEAD, TRACE, CONNECT = Value
    }

    implicit def method(m: String): Method.Value =
      Method.withName(m)

    case class Request(method: Option[Method.Value], path: Either[String, Regex], rewrite: Option[String])

    implicit def request1(r: String): Request =
      Request(None, Left(r), None)
    implicit def regexRequest1(r: Regex): Request =
      Request(None, Right(r), None)

    implicit def request2(r: (String, String)): Request =
      Request(Some(r._1), Left(r._2), None)
    implicit def regexRequest2(r: (String, Regex)): Request =
      Request(Some(r._1), Right(r._2), None)
    implicit def regexToStringRequest2(r: (Regex, String)): Request =
      Request(None, Right(r._1), Some(r._2))

    implicit def request3(r: ((String, String), String)): Request =
      Request(Some(r._1._1), Left(r._1._2), Some(r._2))
    implicit def regexRequest3(r: ((String, Regex), String)): Request =
      Request(Some(r._1._1), Right(r._1._2), Some(r._2))
  }

  /**
   * Represents HTTP request mapping, i.e. sequence of HTTP path(s) exposed by a service endpoint
   *
   * @param requestMappings sequence of HTTP path(s) exposed by a service endpoint
   */
  case class Http(requestMappings: Http.Request*) extends ProtocolFamilyRequestMapping {
    val protocolFamily = "http"
  }

  object Tcp {
    /**
     * Represents TCP port exposed by a service endpoint
     *
     * @param port tcp port exposed by the service endpoint
     */
    case class Request(port: Int) extends RequestMapping

    /**
     * Represents TCP request mapping, i.e. set of TCP port(s) exposed by a service endpoint
     *
     * @param ports set of TCP port(s) exposed by a service endpoint
     */
    def apply(ports: Int*): Tcp =
      Tcp(ports.toSet.map(Tcp.Request))
  }

  /**
   * Represents TCP request mapping, i.e. set of TCP port(s) exposed by a service endpoint
   *
   * @param requestMappings set of TCP port(s) exposed by a service endpoint
   */
  case class Tcp(requestMappings: Set[Tcp.Request]) extends ProtocolFamilyRequestMapping {
    val protocolFamily = "tcp"
  }

  object Udp {
    /**
     * Represents UDP port exposed by a service endpoint
     *
     * @param port udp port exposed by the service endpoint
     */
    case class Request(port: Int) extends RequestMapping

    /**
     * Represents UDP request mapping, i.e. set of UDP port(s) exposed by a service endpoint
     *
     * @param ports set of UDP port(s) exposed by a service endpoint
     */
    def apply(ports: Int*): Udp =
      Udp(ports.toSet.map(Udp.Request))
  }

  /**
   * Represents UDP request mapping, i.e. set of UDP port(s) exposed by a service endpoint
   *
   * @param requestMappings set of UDP port(s) exposed by a service endpoint
   */
  case class Udp(requestMappings: Set[Udp.Request]) extends ProtocolFamilyRequestMapping {
    val protocolFamily = "udp"
  }

  object RequestAcl {
    /**
     * Represents a set of request ACL exposed by a service endpoint.
     * Request ACL can be either a sequence of HTTP paths or set of TCP ports.
     *
     * @param protocolFamilyRequestMappings request ACL exposed by a service endpoint
     */
    def apply(protocolFamilyRequestMappings: ProtocolFamilyRequestMapping*): RequestAcl =
      RequestAcl(protocolFamilyRequestMappings.toSet)
  }

  /**
   * Represents a set of request ACL exposed by a service endpoint.
   * Request ACL can be either a sequence of HTTP paths or set of TCP ports.
   *
   * @param protocolFamilyRequestMappings request ACL exposed by a service endpoint
   */
  case class RequestAcl(protocolFamilyRequestMappings: Set[ProtocolFamilyRequestMapping])

  object Endpoint {
    /**
     * Represents a service endpoint.
     *
     * @param bindProtocol the protocol to bind for this endpoint, e.g. "http"
     * @param bindPort the port the bundle component’s application or service actually binds to; when this is 0 it will be dynamically allocated (which is the default)
     * @param services the public-facing ways to access the endpoint form the outside world with protocol, port, and/or path
     */
    def apply(bindProtocol: String, bindPort: Int = 0, services: Set[URI] = Set.empty): Endpoint =
      new Endpoint(bindProtocol, bindPort, Some(services), None, None)

    /**
     * Represents a service endpoint.
     *
     * @param bindProtocol the protocol to bind for this endpoint, e.g. "http"
     * @param bindPort the port the bundle component’s application or service actually binds to; when this is 0 it will be dynamically allocated (which is the default)
     * @param serviceName the name of the service exposed by this service endpoint
     * @param acls list of protocol and its corresponding paths (for http) or ports (for tcp) exposed by the service endpoint
     */
    def apply(bindProtocol: String, bindPort: Int, serviceName: String, acls: RequestAcl*): Endpoint =
      new Endpoint(bindProtocol, bindPort, None, Some(serviceName), Some(acls.toSet))
  }

  /**
   * Represents a service endpoint.
   *
   * @param bindProtocol the protocol to bind for this endpoint, e.g. "http"
   * @param bindPort the port the bundle component’s application or service actually binds to; when this is 0 it will be dynamically allocated (which is the default)
   * @param services **deprecated** - the public-facing ways to access the endpoint form the outside world with protocol, port, and/or path
   * @param serviceName the name of the service exposed by this service endpoint
   * @param acls list of protocol and its corresponding paths (for http) or ports (for tcp) exposed by the service endpoint
   */
  case class Endpoint(
    bindProtocol: String,
    bindPort: Int,
    services: Option[Set[URI]],
    serviceName: Option[String],
    acls: Option[Set[RequestAcl]]
  )

  object BundleConfVersions extends Enumeration {
    val V_1_1_0 = Value("1.1.0")
    val V_1_2_0 = Value("1.2.0")
  }

  object BundleKeys {

    // Scheduling settings
    val bundleConfVersion = SettingKey[BundleConfVersions.Value](
      "bundle-conf-version",
      "The format of the bundle configuration file to generate. By default this is 1.1.0."
    )

    val system = SettingKey[String](
      "bundle-system",
      "A logical name that can be used to associate multiple bundles with each other."
    )

    val nrOfCpus = SettingKey[Double](
      "bundle-nr-of-cpus",
      "The number of cpus required to run the bundle (can be fractions thereby expressing a portion of CPU). Required."
    )

    val memory = SettingKey[Bytes](
      "bundle-memory",
      "The amount of memory required to run the bundle. This value must a multiple of 1024 greater than 2 MB. Append the letter k or K to indicate kilobytes, or m or M to indicate megabytes. Required."
    )

    val diskSpace = SettingKey[Bytes](
      "bundle-disk-space",
      "The amount of disk space required to host an expanded bundle and configuration. Append the letter k or K to indicate kilobytes, or m or M to indicate megabytes. Required."
    )

    val roles = SettingKey[Set[String]](
      "bundle-roles",
      """The types of node in the cluster that this bundle can be deployed to. Defaults to "web"."""
    )

    // General settings

    val bundleConf = TaskKey[String](
      "bundle-conf",
      "The bundle configuration file contents"
    )

    val bundleType = SettingKey[Configuration](
      "bundle-type",
      "The type of configuration that this bundling relates to. By default Universal is used."
    )

    val executableScriptPath = SettingKey[String](
      "bundle-executable-script-path",
      "The relative path of the executableScript within the bundle."
    )

    val startCommand = TaskKey[Seq[String]](
      "bundle-start-command",
      "Command line args required to start the component. Paths are expressed relative to the component's bin folder. The default is to use the bash script in the bin folder."
    )

    val endpoints = SettingKey[Map[String, Endpoint]](
      "bundle-endpoints",
      """Declares endpoints. The default is Map("web" -> Endpoint("http", 0, Set("http://:9000"))) where the service name is the name of this project. The "web" key is used to form a set of environment variables for your components. For example you will have a `WEB_BIND_PORT` in this example."""
    )

    val overrideEndpoints = TaskKey[Option[Map[String, Endpoint]]](
      "bundle-override-endpoints",
      "Overrides the endpoints settings key with new endpoints. This task should be used if the endpoints need to be specified programmatically. The default is None."
    )

    val checkInitialDelay = SettingKey[FiniteDuration](
      "bundle-check-initial-delay",
      "Initial delay before the check uris are triggered. The 'FiniteDuration' value gets rounded up to full seconds. Default is 3 seconds."
    )

    val checks = SettingKey[Seq[URI]](
      "bundle-check-uris",
      """Declares uris to check to signal to ConductR that the bundle components have started for situations where component doesn't do that. For example Seq(uri("$WEB_HOST?retry-count=5&retry-delay=2")) will check that a endpoint named "web" will be checked given its host environment var. Once that URL becomes available then ConductR will be signalled that the bundle is ready. Optional params are: 'retry-count': Number of retries, 'retry-delay': Delay in seconds between retries, 'docker-timeout': Timeout in seconds for docker container start."""
    )

    val configurationName = SettingKey[String](
      "bundle-configuration-name",
      "The name of the directory of the additional configuration to use. Defaults to 'default'"
    )

    val compatibilityVersion = SettingKey[String](
      "bundle-compatibility-version",
      "A versioning scheme that will be included in a bundle's name that describes the level of compatibility with bundles that go before it. By default we take the major version component of a version as defined by http://semver.org/. However you can make this mean anything that you need it to mean in relation to bundles produced prior to it. We take the notion of a compatibility version from http://ometer.com/parallel.html."
    )

    val systemVersion = SettingKey[String](
      "bundle-system-version",
      "A version to associate with a system. This setting defaults to the value of compatibilityVersion."
    )
  }

  case class Bytes(underlying: Long) extends AnyVal {
    def round1k: Bytes =
      Bytes((Math.max(underlying - 1, 0) >> 10 << 10) + 1024)
  }

  object ByteConversions {
    implicit class IntOps(value: Int) {
      def KB: Bytes =
        Bytes(value * 1000L)
      def MB: Bytes =
        Bytes(value * 1000000L)
      def GB: Bytes =
        Bytes(value * 1000000000L)
      def TB: Bytes =
        Bytes(value * 1000000000000L)
      def KiB: Bytes =
        Bytes(value.toLong << 10)
      def MiB: Bytes =
        Bytes(value.toLong << 20)
      def GiB: Bytes =
        Bytes(value.toLong << 30)
      def TiB: Bytes =
        Bytes(value.toLong << 40)
    }
  }

  object URI {
    def apply(uri: String): URI =
      new sbt.URI(uri)
  }

  val Bundle = config("bundle") extend Universal

  val BundleConfiguration = config("configuration") extend Universal

  val DefaultEndpoints = Map("web" -> Endpoint("http", 0, Set(URI("http://:9000"))))
}
