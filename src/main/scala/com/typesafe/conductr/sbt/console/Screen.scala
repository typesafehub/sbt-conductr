/*
 * Copyright © 2014 Typesafe, Inc. All rights reserved.
 */

package com.typesafe.conductr.sbt
package console

import akka.actor.{ Actor, Props, Status }
import akka.stream.scaladsl.{ ImplicitFlowMaterializer, Sink }
import com.typesafe.conductr.client.ConductRController
import jline.TerminalFactory
import scala.concurrent.duration.DurationInt

object Screen {

  private case class Bundles(bundles: Seq[ConductRController.BundleInfo])

  private case object CheckSize

  def props(refresh: Boolean): Props =
    Props(new Screen(refresh))
}

/**
 * Draws data to the screen. Data is subscribed from a ConductRController.BundleInfo flow,
 * which is received in a ConductRController.BundleInfosSource message and then materialized.
 */
class Screen(refresh: Boolean) extends Actor with ImplicitFlowMaterializer {

  import AnsiConsole.Implicits._
  import Column._
  import Screen._
  import context.dispatcher

  private val terminal = TerminalFactory.get

  private val resizeTask = context.system.scheduler.schedule(0.millis, 100.millis, self, CheckSize)

  private var screenWidth = terminal.getWidth

  private var bundles: Seq[ConductRController.BundleInfo] =
    Nil

  def receive: Receive = {
    case ConductRController.BundleInfosSource(source) =>
      if (refresh)
        source.runForeach(self ! Bundles(_))
      else
        source.runWith(Sink.head).foreach(self ! Bundles(_))

    case Bundles(b) =>
      bundles = b
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

    case CheckSize =>
      checkSize()

    case Status.Failure(_) =>
      context.stop(self)
  }

  override def postStop(): Unit =
    resizeTask.cancel()

  private def printScreen(): Unit = {
    val leftMostColumns =
      Vector(
        Id(bundles),
        Name(bundles),
        Replicated(bundles),
        Starting(bundles),
        Running(bundles)
      )
    val totalWidth = leftMostColumns.map(_.width).sum
    val allColumns = leftMostColumns :+ Roles(bundles, screenWidth - totalWidth)

    println(allColumns.map(_.titleForPrint).reduce(_ + _).invert.render)

    val rowCounts = allColumns.map(_.data.map(_.size)).transpose.map(_.max)
    val lines = allColumns.map(_.dataForPrint(rowCounts)).transpose
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
