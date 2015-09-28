/*
 * Copyright © 2014 Typesafe, Inc. All rights reserved.
 */

package com.typesafe.conductr.sbt
package console

import akka.actor.{ ActorRef, ActorSystem }
import akka.pattern.{ ask, gracefulStop, pipe }
import com.typesafe.conductr.client.ConductRController
import jline.console.ConsoleReader
import org.fusesource.jansi.Ansi
import scala.concurrent.{ Await, blocking }
import scala.util.Failure
import com.typesafe.conductr.sbt.console.Column._

object Console {

  import scala.concurrent.duration._
  val timeout = 5.seconds

  def bundleInfo(apiVersion: ConductRController.ApiVersion.Value, refresh: Boolean): ActorRef => ActorSystem => Unit = { implicit conductr =>
    { implicit system =>

      import system.dispatcher

      val screen = system.actorOf(Screen.props(refresh, (bundles: Seq[ConductRController.BundleInfo]) => {
        val columns = Vector(
          Id(bundles),
          Name(bundles),
          Replicated(bundles),
          Starting(bundles),
          Running(bundles)
        )
        val notes = bundles.find(_.hasError).map(_ => "@|red There are errors: use `conduct events` or `conduct logs` for further information|@")
        Screen.Layout(columns, notes.toVector)
      }), "screen")
      conductr
        .ask(ConductRController.GetBundleInfoStream(apiVersion))(timeout)
        .mapTo[ConductRController.DataSource[Seq[ConductRController.BundleInfo]]]
        .pipeTo(screen)

      cleanUp(screen, refresh)
    }
  }

  def bundleEvents(apiVersion: ConductRController.ApiVersion.Value, bundleId: String, lines: Int, refresh: Boolean): ActorRef => ActorSystem => Unit = { implicit conductr =>
    { implicit system =>

      import system.dispatcher

      val screen = system.actorOf(Screen.props(refresh, (events: Seq[ConductRController.Event]) => {
        val columns = Vector(
          EventTime(events),
          Event(events),
          Description(events)
        )
        Screen.Layout(columns, Vector.empty)
      }), "screen")
      conductr
        .ask(ConductRController.GetBundleEvents(apiVersion, bundleId, lines))(timeout)
        .mapTo[ConductRController.DataSource[Seq[ConductRController.Event]]]
        .pipeTo(screen)

      cleanUp(screen, refresh)
    }
  }

  def bundleLogs(apiVersion: ConductRController.ApiVersion.Value, bundleId: String, lines: Int, refresh: Boolean): ActorRef => ActorSystem => Unit = { implicit conductr =>
    { implicit system =>

      import system.dispatcher

      val screen = system.actorOf(Screen.props(refresh, (logs: Seq[ConductRController.Log]) => {
        val columns = Vector(
          LogTime(logs),
          Host(logs),
          Log(logs)
        )
        Screen.Layout(columns, Vector.empty)
      }), "screen")
      conductr
        .ask(ConductRController.GetBundleLogs(apiVersion, bundleId, lines))(timeout)
        .mapTo[ConductRController.DataSource[Seq[ConductRController.Log]]]
        .pipeTo(screen)

      cleanUp(screen, refresh)
    }
  }

  private def cleanUp(screen: ActorRef, refresh: Boolean)(implicit system: ActorSystem) = {

    import system.dispatcher

    print(Ansi.ansi().saveCursorPosition())

    if (refresh) {
      val con = new ConsoleReader()
      blocking {
        con.readCharacter('q')
      }
      system.stop(screen)
    } else {
      // There is no way to wait for actor termination from a non-actor,
      // other than using gracefulStop with a message that does nothing.
      case object WaitingForYou
      val f = gracefulStop(screen, timeout, WaitingForYou).andThen {
        case Failure(cause) => system.stop(screen)
      }
      Await.ready(f, timeout)
    }

    print(Ansi.ansi().restorCursorPosition())
  }

  object Implicits {
    implicit class RichLong(val underlying: Long) extends AnyVal {
      def toSize: String = {
        val orders = Map(1000000000 -> "G", 1000000 -> "M", 1000 -> "k")
        orders
          .collectFirst {
            case (value, order) if math.abs(underlying) > value => (underlying / value).toString + " " + order
          }.getOrElse(underlying.toString) + "B"
      }
    }
  }
}
