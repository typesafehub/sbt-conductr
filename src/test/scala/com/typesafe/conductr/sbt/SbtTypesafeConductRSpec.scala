/*
 * Copyright © 2014 Typesafe, Inc. All rights reserved.
 */

package com.typesafe.conductr.sbt

import akka.actor.ActorSystem
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpec }

class SbtTypesafeConductRSpec extends WordSpec with Matchers with BeforeAndAfterAll {

  "Setting ConductR URL" should {
    "be prepared to full format given partial input" in {
      val inputToUrls = Map(
        "192.1.1.28" -> "http://192.1.1.28:9005",
        "http://192.1.1.28" -> "http://192.1.1.28:9005",
        "http://192.1.1.28/my/example" -> "http://192.1.1.28:9005/my/example",
        "http://192.1.1.28/my/example/" -> "http://192.1.1.28:9005/my/example/",
        "http://192.1.1.28/example" -> "http://192.1.1.28:9005/example",
        "http://192.1.1.28/example/" -> "http://192.1.1.28:9005/example/",
        "http://192.1.1.28:9999" -> "http://192.1.1.28:9999",
        "http://192.1.1.28:9999/example" -> "http://192.1.1.28:9999/example",
        "192.1.1.28/example" -> "http://192.1.1.28:9005/example",
        "192.1.1.28:9999/example" -> "http://192.1.1.28:9999/example"
      )

      inputToUrls foreach {
        case (input, fullUrl) => SbtTypesafeConductR.prepareConductrUrl(input).toString shouldBe fullUrl
      }
    }
  }

  private implicit val system =
    ActorSystem()

  override protected def afterAll(): Unit = {
    system.shutdown()
    system.awaitTermination()
  }

}
