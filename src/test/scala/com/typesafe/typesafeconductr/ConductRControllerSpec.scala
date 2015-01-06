/*
 * Copyright © 2014 Typesafe, Inc. All rights reserved.
 */

package com.typesafe.typesafeconductr

import akka.actor.{ ActorRef, ActorSystem }
import akka.http.Http.OutgoingConnection
import akka.http.model.{ HttpMethods, HttpRequest, HttpResponse, StatusCodes, Uri }
import akka.stream.FlowMaterializer
import akka.stream.scaladsl.{ Sink, Source }
import akka.testkit.{ TestActorRef, TestProbe }
import com.typesafe.typesafeconductr.ConductRController.{ LoadBundle, StartBundle, StopBundle, UnloadBundle }
import java.net.InetSocketAddress
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpec }
import scala.concurrent.duration.DurationInt
import scala.concurrent.Future

class ConductRControllerSpec extends WordSpec with Matchers with BeforeAndAfterAll {

  import com.typesafe.typesafeconductr.TestBundle._

  // FIXME: Test required for GetBundleStream

  "The controller" should {
    "send a load bundle request and reply with some id" in withController { controller =>
      val testProbe = TestProbe()
      testProbe.send(controller, LoadBundle(Uri(testBundle.toString), None, 1.0, 1024, 1024, Set("web-server")))
      testProbe expectMsg "hello"
    }

    "send a start bundle request and reply with some id" in withController { controller =>
      val testProbe = TestProbe()
      testProbe.send(controller, StartBundle("hello", 2))
      testProbe expectMsg "hello back"
    }

    "send a stop bundle request and reply with some id" in withController { controller =>
      val testProbe = TestProbe()
      testProbe.send(controller, StopBundle("hello"))
      testProbe expectMsg "hello gone"
    }

    "send an unload bundle request and reply with some id" in withController { controller =>
      val testProbe = TestProbe()
      testProbe.send(controller, UnloadBundle("hello"))
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

    val LocalHost = "somehost"
    val LocalPort = 19005

    val controller = TestActorRef[ConductRController](new ConductRController(Uri(WatchdogAddress), 1 minute) {
      override def request(request: HttpRequest, connection: OutgoingConnection) = request match {
        case request if request.method == HttpMethods.POST && request.uri == Uri("/bundles") =>
          Future.successful(HttpResponse(entity = "hello"))
        case context if request.method == HttpMethods.PUT && request.uri == Uri("/bundles/hello?scale=2") =>
          Future.successful(HttpResponse(entity = "hello back"))
        case context if request.method == HttpMethods.PUT && request.uri == Uri("/bundles/hello?scale=0") =>
          Future.successful(HttpResponse(entity = "hello gone"))
        case context if request.method == HttpMethods.DELETE && request.uri == Uri("/bundles/hello") =>
          Future.successful(HttpResponse(entity = "hello really gone"))
        case _ =>
          Future.successful(HttpResponse(StatusCodes.BadRequest))
      }
    })

    block(controller)
  }
}
