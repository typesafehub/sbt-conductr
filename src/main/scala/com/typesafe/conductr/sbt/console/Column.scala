/*
 * Copyright © 2014 Typesafe, Inc. All rights reserved.
 */

package com.typesafe.conductr.sbt
package console

import com.typesafe.conductr.clientlib.scala.models.{ BundleEvent, BundleLog, Bundle }
import jline.TerminalFactory
import org.joda.time._

object Column {

  import AnsiConsole.Implicits._
  import Console.Implicits._

  private final val Space = " "
  private final val NewLine = "\n"
  private final val DateTimeFormat = "HH:mm:ss"

  /**
   * A fixed width column with no additional functionality.
   * Extend to set title, width and data to be displayed.
   */
  trait RegularColumn extends LeftJustified {
    /**
     * How much empty space to leave on the right-side of the column,
     */
    val marginRight = 1

    /**
     * Title of the column.
     */
    def title: String

    /**
     * Width of the column. Shorter values will be padded with spaces to this width.
     */
    def width: Int

    /**
     * Data of the column. Every data cell in the column can have multiple rows.
     * Therefore two-dimensional sequence.
     */
    def data: Seq[Seq[String]]

    /**
     * Column title padded to the required width.
     */
    def titleForPrint: String =
      justify(title)

    /**
     * Column data padded to the required width and flattened to one-dimensional sequence.
     * @param rowCounts specifies how many inner rows does every outer has
     */
    def dataForPrint(rowCounts: Seq[Int]): Seq[String] =
      data.zip(rowCounts).flatMap {
        case (rows, rowCount) =>
          rows.padTo(rowCount, "").map(ellipsize _ andThen justify)
      }

    /**
     * Wrap string with spaces for needed width.
     */
    protected def justify(s: String): String

    protected def space(length: Int): String =
      Stream.continually(Space).take(length).mkString

    /**
     * Add '... ' if string is over the allowed width.
     */
    private[console] def ellipsize(s: String): String = {
      val ellipsis = "... "
      if (s.visibleLength > width)
        s.take(width - ellipsis.length) + ellipsis
      else
        s
    }
  }

  /**
   * The wrapping column wraps the text instead of cutting it based on the column length.
   * If the text is longer than the width of the column, one additional line as separation are added. This
   * new line is necessary so that the next row is displayed on a new line.
   */
  trait WrappingColumn extends RegularColumn {

    /**
     * Left margin. If the text character count is greater than the column with this margin size
     * is used to align the the second line of the text within the column.
     */
    def marginLeft: Int

    override protected def justify(s: String): String = {
      val formattedText =
        if (s.visibleLength > width) s
          .trim
          .replace(NewLine, (Space * marginLeft))
          .grouped(width)
          .mkString(Space * marginLeft) + NewLine
        else s
      super.justify(formattedText)
    }

    override private[console] def ellipsize(s: String): String =
      if (s.visibleLength > width) s + NewLine else s
  }

  trait LeftJustified { self: RegularColumn =>
    override protected def justify(s: String): String =
      s + space(width - s.visibleLength)
  }

  trait RightJustified { self: RegularColumn =>
    override protected def justify(s: String): String = {
      space(width - s.visibleLength - marginRight) + s + space(marginRight)
    }
  }

  /**
   * Displays bundle id, bundle digest and config digest.
   */
  case class Id(bundles: Seq[Bundle]) extends RegularColumn {
    override val title = "ID"
    override val width = 27

    override val data =
      bundles.map { bundle =>
        val id = bundle.bundleId.split("-").map(_.take(7)).mkString("-")
        if (bundle.hasError) List(s"@|red ! $id|@") else List(id)
      }
  }

  /**
   * Displays bundle name.
   */
  case class Name(bundles: Seq[Bundle]) extends RegularColumn {
    override val title = "NAME"
    override val width = 30

    override val data = bundles.map { bundle => List(bundle.attributes.bundleName) }
  }

  /**
   * Displays the number of hosts where bundle is replicated.
   */
  case class Replicated(bundles: Seq[Bundle]) extends RegularColumn with RightJustified {
    override val title = "#REP"
    override val width = 7

    override val data =
      bundles.map { bundle =>
        List(bundle.bundleInstallations.size.toString)
      }
  }

  /**
   * Displays the number of hosts where bundle is starting.
   */
  case class Starting(bundles: Seq[Bundle]) extends RegularColumn with RightJustified {
    override val title = "#STR"
    override val width = 7

    override val data =
      bundles.map { bundle =>
        List(bundle.bundleExecutions.count(!_.isStarted).toString)
      }
  }

  /**
   * Displays the number of hosts where bundle is running.
   */
  case class Running(bundles: Seq[Bundle]) extends RegularColumn with RightJustified {
    override val title = "#RUN"
    override val width = 7

    override val data =
      bundles.map { bundle =>
        List(bundle.bundleExecutions.count(_.isStarted).toString)
      }
  }

  case class Spacer(height: Int, width: Int) extends RegularColumn {
    override val title = ""

    override val data = Seq.fill(height)(Seq.empty)
  }

  case class EventTime(events: Seq[BundleEvent]) extends RegularColumn {
    override val title = "TIME"
    override val width = 11

    override val data = events.map { event => List(new DateTime(event.timestamp).toString(DateTimeFormat)) }
  }

  case class Event(events: Seq[BundleEvent]) extends RegularColumn {
    override val title = "EVENT"
    override val width = 50

    override val data = events.map { event => List(event.event) }
  }

  case class Description(events: Seq[BundleEvent]) extends WrappingColumn {
    override val title = "DESC"
    override val marginLeft = 61
    override val width = getWrappingWidth(title.size, marginLeft)

    override val data = events.map { event => List(event.description) }
  }

  case class LogTime(logs: Seq[BundleLog]) extends RegularColumn {
    override val title = "TIME"
    override val width = 11

    override val data = logs.map { event => List(new DateTime(event.timestamp).toString(DateTimeFormat)) }
  }

  case class Host(logs: Seq[BundleLog]) extends RegularColumn {
    override val title = "HOST"
    override val width = 15

    override val data = logs.map { event => List(event.host) }
  }

  case class Log(logs: Seq[BundleLog]) extends WrappingColumn {
    override val title = "LOG"
    override val marginLeft = 26
    override val width = getWrappingWidth(title.size, marginLeft)

    override val data = logs.map { event => List(event.message.trim) }
  }

  private def getWrappingWidth(minWidth: Int, margin: Int): Int = {
    val width = TerminalFactory.get().getWidth - margin
    if (width >= 0) width else minWidth
  }
}
