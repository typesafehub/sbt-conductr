package com.lightbend.conductr.sbt

import org.scalatest.{ Matchers, WordSpec }

import ConductrPlugin.ProcessConverters._

class ProcessConvertersSpec extends WordSpec with Matchers {
  "ProcessConverters" should {
    "format typical arguments from Seq correctly" in {
      Seq("false", "true").withFlag("--enable-feature") shouldBe Seq("--enable-feature", "false", "--enable-feature", "true")

      Seq("hello world", "hi").withFlag("--greeting") shouldBe Seq("--greeting", "hello world", "--greeting", "hi")
    }

    "format dash (-) arguments from Seq correctly" in {
      Seq("false", "-Denable=false").withFlag("--arg") shouldBe Seq("--arg", "false", "--arg=-Denable=false")
      Seq("-Dmy.greeting=hello world").withFlag("--args") shouldBe Seq("--args=-Dmy.greeting=hello world")
    }

    "format typical arguments from Set correctly" in {
      Set("false", "true").withFlag("--enable-feature") shouldBe Seq("--enable-feature", "false", "--enable-feature", "true")

      Set("hello world", "hi").withFlag("--greeting") shouldBe Seq("--greeting", "hello world", "--greeting", "hi")
    }

    "format dash (-) arguments from Set correctly" in {
      Set("false", "-Denable=false").withFlag("--arg") shouldBe Seq("--arg", "false", "--arg=-Denable=false")
      Set("-Dmy.greeting=hello world").withFlag("--args") shouldBe Seq("--args=-Dmy.greeting=hello world")
    }

    "format typical arguments from Option correctly" in {
      None.withFlag("--arg") shouldBe Seq()

      Some("false").withFlag("--enable-feature") shouldBe Seq("--enable-feature", "false")
    }

    "format dash (-) arguments from Option correctly" in {
      Some("-Denable=false").withFlag("--arg") shouldBe Seq("--arg=-Denable=false")
    }
  }
}
