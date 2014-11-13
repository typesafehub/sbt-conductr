/*
 * Copyright © 2014 Typesafe, Inc. All rights reserved.
 */

package com.typesafe.reactiveruntime

import akka.actor.{ ActorRef, ActorSystem }
import akka.http.Http.{ Connect, OutgoingConnection }
import akka.http.model.{ HttpMethods, HttpRequest, HttpResponse, StatusCodes, Uri }
import akka.stream.FlowMaterializer
import akka.stream.scaladsl.{ Sink, Source }
import akka.testkit.{ TestActor, TestProbe }
import com.typesafe.reactiveruntime.ConductorController.{ LoadBundle, StartBundle, StopBundle, UnloadBundle }
import java.net.InetSocketAddress
import org.scalatest.{ BeforeAndAfterAll, Matchers, WordSpec }
import scala.concurrent.duration.DurationInt

class ConductorControllerSpec extends WordSpec with Matchers with BeforeAndAfterAll {

  import com.typesafe.reactiveruntime.TestBundle._

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

  // private implicit val timeout: Timeout =
  //   TestKitExtension(system).DefaultTimeout

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

    val httpIO = TestProbe()
    httpIO.setAutoPilot(new TestActor.AutoPilot {
      def run(sender: ActorRef, msg: Any): TestActor.AutoPilot =
        msg match {
          case connect: Connect =>
            implicit val mat = FlowMaterializer(connect.materializerSettings)
            val requestSink = Source.subscriber[(HttpRequest, Any)]
            val responses = Sink.publisher[(HttpResponse, Any)]
            val materializedFlow =
              requestSink
                .map {
                  case (request, context) if request.method == HttpMethods.POST && request.uri == Uri("/bundles") =>
                    HttpResponse(entity = "hello") -> context
                  case (request, context) if request.method == HttpMethods.PUT && request.uri == Uri("/bundles/hello?scale=2") =>
                    HttpResponse(entity = "hello back") -> context
                  case (request, context) if request.method == HttpMethods.PUT && request.uri == Uri("/bundles/hello?scale=0") =>
                    HttpResponse(entity = "hello gone") -> context
                  case (request, context) if request.method == HttpMethods.DELETE && request.uri == Uri("/bundles/hello") =>
                    HttpResponse(entity = "hello really gone") -> context
                  case (_, context) =>
                    HttpResponse(StatusCodes.BadRequest) -> context
                }
                .to(responses)
                .run()

            sender ! OutgoingConnection(
              new InetSocketAddress(WatchdogHost, WatchdogPort),
              new InetSocketAddress(LocalHost, LocalPort),
              materializedFlow.get(responses),
              materializedFlow.get(requestSink)
            )
            TestActor.NoAutoPilot
        }
    })

    val controller = system.actorOf(ConductorController.props(Uri(WatchdogAddress), 1 minute, httpIO.testActor))

    block(controller)
  }
}
