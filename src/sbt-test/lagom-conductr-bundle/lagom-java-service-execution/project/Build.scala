/* 
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
import java.net.HttpURLConnection
import java.io.{BufferedReader, InputStreamReader}

import sbt.{IO, File}

// copy/pasted and adapted from
// https://github.com/lagom/lagom/blob/d9f4df0f56f7c567e88602fa91698b8d8f5de3d9/dev/sbt-plugin/src/sbt-test/sbt-plugin/run-all-javadsl/project/Build.scala#L9
object DevModeBuild {

  val ConnectTimeout = 10000
  val ReadTimeout = 10000

  def waitForRequestToContain(uri: String, toContain: String)(totalAttempts:Int=10): Unit = {
    waitFor[String](
      makeRequest(uri),
      _.contains(toContain),
      actual => s"'$actual' did not contain '$toContain'"
    )(totalAttempts)
  }

  def makeRequest(uri: String): String = {
    var conn: java.net.HttpURLConnection = null
    try {
      val url = new java.net.URL(uri)
      conn = url.openConnection().asInstanceOf[HttpURLConnection]
      conn.setConnectTimeout(ConnectTimeout)
      conn.setReadTimeout(ReadTimeout)
      conn.getResponseCode // we make this call just to block until a response is returned.
      val br = new BufferedReader(new InputStreamReader((conn.getInputStream())))
      Stream.continually(br.readLine()).takeWhile(_ != null).mkString("\n").trim()
    }
    finally if(conn != null) conn.disconnect()
  }

  def waitFor[T](check: => T, assertion: T => Boolean, error: T => String)(totalAttempts:Int=10): Unit = {
    var checks = 0
    var actual = check
    while (!assertion(actual) && checks < totalAttempts) {
      Thread.sleep(1000)
      actual = check
      checks += 1
    }
    if (!assertion(actual)) {
      throw new RuntimeException(error(actual))
    }
  }

}
