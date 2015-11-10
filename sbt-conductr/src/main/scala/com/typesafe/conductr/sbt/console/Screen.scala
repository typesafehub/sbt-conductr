/*
 * Copyright © 2014 Typesafe, Inc. All rights reserved.
 */

package com.typesafe.conductr.sbt
package console

import akka.actor.{ Actor, Props, Status }
import akka.stream.scaladsl.{ ImplicitMaterializer, Sink }
import com.typesafe.conductr.client.ConductRController
import jline.TerminalFactory
import scala.concurrent.duration.DurationInt

object Screen {

  private case class Data[A](data: A)

  private case object CheckSize

  case class Layout(columns: Vector[Column.RegularColumn], notes: Vector[String])

  def props[A <: Iterable[B], B](refresh: Boolean, layout: A => Layout): Props =
    Props(new Screen(refresh, layout))
}

/**
 * Draws data to the screen. Data is subscribed from a ConductRController.BundleInfo flow,
 * which is received in a ConductRController.BundleInfosSource message and then materialized.
 */
class Screen[A <: Iterable[B], B](refresh: Boolean, layout: A => Screen.Layout) extends Actor with ImplicitMaterializer {

  import AnsiConsole.Implicits._
  import Column._
  import Screen._
  import context.dispatcher

  private val terminal = TerminalFactory.get

  private val resizeTask = context.system.scheduler.schedule(0.millis, 100.millis, self, CheckSize)

  private var screenWidth = terminal.getWidth

  private var data: A = _

  def receive: Receive = {
    case ConductRController.DataSource(source) =>
      if (refresh)
        source.runForeach(self ! Data(_))
      else
        source.runWith(Sink.head).foreach(self ! Data(_))

    case CheckSize =>
      checkSize()

    case Status.Failure(_) =>
      context.stop(self)

    case d: Data[A] =>
      data = d.data
      if (refresh) {
        AnsiConsole.clear()
        printScreen()
        printInfoBar()
        AnsiConsole.hideCursor()
      } else {
        printScreen()
        println()
        context.stop(self)
      }
  }

  override def postStop(): Unit =
    resizeTask.cancel()

  private def printScreen(): Unit = {
    val Screen.Layout(leftMostColumns, notes) = layout(data)
    val totalWidth = leftMostColumns.map(_.width).sum
    val allColumns = leftMostColumns :+ Spacer(data.size, screenWidth - totalWidth)

    println(allColumns.map(_.titleForPrint).reduce(_ + _).invert.render)

    val rowCounts = allColumns.map(_.data.map(_.size)).transpose.map(_.max)
    val lines = allColumns.map(_.dataForPrint(rowCounts)).transpose :+ notes
    for {
      line <- lines
      cell <- line
    } print(cell.render)
  }

  private def printInfoBar(): Unit = {
    AnsiConsole.goToLine(terminal.getHeight)
    val hints = " q: Quit "
    print(hints.reverse.padTo(screenWidth, " ").reverse.mkString)
  }

  private def checkSize(): Unit = {
    val currentScreenWidth = terminal.getWidth
    if (currentScreenWidth != screenWidth) {
      screenWidth = currentScreenWidth
      printScreen()
    }
  }
}
