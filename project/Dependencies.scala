/*
 * Copyright © 2014-2016 Lightbend, Inc. All rights reserved.
 */

import sbt._

object Version {
  val nativePackager    = "1.0.6"
  val lagom             = "1.0.0-M1"
  val play              = "2.3.10" // Using Play 2.3 to ensure compatibility across sbt-conductr
  val scalaTest         = "2.2.6"
  val scala             = "2.10.6"
}

object Library {
  val nativePackager         = "com.typesafe.sbt"      %  "sbt-native-packager"         % Version.nativePackager
  val sbtLagom               = "com.lightbend.lagom"   %  "lagom-sbt-plugin"            % Version.lagom
  val playJson               = "com.typesafe.play"     %% "play-json"                   % Version.play
  val scalaTest              = "org.scalatest"         %% "scalatest"                   % Version.scalaTest % "test"
}