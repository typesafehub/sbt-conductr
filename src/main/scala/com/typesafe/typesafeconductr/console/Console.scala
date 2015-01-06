/*
 * Copyright © 2014 Typesafe, Inc. All rights reserved.
 */

package com.typesafe.typesafeconductr
package console

import akka.actor.{ ActorRef, ActorSystem }
import akka.pattern.{ ask, pipe }
import com.typesafe.typesafeconductr.ConductRController.{ BundleInfosSource, GetBundleInfoStream }
import jline.console.ConsoleReader
import org.fusesource.jansi.Ansi
import scala.concurrent.blocking

object Console {
  def bundleInfo: ActorRef => ActorSystem => Unit = { implicit conductr =>
    { implicit system =>

      val screen = system.actorOf(Screen.props, "screen")

      import scala.concurrent.duration._
      import system.dispatcher
      conductr.ask(GetBundleInfoStream)(1.second).mapTo[BundleInfosSource].pipeTo(screen)

      print(Ansi.ansi().saveCursorPosition())

      val con = new ConsoleReader()
      blocking {
        con.readCharacter('q')
      }

      system.stop(screen)
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
