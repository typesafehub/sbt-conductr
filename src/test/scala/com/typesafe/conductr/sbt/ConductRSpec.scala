/*
 * Copyright © 2014 Typesafe, Inc. All rights reserved.
 */

package com.typesafe.conductr.sbt

import akka.actor.Status.Failure
import akka.actor.{ Cancellable, ActorRef, ActorSystem }
import akka.http.scaladsl.model.Uri
import akka.stream.scaladsl.Source
import akka.testkit.TestActor.AutoPilot
import akka.testkit.{ TestActor, TestProbe }
import akka.util.Timeout
import com.typesafe.conductr.client.ConductRController
import com.typesafe.conductr.client.ConductRController._
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpec }
import sbt.{ Level, Logger }
import scala.concurrent.duration._

class ConductRSpec extends WordSpec with Matchers with BeforeAndAfterAll {
  import com.typesafe.conductr.TestBundle._
  val callTimeout = Timeout(1 second)
  implicit val printlnLogger = new Logger {
    def log(level: Level.Value, message: => String): Unit = println(message)
    def success(message: => String): Unit = println(message)
    def trace(t: => Throwable): Unit = t.printStackTrace()
  }

  Seq(
    ConductRController.ApiVersion.V1,
    ConductRController.ApiVersion.V2
  ).foreach { implicit apiVersion =>
      s"API version $apiVersion" when {

        "runBundle" should {
          "handle successful scenario" in {
            val conductrController = TestProbe()
            implicit val conductrControllerRef = conductrController.ref
            replyWith(conductrController,
              """
                | { "requestId": "request-1234" }
              """.stripMargin)

            ConductR.runBundle("my-bundle", Some(3), Some("other-bundle"), callTimeout)

            conductrController.expectMsg(RunBundle(apiVersion, "my-bundle", 3, Some("other-bundle")))
          }

          "handle unexpected reply" in {
            val conductrController = TestProbe()
            implicit val conductrControllerRef = conductrController.ref
            replyWith(conductrController,
              """
                | {}
              """.stripMargin)

            intercept[RuntimeException] {
              ConductR.runBundle("my-bundle", Some(3), Some("other-bundle"), callTimeout)
            }

            conductrController.expectMsg(RunBundle(apiVersion, "my-bundle", 3, Some("other-bundle")))
          }

          "handle failure" in {
            val conductrController = TestProbe()
            implicit val conductrControllerRef = conductrController.ref
            replyWith(conductrController, Failure(new RuntimeException("test-only")))

            intercept[RuntimeException] {
              ConductR.runBundle("my-bundle", Some(3), Some("other-bundle"), callTimeout)
            }

            conductrController.expectMsg(RunBundle(apiVersion, "my-bundle", 3, Some("other-bundle")))
          }
        }

        "stopBundle" should {
          "handle successful scenario" in {
            val conductrController = TestProbe()
            implicit val conductrControllerRef = conductrController.ref
            replyWith(conductrController,
              """
                | { "requestId": "request-1234" }
              """.stripMargin)

            ConductR.stopBundle("my-bundle", callTimeout)

            conductrController.expectMsg(StopBundle(apiVersion, "my-bundle"))
          }

          "handle unexpected reply" in {
            val conductrController = TestProbe()
            implicit val conductrControllerRef = conductrController.ref
            replyWith(conductrController,
              """
                | {}
              """.stripMargin)

            intercept[RuntimeException] {
              ConductR.stopBundle("my-bundle", callTimeout)
            }

            conductrController.expectMsg(StopBundle(apiVersion, "my-bundle"))
          }

          "handle failure" in {
            val conductrController = TestProbe()
            implicit val conductrControllerRef = conductrController.ref
            replyWith(conductrController, Failure(new RuntimeException("test-only")))

            intercept[RuntimeException] {
              ConductR.stopBundle("my-bundle", callTimeout)
            }

            conductrController.expectMsg(StopBundle(apiVersion, "my-bundle"))
          }
        }

        "unloadBundle" should {
          "handle successful scenario" in {
            val conductrController = TestProbe()
            implicit val conductrControllerRef = conductrController.ref
            replyWith(conductrController,
              """
                | { "requestId": "request-1234" }
              """.stripMargin)

            ConductR.unloadBundleTask("my-bundle", callTimeout)

            conductrController.expectMsg(UnloadBundle(apiVersion, "my-bundle"))
          }

          "handle unexpected reply" in {
            val conductrController = TestProbe()
            implicit val conductrControllerRef = conductrController.ref
            replyWith(conductrController,
              """
                | {}
              """.stripMargin)

            intercept[RuntimeException] {
              ConductR.unloadBundleTask("my-bundle", callTimeout)
            }

            conductrController.expectMsg(UnloadBundle(apiVersion, "my-bundle"))
          }

          "handle failure" in {
            val conductrController = TestProbe()
            implicit val conductrControllerRef = conductrController.ref
            replyWith(conductrController, Failure(new RuntimeException("test-only")))

            intercept[RuntimeException] {
              ConductR.unloadBundleTask("my-bundle", callTimeout)
            }

            conductrController.expectMsg(UnloadBundle(apiVersion, "my-bundle"))
          }
        }

        "events" should {
          "handle successful scenario" in {
            val loggerMonitor = TestProbe()
            implicit val logger = new Logger {
              def log(level: Level.Value, message: => String): Unit = fail("log should not be called")
              def success(message: => String): Unit = fail("foo")
              def trace(t: => Throwable): Unit = fail("Trace should not be called")
            }

            val conductrController = TestProbe()
            implicit val conductrControllerRef = conductrController.ref
            val source = Source(100 millis, 2 seconds, Seq(Event("2015-01-01T00:00:00Z", "event", "description")))
            replyWith(conductrController, DataSource(source))

            ConductR.events("my-bundle", Some(5))(system, conductrController.ref, apiVersion)

            conductrController.expectMsg(GetBundleEvents(apiVersion, "my-bundle", 5))
          }
        }

        "logs" should {
          "handle successful scenario" in {
            val conductrController = TestProbe()
            implicit val conductrControllerRef = conductrController.ref
            val source = Source(100 millis, 2 seconds, Seq(Log("2015-01-01T00:00:00Z", "host", "log message")))
            replyWith(conductrController, DataSource(source))

            ConductR.logs("my-bundle", Some(5))

            conductrController.expectMsg(GetBundleLogs(apiVersion, "my-bundle", 5))
          }
        }
      }
    }

