package test

import scala.util.Try

object TestApp extends App {
  val message = sys.env.getOrElse("MESSAGE", "Up and running!")
  val timeout = Try(sys.env.getOrElse("TIMEOUT", "").toInt).getOrElse(60000)
  println(message)
  println(s"Sleeping for $timeout milliseconds ...")
  Thread.sleep(timeout) // We will die eventually, but also provide the opportunity for us to die.
}
