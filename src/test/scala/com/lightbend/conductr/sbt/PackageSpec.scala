package com.lightbend.conductr.sbt

import _root_.sbt._
import org.scalatest.{ Matchers, WordSpec }

class PackageSpec extends WordSpec with Matchers {
  "Library" should {
    "return ConductR Lib support for Play" in {
      val result = Library.playConductrBundleLib(playVersion = "2.5.6", scalaVersion = "2.11", conductrLibVersion = "1.1.3")
      result shouldBe ("com.typesafe.conductr" % s"play25-conductr-bundle-lib_2.11" % "1.1.3")
    }

    "return ConductR Lib support for Lagom" in {
      val result = Library.lagomConductrBundleLib(language = "java", lagomVersion = "1.3.5", scalaVersion = "2.11", conductrLibVersion = "2.0.0")
      result shouldBe ("com.typesafe.conductr" % s"lagom13-java-conductr-bundle-lib_2.11" % "2.0.0")
    }
  }
}
