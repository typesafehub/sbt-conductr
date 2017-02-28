package com.lightbend.conductr.sbt

import sbt._

import com.typesafe.sbt.SbtNativePackager

/**
 * Adds Lagom concerns to the ConductR plugin
 */
object LagomConductRPlugin extends AutoPlugin {

  import ConductrPlugin.autoImport._
  import ConductrKeys._

  import SbtNativePackager.autoImport._

  override def requires = ConductrPlugin && LagomBundlePlugin

  override def trigger = allRequirements

  override def buildSettings: Seq[Setting[_]] =
    List(
      // Cassandra should be started as the first service to not produce unnecessary warnings in the log
      // that the Cassandra contact point is not available
      installationData := installationDataTask.value ++ installationData.value
    )

  private def installationDataTask: Def.Initialize[Task[Seq[InstallationData]]] = Def.task {
    List(InstallationData("cassandra", Left("cassandra"), None))
  }
}
