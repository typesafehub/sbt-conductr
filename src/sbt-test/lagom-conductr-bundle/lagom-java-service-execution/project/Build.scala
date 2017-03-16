/* 
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */

import com.ning.http.client.AsyncHttpClientConfig

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

// copy/pasted and adapted from
// https://github.com/lagom/lagom/blob/d9f4df0f56f7c567e88602fa91698b8d8f5de3d9/dev/sbt-plugin/src/sbt-test/sbt-plugin/run-all-javadsl/project/Build.scala#L9
object DevModeBuild {

  def waitForRequestToContain(uri: String, toContain: String)(totalAttempts: Int = 10): Unit = {
    waitFor[String](
      makeRequest(uri),
      _.contains(toContain), {
        case Success(msg) => s"'$msg' did not contain '$toContain'"
        case Failure(t) =>
          t.printStackTrace()
          t.getMessage
      }
    )(totalAttempts)
  }

  def makeRequest(uri: String): Try[String] = {
    import play.api.libs.ws._
    import play.api.libs.ws.ning._

    import scala.concurrent.ExecutionContext.Implicits.global

    val config = new NingAsyncHttpClientConfigBuilder(DefaultWSClientConfig()).build()
    val builder = new AsyncHttpClientConfig.Builder(config)
    val wsClient:WSClient = new NingWSClient(builder.build())
    val future = wsClient.url(uri).get().map(_.body)
    Await.ready(future, Duration(1, "seconds"))
    future.value.get
  }

  def waitFor[T](check: => Try[T], assertion: T => Boolean, error: Try[T] => String)(totalAttempts: Int = 10): Unit = {
    var checks = 0
    var actual = check
    while ((actual.isFailure || !assertion(actual.get)) && checks < totalAttempts) {
      Thread.sleep(1000)
      actual = check
      checks += 1
    }
    if (actual.isFailure && !assertion(actual.get)) {
      throw new RuntimeException(error(actual))
    }
  }

}
