/*
 * Copyright © 2014 Typesafe, Inc. All rights reserved.
 */

package com.typesafe.reactiveruntime
package console

import akka.actor.{ Actor, Props }
import akka.stream.scaladsl.{ ImplicitFlowMaterializer, Source }
import com.typesafe.reactiveruntime.WatchdogController
import jline.TerminalFactory
import scala.concurrent.duration.DurationInt

object Screen {

  private case class Bundles(bundles: Seq[WatchdogController.BundleInfo])

  private case object CheckSize

  def props: Props =
    Props(new Screen())
}

/**
 * Draws data to the screen. Data is subscribed from a [[WatchdogController.BundleInfo]] flow,
 * which is received in a [[WatchdogController.BundleInfosSource]] message and then materialized.
 */
class Screen extends Actor with ImplicitFlowMaterializer {

  import AnsiConsole.Implicits._
  import Column._
  import Screen._
  import context.dispatcher

  private val terminal = TerminalFactory.get

  private val resizeTask = context.system.scheduler.schedule(0.millis, 100.millis, self, CheckSize)

  private var screenWidth = terminal.getWidth

  private var bundles: Seq[WatchdogController.BundleInfo] =
    Nil

  def receive: Receive = {
    case WatchdogController.BundleInfosSource(source) =>
      source.foreach(self ! Bundles(_))
    case Bundles(b) =>
      bundles = b
      printScreen()
    case CheckSize =>
      checkSize()
  }

  override def postStop(): Unit =
    resizeTask.cancel()

  private def printScreen(): Unit = {
    val leftMostColumns =
      Vector(
        Id(bundles),
        Where(bundles),
        Running(bundles),
        Cpu(bundles),
        Memory(bundles),
        FileSize(bundles)
      )
    val totalWidth = leftMostColumns.map(_.width).sum
    val allColumns = leftMostColumns :+ Roles(bundles, screenWidth - totalWidth)

    AnsiConsole.clear
    println(allColumns.map(_.titleForPrint).reduce(_ + _).invert.render)

    val rowCounts = allColumns.map(_.data.map(_.size)).transpose.map(_.max)
    val lines = allColumns.map(_.dataForPrint(rowCounts)).transpose
    for {
      line <- lines
      cell <- line
    } print(cell.render)

    AnsiConsole.hideCursor
  }

  private def checkSize(): Unit = {
    val currentScreenWidth = terminal.getWidth
    if (currentScreenWidth != screenWidth) {
      screenWidth = currentScreenWidth
      printScreen()
    }
  }
}
