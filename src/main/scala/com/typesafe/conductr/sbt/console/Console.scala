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

object Console {

  import scala.concurrent.duration._
  val timeout = 10.second

  def bundleInfo(refresh: Boolean): ActorRef => ActorSystem => Unit = { implicit conductr =>
    { implicit system =>

      import system.dispatcher

      val screen = system.actorOf(Screen.props(refresh), "screen")
      conductr
        .ask(ConductRController.GetBundleInfoStream)(timeout)
        .mapTo[ConductRController.BundleInfosSource]
        .pipeTo(screen)

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
