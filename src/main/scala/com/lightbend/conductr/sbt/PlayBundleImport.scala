package com.lightbend.conductr.sbt

import sbt._

object PlayBundleImport {

  val conductrBundleLibVersion = BaseKeys.conductrBundleLibVersion

  val playClassLoading = taskKey[String]("play-class-loading")
}
