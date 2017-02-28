/*
 * Copyright © 2014-2016 Lightbend, Inc. All rights reserved.
 */

package com.lightbend.conductr.sbt

import java.nio.file.Path

import sbt._

object ConductrImport {

  object InstallationData {
    /**
     * Flatten our Either type to a string.
     */
    def nameOrPath(e: Either[String, Path]): String =
      e match {
        case Left(v)  => v
        case Right(v) => v.toString
      }
  }

  /**
   * Provides the data required to load and run a bundle
   * @param bundleName the normalized name of the bundle
   * @param bundleFile the bundle for resolving or a file path
   * @param bundleConfigFile and optional configuration bundle file
   */
  case class InstallationData(
    bundleName: String,
    bundleFile: Either[String, Path],
    bundleConfigFile: Option[Path]
  )

  object ConductrKeys {
    val hasRpLicense = SettingKey[Boolean](
      "conductr-has-rp-license",
      "Checks that the project has a reactive platform license"
    )

    val isSbtBuild = SettingKey[Boolean](
      "conductr-is-sbt-build",
      "True if the project is THE sbt build project."
    )

    val sandbox = inputKey[Unit]("Sandbox task")

    val discoveredDist = TaskKey[File](
      "conductr-discoverd-dist",
      "Any distribution produced by the current project"
    )

    val discoveredConfigDist = TaskKey[File](
      "conductr-discovered-config-dist",
      "Any additional configuration distribution produced by the current project"
    )

    val conduct = inputKey[Unit]("Conduct task")

    val installationData = TaskKey[Seq[InstallationData]](
      "conductr-installation-data",
      "Returns an array of InstallationData containing the information for installing an entire build's bundles"
    )

    val generateInstallationScript = TaskKey[File](
      "generate-installation-script",
      "Produces the installation script to install the entire build's bundles"
    )

    val install = TaskKey[Unit](
      "install",
      "Installs an entire build's bundles"
    )
  }
}