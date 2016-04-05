package com.lightbend.conductr.sbt

import com.typesafe.sbt.SbtNativePackager.Universal
import sbt._

object LagomBundleImport {

  // Configuration to produce a bundle configuration for cassandra
  val CassandraConfiguration = config("cassandra-configuration") extend Universal

  object LagomBundleKeys {
    val conductrBundleLibVersion = BaseKeys.conductrBundleLibVersion
  }
}
