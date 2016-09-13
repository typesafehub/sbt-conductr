package com.lightbend.conductr.sbt

import org.scalatest.{ Matchers, WordSpec }

class ConductrVersionSpec extends WordSpec with Matchers {

  import BundleImport.ConductrVersion._

  "ConductrVersion" should {

    "order the versions correctly" in {
      V1_1 < V2
      V2 > V1_1
    }
  }
}
