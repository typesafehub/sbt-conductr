/*
 * Copyright © 2014 Typesafe, Inc. All rights reserved.
 */

package com.typesafe.conductr.client

import java.io.File

import akka.actor.Status.Failure
import akka.actor.{ ActorRef, ActorSystem }
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{ Sink, Flow }
import akka.testkit.{ TestActorRef, TestProbe }
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ BeforeAndAfterAll, Matchers, PrivateMethodTester, WordSpec }

import scala.concurrent.Future
import scala.concurrent.duration._

class ConductRControllerSpec extends WordSpec with Matchers with BeforeAndAfterAll with PrivateMethodTester with ScalaFutures {

  implicit val futureTimeout = PatienceConfig(timeout = 3 seconds)

  import com.typesafe.conductr.TestBundle._

  // FIXME: Test required for GetBundleStream

  import ConductRController._

  "The controller" when {
    Map(
      ApiVersion.V1 -> "",
      ApiVersion.V2 -> "/v2"
    ).foreach {
        case (apiVersion, urlPrefix) =>
          s"api version $apiVersion is specified" should {
            "send a start bundle request and reply with some id" in withController { (controller, requestMonitor) =>
              val testProbe = TestProbe()
              testProbe.send(controller, ConductRController.RunBundle(apiVersion, "hello", 2, None))
              testProbe expectMsg "OK"

              requestMonitor.expectMsg(HttpRequest(method = HttpMethods.PUT, uri = s"$urlPrefix/bundles/hello?scale=2"))
            }

            "send a stop bundle request and reply with some id" in withController { (controller, requestMonitor) =>
              val testProbe = TestProbe()
              testProbe.send(controller, ConductRController.StopBundle(apiVersion, "hello"))
              testProbe expectMsg "OK"

              requestMonitor.expectMsg(HttpRequest(method = HttpMethods.PUT, uri = s"$urlPrefix/bundles/hello?scale=0"))
            }

            "send an unload bundle request and reply with some id" in withController { (controller, requestMonitor) =>
              val testProbe = TestProbe()
              testProbe.send(controller, ConductRController.UnloadBundle(apiVersion, "hello"))
              testProbe expectMsg "OK"

              requestMonitor.expectMsg(HttpRequest(method = HttpMethods.DELETE, uri = s"$urlPrefix/bundles/hello"))
            }

            "send an events request and reply with data source" in withController { (controller, requestMonitor) =>
              implicit val materializer = ActorMaterializer()

              val testProbe = TestProbe()
              testProbe.send(controller, ConductRController.GetBundleEvents(apiVersion, "my-bundle", 20))

              val dataSource = testProbe.expectMsgType[DataSource[_]]
              dataSource.source.runWith(Sink.head).futureValue shouldBe Seq.empty

              requestMonitor.expectMsg(HttpRequest(method = HttpMethods.GET, uri = s"$urlPrefix/bundles/my-bundle/events?count=20"))
            }

            "send an logs request and reply with data source" in withController { (controller, requestMonitor) =>
              implicit val materializer = ActorMaterializer()

              val testProbe = TestProbe()
              testProbe.send(controller, ConductRController.GetBundleLogs(apiVersion, "my-bundle", 20))

              val dataSource = testProbe.expectMsgType[DataSource[_]]
              dataSource.source.runWith(Sink.head).futureValue shouldBe Seq.empty

              requestMonitor.expectMsg(HttpRequest(method = HttpMethods.GET, uri = s"$urlPrefix/bundles/my-bundle/logs?count=20"))
            }
          }
      }
  }

