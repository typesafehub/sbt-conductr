/*
 * Copyright © 2014 Typesafe, Inc. All rights reserved.
 */

package com.typesafe.conductr.sbt
package console

import akka.actor.{ ActorRef, ActorSystem }
import akka.pattern.{ gracefulStop }
import com.typesafe.conductr.clientlib.scala.models._
import jline.console.ConsoleReader
import org.fusesource.jansi.Ansi
import scala.concurrent.{ Await, blocking }
import scala.util.{ Failure }
import com.typesafe.conductr.sbt.console.Column._

object Console {

  import scala.concurrent.duration._
  val timeout = 1.second

  def bundleInfo(bundles: Seq[Bundle], refresh: Boolean)(implicit system: ActorSystem): Unit = {
    import system.dispatcher

    val screen = system.actorOf(Screen.props(refresh, (bundles: Seq[Bundle]) => {
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

    screen ! bundles

    cleanUp(system, screen, refresh)
  }

  def bundleEvents(events: Seq[BundleEvent], refresh: Boolean)(implicit system: ActorSystem): Unit = {
    import system.dispatcher

    val screen = system.actorOf(Screen.props(refresh, (events: Seq[BundleEvent]) => {
      val columns = Vector(
        EventTime(events),
        Event(events),
        Description(events)
      )
      Screen.Layout(columns, Vector.empty)
    }), "screen")

    screen ! events

    cleanUp(system, screen, refresh)
  }

  def bundleLogs(logs: Seq[BundleLog], refresh: Boolean)(implicit system: ActorSystem): Unit = {
    import system.dispatcher

    val screen = system.actorOf(Screen.props(refresh, (logs: Seq[BundleLog]) => {
      val columns = Vector(
        LogTime(logs),
        Host(logs),
        Log(logs)
      )
      Screen.Layout(columns, Vector.empty)
    }), "screen")

    screen ! logs

    cleanUp(system, screen, refresh)
  }

  private def cleanUp(system: ActorSystem, screen: ActorRef, refresh: Boolean) = {

    import system.dispatcher

    if (refresh) {
      print(Ansi.ansi().saveCursorPosition())
      val con = new ConsoleReader()
      blocking {
        con.readCharacter('q')
      }
      system.stop(screen)
      print(Ansi.ansi().restorCursorPosition())
    } else {
      // There is no way to wait for actor termination from a non-actor,
      // other than using gracefulStop with a message that does nothing.
      case object WaitingForYou
      val f = gracefulStop(screen, timeout, WaitingForYou).andThen {
        case Failure(cause) => system.stop(screen)
      }
      Await.ready(f, timeout)
    }
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
