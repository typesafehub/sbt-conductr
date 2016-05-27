package com.lightbend.conductr.sbt

import sbt._

import com.typesafe.sbt.SbtNativePackager

/**
 * Adds Lagom concerns to the ConductR plugin
 */
object LagomConductRPlugin extends AutoPlugin {

  import ConductrPlugin.autoImport._
  import ConductrKeys._

  import LagomBundlePlugin.autoImport._

  import SbtNativePackager.autoImport._
  import NativePackagerKeys._

  override def requires = ConductrPlugin && LagomBundlePlugin

  override def trigger = allRequirements

  override def buildSettings: Seq[Setting[_]] =
    List(
      installationData ++= installationDataTask.value
    )

  private def installationDataTask: Def.Initialize[Task[Seq[InstallationData]]] = Def.task {
    val cassandraConfigPath = (dist in CassandraConfiguration).value.toPath
    List(InstallationData("cassandra", Left("cassandra"), Some(cassandraConfigPath)))
  }
}