  "The controller - version specific behaviour" when {
    "api version 1 is specified" should {
      "send a load bundle request and reply with some id" in withController { (controller, requestMonitor) =>
        implicit val materializer = ActorMaterializer()
        import system.dispatcher

        val testProbe = TestProbe()
        testProbe.send(
          controller,
          ConductRController.LoadBundle(ApiVersion.V1, "some-name", "1.0", "some-system", "1.0", 1.0, 1024, 1024, Set("web-server"), Uri(testBundle.toURI.toString), None)
        )
        testProbe expectMsg "OK"

        val request = requestMonitor.expectMsgType[HttpRequest]
        val multipartForm = Unmarshal(request.entity).to[Multipart.FormData].futureValue
        val bodyParts = multipartForm.parts.runFold(Seq.empty[Multipart.FormData.BodyPart]) { _ :+ _ }.futureValue
        bodyParts.size shouldBe 7

        // The first six parts should be bundle conf
        val postedBundleConf = bodyParts.take(6).map { part => part.name -> Unmarshal(part.entity).to[String].futureValue }
        postedBundleConf shouldBe Seq(
          "system" -> "some-system",
          "nrOfCpus" -> "1.0",
          "memory" -> "1024",
          "diskSpace" -> "1024",
          "roles" -> "web-server",
          "bundleName" -> "some-name"
        )

        // The last part should be the file
        val postedBundleFile = bodyParts.last
        postedBundleFile.name shouldBe "bundle"
        postedBundleFile.filename shouldBe Some(new File(testBundle.getFile).getName)
      }

      "send a load bundle request and reply with some id when configuration is specified" in withController { (controller, requestMonitor) =>
        implicit val materializer = ActorMaterializer()
        import system.dispatcher

        val testProbe = TestProbe()
        testProbe.send(
          controller,
          ConductRController.LoadBundle(ApiVersion.V1, "some-name", "1.0", "some-system", "1.0", 1.0, 1024, 1024, Set("web-server"), Uri(testBundle.toURI.toString), Some(Uri(testConfigWithoutBundleConf.toURI.toString)))
        )
        testProbe expectMsg "OK"

        val request = requestMonitor.expectMsgType[HttpRequest]
        val multipartForm = Unmarshal(request.entity).to[Multipart.FormData].futureValue
        val bodyParts = multipartForm.parts.runFold(Seq.empty[Multipart.FormData.BodyPart]) { _ :+ _ }.futureValue
        bodyParts.size shouldBe 8

        // The first six parts should be bundle conf
        val postedBundleConf = bodyParts.take(6).map { part => part.name -> Unmarshal(part.entity).to[String].futureValue }
        postedBundleConf shouldBe Seq(
          "system" -> "some-system",
          "nrOfCpus" -> "1.0",
          "memory" -> "1024",
          "diskSpace" -> "1024",
          "roles" -> "web-server",
          "bundleName" -> "some-name"
        )

        val files = bodyParts.takeRight(2)

        // The first part should be the bundle
        val postedBundleFile = files.head
        postedBundleFile.name shouldBe "bundle"
        postedBundleFile.filename shouldBe Some(new File(testBundle.getFile).getName)

        // The last part should be the config
        val postedConfigurationFile = files.last
        postedConfigurationFile.name shouldBe "configuration"
        postedConfigurationFile.filename shouldBe Some(new File(testConfigWithoutBundleConf.getFile).getName)
      }

      "reject start a bundle given v1.0 of the API and affinity option supplied" in withController { (controller, requestMonitor) =>
        val testProbe = TestProbe()
        testProbe.send(controller, ConductRController.RunBundle(ApiVersion.V1, "hello", 2, Some("other-bundle")))
        testProbe.expectMsgPF() {
          case Failure(e) if e.getMessage == "Affinity feature is only available for v1.1 onwards of ConductR" =>
            e
        }

        requestMonitor.expectNoMsg()
      }
    }

    "api version 2 is specified" should {
      "send a load bundle request and reply with some id" in withController { (controller, requestMonitor) =>
        implicit val materializer = ActorMaterializer()
        import system.dispatcher

        val testProbe = TestProbe()
        testProbe.send(
          controller,
          ConductRController.V2.LoadBundle(Uri(testBundle.toURI.toString), None)
        )
        testProbe expectMsg "OK"

        val request = requestMonitor.expectMsgType[HttpRequest]
        val multipartForm = Unmarshal(request.entity).to[Multipart.FormData].futureValue
        val bodyParts = multipartForm.parts.runFold(Seq.empty[Multipart.FormData.BodyPart]) { _ :+ _ }.futureValue
        val postedFiles = bodyParts.map { part => part.name -> part.filename }
        postedFiles shouldBe Seq(
          "bundleConf" -> Some("bundle.conf"),
          "bundle" -> Some(new File(testBundle.getFile).getName)
        )
      }

      "send a load bundle request and reply with some id when bundle.conf is specified in the configuration" in withController { (controller, requestMonitor) =>
        implicit val materializer = ActorMaterializer()
        import system.dispatcher

        val testProbe = TestProbe()
        testProbe.send(
          controller,
          ConductRController.V2.LoadBundle(Uri(testBundle.toURI.toString), Some(Uri(testConfigWithBundleConf.toURI.toString)))
        )
        testProbe expectMsg "OK"

        val request = requestMonitor.expectMsgType[HttpRequest]
        val multipartForm = Unmarshal(request.entity).to[Multipart.FormData].futureValue
        val bodyParts = multipartForm.parts.runFold(Seq.empty[Multipart.FormData.BodyPart]) { _ :+ _ }.futureValue
        val postedFiles = bodyParts.map { part => part.name -> part.filename }
        postedFiles shouldBe Seq(
          "bundleConf" -> Some("bundle.conf"),
          "bundleConfOverlay" -> Some("bundle.conf"),
          "bundle" -> Some(new File(testBundle.getFile).getName),
          "configuration" -> Some(new File(testConfigWithBundleConf.getFile).getName)
        )
      }

      "send a load bundle request and reply with some id when bundle.conf is not specified in the configuration" in withController { (controller, requestMonitor) =>
        implicit val materializer = ActorMaterializer()
        import system.dispatcher

        val testProbe = TestProbe()
        testProbe.send(
          controller,
          ConductRController.V2.LoadBundle(Uri(testBundle.toURI.toString), Some(Uri(testConfigWithoutBundleConf.toURI.toString)))
        )
        testProbe expectMsg "OK"

        val request = requestMonitor.expectMsgType[HttpRequest]
        val multipartForm = Unmarshal(request.entity).to[Multipart.FormData].futureValue
        val bodyParts = multipartForm.parts.runFold(Seq.empty[Multipart.FormData.BodyPart]) { _ :+ _ }.futureValue
        val postedFiles = bodyParts.map { part => part.name -> part.filename }
        postedFiles shouldBe Seq(
          "bundleConf" -> Some("bundle.conf"),
          "bundle" -> Some(new File(testBundle.getFile).getName),
          "configuration" -> Some(new File(testConfigWithoutBundleConf.getFile).getName)
        )
      }

      "send a start bundle request with affinity option and reply with some id" in withController { (controller, requestMonitor) =>
        val testProbe = TestProbe()
        testProbe.send(controller, ConductRController.RunBundle(ApiVersion.V2, "hello", 2, Some("other-bundle")))
        testProbe expectMsg "OK"

        requestMonitor.expectMsg(HttpRequest(method = HttpMethods.PUT, uri = s"/v2/bundles/hello?scale=2&affinity=other-bundle"))
      }
    }
  }

