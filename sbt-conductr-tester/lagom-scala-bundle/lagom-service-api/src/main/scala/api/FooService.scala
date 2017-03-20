package api

import akka.NotUsed
import com.lightbend.lagom.scaladsl.api._

trait FooService extends Service {

  def foo: ServiceCall[NotUsed, String]

  override def descriptor: Descriptor = {
    import Service._
    named("fooservice")
      .withCalls(call(foo))
      .withAutoAcl(true)
  }
}