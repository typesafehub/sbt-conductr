package com.lightbend.conductr.sbt

import com.typesafe.sbt.SbtNativePackager.Universal
import sbt._

object LagomBundleImport {

  // Configuration to produce a bundle configuration for cassandra
  val CassandraConfiguration = config("cassandra-configuration") extend Universal

  object LagomBundleKeys {
    val conductrBundleLibVersion = BaseKeys.conductrBundleLibVersion

    @deprecated("This setting is no longer used as endpoint port is not of Bundle Endpoint declaration using HTTP request ACL", since = "2.0.0")
    val endpointsPort = SettingKey[Int](
      "lagom-bundle-endpoints-port",
      "Declares the port for each service endpoint that gets exposed to the outside world, e.g. http://:9000/myservice. Defaults to 9000."
    )
  }
}
