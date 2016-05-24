package com.lightbend.conductr.sbt

import sbt._

object LagomBundleImport {

  // Configuration to produce a bundle configuration for cassandra
  val CassandraConfiguration = config("cassandra-configuration") extend BundleImport.BundleConfiguration

  object LagomBundleKeys {
    val conductrBundleLibVersion = BaseKeys.conductrBundleLibVersion

    val endpointsPort = SettingKey[Int](
      "lagom-bundle-endpoints-port",
      "Declares the port for each service endpoint that gets exposed via the proxy to the outside world, e.g. http://:9000/myservice. Defaults to 9000. This setting is only used if 'BundleKeys.enableAcls' is 'false'. In ConductR 1.1 'enableAcls' is set to 'true'. From ConductR 1.2 onwards 'enableAcls' is set to 'false'."
    )
  }
}