  "API version 1" when {
    implicit val apiVersion = ConductRController.ApiVersion.V1

    "loadBundle" should {
      "handle successful scenario" in {
        val conductrController = TestProbe()
        implicit val conductrControllerRef = conductrController.ref
        replyWith(conductrController,
          """
            | { "bundleId": "test-1234" }
          """.stripMargin)

        ConductR.loadBundle(testBundle.toURI, None, callTimeout)

        conductrController.expectMsg(LoadBundle(
          apiVersion,
          "tester",
          "1",
          "tester",
          "0.1.0",
          1.0,
          8000000,
          10000000,
          Set("test"),
          Uri(testBundle.toString),
          None))
      }

      "handle successful scenario when configuration is specified" in {
        val conductrController = TestProbe()
        implicit val conductrControllerRef = conductrController.ref
        replyWith(conductrController,
          """
            | { "bundleId": "test-1234" }
          """.stripMargin)

        ConductR.loadBundle(testBundle.toURI, Some(testConfigWithBundleConf.toURI), callTimeout)

        conductrController.expectMsg(LoadBundle(
          apiVersion,
          "tester",
          "1",
          "tester",
          "0.1.0",
          2.0,
          16000000,
          20000000,
          Set("other-roles"),
          Uri(testBundle.toString),
          Some(Uri(testConfigWithBundleConf.toString))))
      }

      "handle unexpected reply" in {
        val conductrController = TestProbe()
        implicit val conductrControllerRef = conductrController.ref
        replyWith(conductrController, "{}")

        intercept[RuntimeException] {
          ConductR.loadBundle(testBundle.toURI, Some(testConfigWithBundleConf.toURI), callTimeout)
        }

        conductrController.expectMsg(LoadBundle(
          apiVersion,
          "tester",
          "1",
          "tester",
          "0.1.0",
          2.0,
          16000000,
          20000000,
          Set("other-roles"),
          Uri(testBundle.toString),
          Some(Uri(testConfigWithBundleConf.toString))))
      }

      "handle failure" in {
        val conductrController = TestProbe()
        implicit val conductrControllerRef = conductrController.ref
        replyWith(conductrController, Failure(new RuntimeException("test only")))

        intercept[RuntimeException] {
          ConductR.loadBundle(testBundle.toURI, Some(testConfigWithBundleConf.toURI), callTimeout)
        }

        conductrController.expectMsg(LoadBundle(
          apiVersion,
          "tester",
          "1",
          "tester",
          "0.1.0",
          2.0,
          16000000,
          20000000,
          Set("other-roles"),
          Uri(testBundle.toString),
          Some(Uri(testConfigWithBundleConf.toString))))
      }
    }
  }

  "API version 2" when {
    implicit val apiVersion = ConductRController.ApiVersion.V2

    "loadBundle" should {
      "handle successful scenario" in {
        val conductrController = TestProbe()
        implicit val conductrControllerRef = conductrController.ref
        replyWith(conductrController,
          """
            | { "bundleId": "test-1234" }
          """.stripMargin)

        ConductR.loadBundle(testBundle.toURI, Some(testConfigWithBundleConf.toURI), callTimeout)

        conductrController.expectMsg(V2.LoadBundle(
          Uri(testBundle.toString),
          Some(Uri(testConfigWithBundleConf.toString)))
        )
      }

      "handle unexpected reply" in {
        val conductrController = TestProbe()
        implicit val conductrControllerRef = conductrController.ref
        replyWith(conductrController, "{}")

        intercept[RuntimeException] {
          ConductR.loadBundle(testBundle.toURI, Some(testConfigWithBundleConf.toURI), callTimeout)
        }

        conductrController.expectMsg(V2.LoadBundle(
          Uri(testBundle.toString),
          Some(Uri(testConfigWithBundleConf.toString)))
        )
      }

      "handle failure" in {
        val conductrController = TestProbe()
        implicit val conductrControllerRef = conductrController.ref
        replyWith(conductrController, Failure(new RuntimeException("test only")))

        intercept[RuntimeException] {
          ConductR.loadBundle(testBundle.toURI, Some(testConfigWithBundleConf.toURI), callTimeout)
        }

        conductrController.expectMsg(V2.LoadBundle(
          Uri(testBundle.toString),
          Some(Uri(testConfigWithBundleConf.toString)))
        )
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
        case (input, fullUrl) => ConductR.prepareConductrUrl(input).toString shouldBe fullUrl
      }
    }
  }

  private implicit val system =
    ActorSystem()

  override protected def afterAll(): Unit = {
    system.shutdown()
    system.awaitTermination()
  }

  private def replyWith[T](testProbe: TestProbe, message: T): Unit =
    testProbe.setAutoPilot(new AutoPilot {
      override def run(sender: ActorRef, msg: Any): AutoPilot = {
        sender ! message
        TestActor.NoAutoPilot
      }
    })

}
