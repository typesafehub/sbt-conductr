/*
 * Copyright © 2014 Typesafe, Inc. All rights reserved.
 */

package com.typesafe.conductr.sbt

import java.util.{ UUID }
import akka.actor.{ ActorSystem }
import com.typesafe.conductr.clientlib.akka.ControlClient
import org.scalatest.mock.MockitoSugar
import org.mockito.Mockito._
import akka.util.Timeout
import com.typesafe.conductr.akka.ConnectionContext
import com.typesafe.conductr.clientlib.scala.models._
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpec }
import sbt.{ Level, Logger }
import scala.concurrent.Future
import scala.concurrent.duration._

class ConductRClientSpec extends WordSpec with Matchers with BeforeAndAfterAll with MockitoSugar {
  import com.typesafe.conductr.TestData._

  val callTimeout = Timeout(5 seconds)

  private implicit val system = ActorSystem()

  private implicit val cc = ConnectionContext()

  implicit val printlnLogger = new Logger {
    def log(level: Level.Value, message: => String): Unit = println(message)
    def success(message: => String): Unit = println(message)
    def trace(t: => Throwable): Unit = t.printStackTrace()
  }

  "API version 2" when {

    "loadBundle" should {
      "handle successful scenario" in {
        val expectedBundle = "my-bundle"

        val mockedControlClient = mock[ControlClient]
        when(mockedControlClient.loadBundle(testBundle.toURI, Some(testConfigWithBundleConf.toURI)))
          .thenReturn(Future.successful(BundleRequestSuccess(UUID.randomUUID(), expectedBundle)))

        val conductrClient = new ConductRClient(mockedControlClient)
        conductrClient.loadBundle(testBundle.toURI, Some(testConfigWithBundleConf.toURI), callTimeout) shouldBe expectedBundle
      }

      "handle unexpected reply" in {
        val mockedControlClient = mock[ControlClient]
        when(mockedControlClient.loadBundle(testBundle.toURI))
          .thenReturn(Future.successful(BundleRequestFailure(400, "error")))

        val conductrClient = new ConductRClient(mockedControlClient)

        intercept[RuntimeException] {
          conductrClient.loadBundle(testBundle.toURI, Some(testConfigWithBundleConf.toURI), callTimeout)
        }
      }

      "handle failure" in {
        val mockedControlClient = mock[ControlClient]
        when(mockedControlClient.loadBundle(testBundle.toURI))
          .thenReturn(Future.failed(new RuntimeException("test only")))

        val conductrClient = new ConductRClient(mockedControlClient)

        intercept[RuntimeException] {
          conductrClient.loadBundle(testBundle.toURI, Some(testConfigWithBundleConf.toURI), callTimeout)
        }
      }
    }

    "runBundle" should {
      "handle successful scenario" in {
        val expectedUUID = "c56a4180-65aa-42ec-a945-5fd21dec0538"

        val mockedControlClient = mock[ControlClient]
        when(mockedControlClient.runBundle("my-bundle", Some(3), Some("other-bundle")))
          .thenReturn(Future.successful(BundleRequestSuccess(UUID.fromString(expectedUUID), "my-bundle")))

        val conductrClient = new ConductRClient(mockedControlClient)
        conductrClient.runBundle("my-bundle", Some(3), Some("other-bundle"), callTimeout) shouldBe expectedUUID
      }

      "handle unexpected reply" in {
        val mockedControlClient = mock[ControlClient]
        when(mockedControlClient.runBundle("my-bundle", Some(3), Some("other-bundle")))
          .thenReturn(Future.successful(BundleRequestFailure(400, "error")))

        val conductrClient = new ConductRClient(mockedControlClient)

        intercept[RuntimeException] {
          conductrClient.runBundle("my-bundle", Some(3), Some("other-bundle"), callTimeout)
        }
      }

      "handle failure" in {
        val mockedControlClient = mock[ControlClient]
        when(mockedControlClient.runBundle("my-bundle", Some(3), Some("other-bundle")))
          .thenReturn(Future.failed(new RuntimeException("test only")))

        val conductrClient = new ConductRClient(mockedControlClient)

        intercept[RuntimeException] {
          conductrClient.runBundle("my-bundle", Some(3), Some("other-bundle"), callTimeout)
        }
      }
    }

    "stopBundle" should {
      "handle successful scenario" in {
        val expectedUUID = "c56a4180-65aa-42ec-a945-5fd21dec0538"

        val mockedControlClient = mock[ControlClient]
        when(mockedControlClient.stopBundle("my-bundle"))
          .thenReturn(Future.successful(BundleRequestSuccess(UUID.fromString(expectedUUID), "my-bundle")))

        val conductrClient = new ConductRClient(mockedControlClient)
        conductrClient.stopBundle("my-bundle", callTimeout) shouldBe expectedUUID
      }

      "handle unexpected reply" in {
        val mockedControlClient = mock[ControlClient]
        when(mockedControlClient.stopBundle("my-bundle"))
          .thenReturn(Future.successful(BundleRequestFailure(400, "error")))

        val conductrClient = new ConductRClient(mockedControlClient)

        intercept[RuntimeException] {
          conductrClient.stopBundle("my-bundle", callTimeout)
        }
      }

      "handle failure" in {
        val mockedControlClient = mock[ControlClient]
        when(mockedControlClient.stopBundle("my-bundle"))
          .thenReturn(Future.failed(new RuntimeException("test only")))

        val conductrClient = new ConductRClient(mockedControlClient)

        intercept[RuntimeException] {
          conductrClient.stopBundle("my-bundle", callTimeout)
        }
      }
    }

    "unloadBundle" should {
      "handle successful scenario" in {
        val expectedUUID = "c56a4180-65aa-42ec-a945-5fd21dec0538"

        val mockedControlClient = mock[ControlClient]
        when(mockedControlClient.unloadBundle("my-bundle"))
          .thenReturn(Future.successful(BundleRequestSuccess(UUID.fromString(expectedUUID), "my-bundle")))

        val conductrClient = new ConductRClient(mockedControlClient)
        conductrClient.unloadBundle("my-bundle", callTimeout) shouldBe expectedUUID
      }

      "handle unexpected reply" in {
        val mockedControlClient = mock[ControlClient]
        when(mockedControlClient.unloadBundle("my-bundle"))
          .thenReturn(Future.successful(BundleRequestFailure(400, "error")))

        val conductrClient = new ConductRClient(mockedControlClient)

        intercept[RuntimeException] {
          conductrClient.unloadBundle("my-bundle", callTimeout)
        }
      }

      "handle failure" in {
        val mockedControlClient = mock[ControlClient]
        when(mockedControlClient.unloadBundle("my-bundle"))
          .thenReturn(Future.failed(new RuntimeException("test only")))

        val conductrClient = new ConductRClient(mockedControlClient)

        intercept[RuntimeException] {
          conductrClient.unloadBundle("my-bundle", callTimeout)
        }
      }
    }

    "events" should {
      "handle successful scenario" in {
        implicit val logger = new Logger {
          def log(level: Level.Value, message: => String): Unit = fail("log should not be called")
          def success(message: => String): Unit = fail("foo")
          def trace(t: => Throwable): Unit = fail("Trace should not be called")
        }

        val mockedControlClient = mock[ControlClient]
        when(mockedControlClient.getBundleEvents("my-bundle", Some(5)))
          .thenReturn(Future.successful(BundleEventsSuccess(BundleEvents)))

        val conductrClient = new ConductRClient(mockedControlClient)(system, cc, logger)
        conductrClient.events("my-bundle", Some(5), callTimeout) shouldBe ()
      }
    }

    "logs" should {
      "handle successful scenario" in {
        implicit val logger = new Logger {
          def log(level: Level.Value, message: => String): Unit = fail("log should not be called")
          def success(message: => String): Unit = fail("foo")
          def trace(t: => Throwable): Unit = fail("Trace should not be called")
        }

        val mockedControlClient = mock[ControlClient]
        when(mockedControlClient.getBundleLogs("my-bundle", Some(5)))
          .thenReturn(Future.successful(BundleLogsSuccess(BundleLogs)))

        val conductrClient = new ConductRClient(mockedControlClient)(system, cc, logger)
        conductrClient.logs("my-bundle", Some(5), callTimeout) shouldBe ()
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
        case (input, fullUrl) => ConductRClient.prepareConductrUrl(input).toString shouldBe fullUrl
      }
    }
  }

  override protected def afterAll(): Unit = {
    system.shutdown()
    system.awaitTermination()
  }
}
