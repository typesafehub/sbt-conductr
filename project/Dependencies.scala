/*
 * Copyright © 2014-2016 Lightbend, Inc. All rights reserved.
 */

import sbt._

object Version {
  val config            = "1.3.1"
  val nativePackager    = "1.3.2"
  val play              = "2.6.8" // Need to use a Play version that's published for 2.10 and 2.12
  val sbt013            = "0.13.16"
  val sbt10             = "1.0.3"
  val scalaTest         = "3.0.4"
  val scala210          = "2.10.6"
  val scala212          = "2.12.4"
  val sjsonnew          = "0.8.2"
}

object Library {
  val config                 = "com.typesafe"          %  "config"                      % Version.config
  val nativePackager         = "com.typesafe.sbt"      %  "sbt-native-packager"         % Version.nativePackager
  val playJson               = "com.typesafe.play"     %% "play-json"                   % Version.play
  val scalaTest              = "org.scalatest"         %% "scalatest"                   % Version.scalaTest % "test"
  val sjsonnew               = "com.eed3si9n"          %% "sjson-new-core"              % Version.sjsonnew
}
