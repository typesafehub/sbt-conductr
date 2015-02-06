/*
 * Copyright © 2014 Typesafe, Inc. All rights reserved.
 */

package com.typesafe.conductr.client

import akka.actor.{ ActorRef, ActorSystem }
import akka.http.Http.OutgoingConnection
import akka.http.model.{ HttpMethods, HttpRequest, HttpResponse, StatusCodes, Uri }
import akka.testkit.{ TestActorRef, TestProbe }
import org.scalatest.{ BeforeAndAfterAll, Matchers, PrivateMethodTester, WordSpec }
import scala.concurrent.duration.DurationInt
import scala.concurrent.Future

class ConductRControllerSpec extends WordSpec with Matchers with BeforeAndAfterAll with PrivateMethodTester {

  import com.typesafe.conductr.TestBundle._

  // FIXME: Test required for GetBundleStream

  "The controller" should {
    "send a load bundle request and reply with some id" in withController { controller =>
      val testProbe = TestProbe()
      testProbe.send(
        controller,
        ConductRController.LoadBundle(Uri(BundleFile), None, "some-system", 1.0, 1024, 1024, Set("web-server"))
      )
      testProbe expectMsg "hello"
    }

    "send a start bundle request and reply with some id" in withController { controller =>
      val testProbe = TestProbe()
      testProbe.send(controller, ConductRController.StartBundle("hello", 2))
      testProbe expectMsg "hello back"
    }

    "send a stop bundle request and reply with some id" in withController { controller =>
      val testProbe = TestProbe()
      testProbe.send(controller, ConductRController.StopBundle("hello"))
      testProbe expectMsg "hello gone"
    }

    "send an unload bundle request and reply with some id" in withController { controller =>
      val testProbe = TestProbe()
      testProbe.send(controller, ConductRController.UnloadBundle("hello"))
      testProbe expectMsg "hello really gone"
    }
  }

  "Bundle URI" should {
    "be converted to bundle name" in {
      val pathsToNames = Map(
        "path/to/bundle.zip" -> "bundle",
        "path/to/bundle-5ca1ab1e.zip" -> "bundle",
        "path/to/bundle-1.0.0-M2.zip" -> "bundle-1.0.0-M2"
      )

      val toBundleName = PrivateMethod[String]('toBundleName)
      pathsToNames foreach {
        case (path, name) => ConductRController.invokePrivate(toBundleName(Uri(s"file://$path"))) shouldBe name
      }
    }
  }

  private implicit val system =
    ActorSystem()

  override protected def afterAll(): Unit = {
    system.shutdown()
    system.awaitTermination()
  }

  private def withController[T](block: ActorRef => T): T = {
    val WatchdogHost = "localhost"
    val WatchdogPort = 9005
    val WatchdogAddress = s"http://$WatchdogHost:$WatchdogPort"

    val controller = TestActorRef[ConductRController](new ConductRController(Uri(WatchdogAddress), 1 minute) {
      override def request(request: HttpRequest, connection: OutgoingConnection) =
        if (request.method == HttpMethods.POST && request.uri == Uri("/bundles"))
          Future.successful(HttpResponse(entity = "hello"))
        else if (request.method == HttpMethods.PUT && request.uri == Uri("/bundles/hello?scale=2"))
          Future.successful(HttpResponse(entity = "hello back"))
        else if (request.method == HttpMethods.PUT && request.uri == Uri("/bundles/hello?scale=0"))
          Future.successful(HttpResponse(entity = "hello gone"))
        else if (request.method == HttpMethods.DELETE && request.uri == Uri("/bundles/hello"))
          Future.successful(HttpResponse(entity = "hello really gone"))
        else
          Future.successful(HttpResponse(StatusCodes.BadRequest))
    })

    block(controller)
  }
}
