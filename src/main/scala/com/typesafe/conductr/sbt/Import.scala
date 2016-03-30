/*
 * Copyright © 2014-2016 Lightbend, Inc. All rights reserved.
 */

package com.typesafe.conductr.sbt

import sbt._

object Import {

  object ConductRKeys {

    // Sandbox keys
    val hasRpLicense = SettingKey[Boolean](
      "conductr-has-rp-license",
      "Checks that the project has a reactive platform license"
    )
    val isSbtBuild = SettingKey[Boolean](
      "conductr-is-sbt-build",
      "True if the project is THE sbt build project."
    )
    val sandbox = inputKey[Unit]("Sandbox task")

    // Conduct keys
    val discoveredDist = TaskKey[File](
      "conductr-discoverd-dist",
      "Any distribution produced by the current project"
    )
    val discoveredConfigDist = TaskKey[File](
      "conductr-discovered-config-dist",
      "Any additional configuration distribution produced by the current project"
    )
    val conduct = inputKey[Unit]("Conduct task")
  }
}