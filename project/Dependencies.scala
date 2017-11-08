/*
 * Copyright © 2014-2016 Lightbend, Inc. All rights reserved.
 */

import sbt._

object Version {
  val nativePackager    = "1.3.2"
  val play              = "2.3.10" // Using Play 2.3 to use Scala 2.10 library
  val scalaTest         = "2.2.6"
  val scala             = "2.10.6"
}

object Library {
  val nativePackager         = "com.typesafe.sbt"      %  "sbt-native-packager"         % Version.nativePackager
  val playJson               = "com.typesafe.play"     %% "play-json"                   % Version.play
  val scalaTest              = "org.scalatest"         %% "scalatest"                   % Version.scalaTest % "test"
}
