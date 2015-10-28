/*
 * Copyright © 2014 Typesafe, Inc. All rights reserved.
 */

package com.typesafe.conductr.client

import akka.actor.Status.Failure
import akka.actor.{ ActorRef, ActorSystem }
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ HttpMethods, HttpRequest, HttpResponse, StatusCodes, Uri }
import akka.stream.scaladsl.Flow
import akka.testkit.{ TestActorRef, TestProbe }
import org.scalatest.{ BeforeAndAfterAll, Matchers, PrivateMethodTester, WordSpec }

import scala.concurrent.Future
import scala.concurrent.duration._

class ConductRControllerSpec extends WordSpec with Matchers with BeforeAndAfterAll with PrivateMethodTester {

  import com.typesafe.conductr.TestBundle._

  // FIXME: Test required for GetBundleStream

  import ConductRController._

  "The controller" should {
    "send a load bundle request and reply with some id" in withController { controller =>
      val testProbe = TestProbe()
      testProbe.send(
        controller,
        ConductRController.LoadBundle(ApiVersion.V10, "some-name", "1.0", "some-system", "1.0", 1.0, 1024, 1024, Set("web-server"), Uri(testBundle.toURI.toString), None)
      )
      testProbe expectMsg "hello"
    }

    "send a load bundle request and reply with some id for the 1.1 protocol" in withController { controller =>
      val testProbe = TestProbe()
      testProbe.send(
        controller,
        ConductRController.LoadBundle(ApiVersion.V11, "some-name", "2.0", "some-system", "3.0", 1.0, 1024, 1024, Set("web-server"), Uri(testBundle.toURI.toString), None)
      )
      testProbe expectMsg "hello again"
    }

    "send a start bundle request and reply with some id" in withController { controller =>
      val testProbe = TestProbe()
      testProbe.send(controller, ConductRController.RunBundle(ApiVersion.V10, "hello", 2, None))
      testProbe expectMsg "hello back"
    }

    "send a start bundle request with affinity option and reply with some id" in withController { controller =>
      val testProbe = TestProbe()
      testProbe.send(controller, ConductRController.RunBundle(ApiVersion.V11, "hello", 2, Some("other-bundle")))
      testProbe expectMsg "hello with affinity"
    }

    "reject start a bundle given v1.0 of the API and affinity option supplied" in withController { controller =>
      val testProbe = TestProbe()
      testProbe.send(controller, ConductRController.RunBundle(ApiVersion.V10, "hello", 2, Some("other-bundle")))
      testProbe.expectMsgPF() {
        case Failure(e) if e.getMessage == "Affinity feature is only available for v1.1 onwards of ConductR" =>
          e
      }
    }

    "send a stop bundle request and reply with some id" in withController { controller =>
      val testProbe = TestProbe()
      testProbe.send(controller, ConductRController.StopBundle(ApiVersion.V10, "hello"))
      testProbe expectMsg "hello gone"
    }

    "send an unload bundle request and reply with some id" in withController { controller =>
      val testProbe = TestProbe()
      testProbe.send(controller, ConductRController.UnloadBundle(ApiVersion.V10, "hello"))
      testProbe expectMsg "hello really gone"
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

    val controller = TestActorRef[ConductRController](new ConductRController(Uri(WatchdogAddress), Uri(WatchdogAddress), 1.minute) {
      override def request(request: HttpRequest, connection: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]]) =
        if (request.method == HttpMethods.POST && request.uri == Uri("/bundles"))
          Future.successful(HttpResponse(entity = "hello"))
        else if (request.method == HttpMethods.POST && request.uri == Uri("/v1.1/bundles"))
          Future.successful(HttpResponse(entity = "hello again"))
        else if (request.method == HttpMethods.PUT && request.uri == Uri("/bundles/hello?scale=2"))
          Future.successful(HttpResponse(entity = "hello back"))
        else if (request.method == HttpMethods.PUT && request.uri == Uri("/v1.1/bundles/hello?scale=2&affinity=other-bundle"))
          Future.successful(HttpResponse(entity = "hello with affinity"))
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
