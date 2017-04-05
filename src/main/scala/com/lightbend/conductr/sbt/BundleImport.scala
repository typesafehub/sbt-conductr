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
  sealed trait ProtocolFamilyRequestMapping {
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

    case class Request(method: Option[Method.Value] = None, path: Either[String, Regex], rewrite: Option[String] = None)

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
      if (acls.flatMap(_.protocolFamilyRequestMappings).nonEmpty)
        new Endpoint(bindProtocol, bindPort, None, Some(serviceName), Some(acls.toSet))
      else
        new Endpoint(bindProtocol, bindPort, Some(Set.empty), Some(serviceName), None)

    /**
     * Represents a service endpoint.
     *
     * @param bindProtocol the protocol to bind for this endpoint, e.g. "http"
     * @param bindPort the port the bundle component’s application or service actually binds to; when this is 0 it will be dynamically allocated (which is the default)
     * @param acls list of protocol and its corresponding paths (for http) or ports (for tcp) exposed by the service endpoint
     */
    def apply(bindProtocol: String, bindPort: Int, acls: RequestAcl*): Endpoint = {
      if (acls.flatMap(_.protocolFamilyRequestMappings).nonEmpty)
        new Endpoint(bindProtocol, bindPort, None, None, Some(acls.toSet))
      else
        new Endpoint(bindProtocol, bindPort, Some(Set.empty), None, None)
    }

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

  object EndpointType extends Enumeration {
    val Service, Acl = Value
  }

  object BundleConfVersion extends Enumeration {
    val V1 = Value("1")
  }

  object ConductrVersion extends Enumeration {
    // The order of the versions matter because there are used to compare the versions with `>=`, `<=`, etc.
    // Therefore the oldest version must come first, the latest version at last
    val V1_1 = Value("1.1")
    val V2_0 = Value("2.0")
  }

  object BundleKeys {
    val bundleConf = TaskKey[String](
      "bundle-conf",
      "The bundle configuration file contents"
    )

    val bundleConfVersion = SettingKey[BundleConfVersion.Value](
      "bundle-conf-version",
      "The version of the bundle.conf file. By default this is 1."
    )

    val bundleType = SettingKey[Configuration](
      "bundle-type",
      "The type of configuration that this bundling relates to. By default Universal is used."
    )

    val checks = SettingKey[Seq[URI]](
      "bundle-check-uris",
      """Declares uris to check to signal to ConductR that the bundle components have started for situations where component doesn't do that. For example Seq(uri("$WEB_HOST?retry-count=5&retry-delay=2")) will check that a endpoint named "web" will be checked given its host environment var. Once that URL becomes available then ConductR will be signalled that the bundle is ready. Optional params are: 'retry-count': Number of retries, 'retry-delay': Delay in seconds between retries, 'docker-timeout': Timeout in seconds for docker container start."""
    )

    val checkInitialDelay = SettingKey[FiniteDuration](
      "bundle-check-initial-delay",
      "Initial delay before the check uris are triggered. The 'FiniteDuration' value gets rounded up to full seconds. Default is 3 seconds."
    )

    val compatibilityVersion = SettingKey[String](
      "bundle-compatibility-version",
      "A versioning scheme that will be associated with a bundle that describes the level of compatibility with the bundle that went before it. ConductR can use this property to reason about the compatibility of one bundle to another given the same bundle name. By default we take the major version component of a project version where major is defined by http://semver.org/. However you can make this mean anything that you need it to mean in relation to the bundle produced prior to it. We take the notion of a compatibility version from http://ometer.com/parallel.html."
    )

    val tags = SettingKey[Seq[String]](
      "bundle-tags",
      """An array of strings that can be used to further qualify a bundle name. Just as with a name, these strings are intended for human consumption and ConductR makes no assumptions about their value - see "compatibilityVersion" for semantically relevant versioning. Tags are often represented as versions e.g. "1.0.0-beta.1", "version1"" etc. By default we use the project version."""
    )

    val annotations = SettingKey[Option[String]](
      "bundle-annotations",
      """A HOCON string representing additional metadata that you may wish to associate with a bundle. Key names should be in accordance with the OCI image annotation conventions: https://github.com/opencontainers/image-spec/blob/master/annotations.md."""
    )

    val conductrTargetVersion = SettingKey[ConductrVersion.Value](
      "bundle-conductr-target-version",
      "The version of ConductR to that this bundle can be deployed on. During bundle creation a compatibility check is made whether this bundle can be deployed on the specified ConductR version. Defaults to 2.0."
    )

    val configurationName = SettingKey[String](
      "bundle-configuration-name",
      "The name of the directory of the additional configuration to use. Defaults to 'default'"
    )

    val diskSpace = SettingKey[Bytes](
      "bundle-disk-space",
      "The amount of disk space required to host an expanded bundle and configuration. Append the letter k or K to indicate kilobytes, or m or M to indicate megabytes. Required."
    )

    val enableAcls = SettingKey[Boolean](
      "bundle-enable-acls",
      "Acls can be declared on an endpoint if this setting is 'true'. Otherwise only service endpoints can be declared. Endpoint acls can be used from ConductR 1.2 onwards. Therefore, the default in ConductR 1.1- is 'false' and in ConductR 1.2+ 'true'."
    )

    val endpoints = TaskKey[Map[String, Endpoint]](
      "bundle-endpoints",
      """Declares endpoints. The default is Map("<project-name>" -> Endpoint("http", 0, Set.empty)) where the <project-name> is the name of your sbt project. The endpoint key is used to form a set of environment variables for your components, e.g. for the endpoint key "web" ConductR creates the environment variable `WEB_BIND_PORT`."""
    )

    val executableScriptPath = SettingKey[String](
      "bundle-executable-script-path",
      "The relative path of the executableScript within the bundle."
    )

    val startCommand = TaskKey[Seq[String]](
      "bundle-start-command",
      "Command line args required to start the component. Paths are expressed relative to the component's bin folder. The default is to use the bash script in the bin folder."
    )

    val memory = SettingKey[Bytes](
      "bundle-memory",
      "The amount of resident memory required to run the bundle. Use the Unix `top` command to determine this value by observing the `RES` and rounding up to the nearest 10MiB. Required."
    )

    val minMemoryCheckValue = SettingKey[Bytes](
      "bundle-min-memory-check-value",
      "The minimum value for the `memory` setting that is checked when creating a bundle. Defaults to 384MiB."
    )

    val nrOfCpus = SettingKey[Double](
      "bundle-nr-of-cpus",
      "The minimum number of cpus required to run the bundle (can be fractions thereby expressing a portion of CPU). This value is considered when starting a bundle on a node. If the specified CPUs exceeds the available CPUs on a node, then this node is not considered for scaling the bundle. Once running, the application is not restricted to the given value and tries to use all available CPUs on the node. Required."
    )

    val overrideEndpoints = TaskKey[Option[Map[String, Endpoint]]](
      "bundle-override-endpoints",
      "Overrides the endpoints settings key with new endpoints. This task should be used if the endpoints need to be specified programmatically. The default is None."
    )

    val roles = SettingKey[Set[String]](
      "bundle-roles",
      """The types of node in the cluster that this bundle can be deployed to. Defaults to "web"."""
    )

    val system = SettingKey[String](
      "bundle-system",
      "A logical name that can be used to associate multiple bundles with each other."
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
}
