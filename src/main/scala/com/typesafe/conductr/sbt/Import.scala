/*
 * Copyright © 2014-2016 Lightbend, Inc. All rights reserved.
 */

package com.typesafe.conductr.sbt

import sbt._

object Import {

  object ConductRKeys {
    val conduct = inputKey[Unit]("ConductR task.")
    val conductrDiscoveredDist = taskKey[File]("Any distribution produced by the current project")
    val conductrDiscoveredConfigDist = taskKey[File]("Any additional configuration distribution produced by the current project")
  }

  val conduct = ConductRKeys.conduct
}