/*
 * Copyright © 2014 Typesafe, Inc. All rights reserved.
 */

package com.typesafe.conductr.sbt

import akka.actor.ActorSystem
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpec }
import sbt.complete.Parser

class SbtTypesafeConductRSpec extends WordSpec with Matchers with BeforeAndAfterAll {

  "Parsing Input" should {
    "parse properly formed IP:PORT combinations" in {

      val input = List(
        "1.1.1.1", "12.12.12.12", "123.123.123.123",
        "123.123.123.123:1", "123.123.123.123:11",
        "123.123.123.123:1111", "123.123.123.123:11111"
      )
      input foreach {
        case (i) => Parser.parse(i, TypesafeConductRPlugin.Parsers.parseIpAddress) shouldBe Right(i)
      }
    }
    "reject invalid IP:PORT combinations" in {

      val input = List(
        "", "asd", "123.a.123.123",
        "123.123.", "123.123.123.123:",
        "123.123.123.123:asd", "."
      )
      val parsed = input map (Parser.parse(_, TypesafeConductRPlugin.Parsers.parseIpAddress))

      parsed foreach {
        case Right(r) => fail(r + " is a valid IP:PORT")
        case Left(r)  => {}
      }
    }
  }

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
        case (input, fullUrl) => TypesafeConductR.prepareConductrUrl(input).toString shouldBe fullUrl
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
