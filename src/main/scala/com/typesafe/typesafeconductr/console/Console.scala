/*
 * Copyright © 2014 Typesafe, Inc. All rights reserved.
 */

package com.typesafe.typesafeconductr
package console

import akka.actor.{ ActorRef, ActorSystem }
import akka.pattern.{ ask, pipe, gracefulStop }
import com.typesafe.typesafeconductr.ConductRController.{ BundleInfosSource, GetBundleInfoStream }
import jline.console.ConsoleReader
import org.fusesource.jansi.Ansi
import scala.concurrent.{ blocking, Await }

object Console {

  import scala.concurrent.duration._
  val timeout = 1.second

  def bundleInfo(refresh: Boolean): ActorRef => ActorSystem => Unit = { implicit conductr =>
    { implicit system =>

      val screen = system.actorOf(Screen.props(refresh), "screen")

      import system.dispatcher
      conductr.ask(GetBundleInfoStream)(timeout).mapTo[BundleInfosSource].pipeTo(screen)

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
        Await.ready(gracefulStop(screen, timeout, WaitingForYou), timeout)
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