  private implicit val system = ActorSystem()

  override protected def afterAll(): Unit = {
    system.shutdown()
    system.awaitTermination()
  }

  private def withController[T](block: (ActorRef, TestProbe) => T): T = {
    val WatchdogHost = "localhost"
    val WatchdogPort = 9005
    val WatchdogAddress = s"http://$WatchdogHost:$WatchdogPort"

    val requestMonitor = TestProbe()

    val controller = TestActorRef[ConductRController](new ConductRController(Uri(WatchdogAddress), Uri(WatchdogAddress), 1.minute) {
      override def request(request: HttpRequest, connection: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]]) = {
        requestMonitor.ref ! request
        val requestUri = request.uri.toString()
        if (requestUri.matches(s"\\/bundles\\/.*\\/events\\?.*") ||
          requestUri.matches(s"\\/v2\\/bundles\\/.*\\/events\\?.*") ||
          requestUri.matches(s"\\/bundles\\/.*\\/logs\\?.*") ||
          requestUri.matches(s"\\/v2\\/bundles\\/.*\\/logs\\?.*"))
          Future.successful(HttpResponse(entity = "[]"))
        else if (requestUri.startsWith("/bundles") || requestUri.startsWith("/v2/bundles"))
          Future.successful(HttpResponse(entity = "OK"))
        else
          Future.successful(HttpResponse(StatusCodes.BadRequest))
      }
    })

    block(controller, requestMonitor)
  }
}
