package com.lightbend.conductr.sbt

import com.typesafe.sbt.SbtNativePackager.Universal
import sbt._

object LagomBundleImport {

  // Configuration to produce a bundle configuration for cassandra
  val CassandraConfiguration = config("cassandra-configuration") extend Universal

  object LagomBundleKeys {

    val conductrBundleLibVersion = SettingKey[String](
      "lagom-bundle-conductr-bundle-lib-version",
      "The version of conductr-bundle-lib to depend on. Defaults to 1.4.2"
    )

    val endpointsPort = SettingKey[Int](
      "lagom-bundle-endpoints-port",
      "Declares the port for each service endpoint that gets exposed to the outside world, e.g. http://:9000/myservice. Defaults to 9000."
    )
  }
}
