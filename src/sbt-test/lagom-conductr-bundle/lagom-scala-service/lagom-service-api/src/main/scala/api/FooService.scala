package api

import akka.NotUsed
import com.lightbend.lagom.scaladsl.api.transport.Method
import com.lightbend.lagom.scaladsl.api.{Service, ServiceCall}

trait FooService extends Service {

  def foo: ServiceCall[NotUsed, String]

  import Service._

  override def descriptor =
    named("fooservice").withCalls(
      restCall(Method.GET, "/foo", foo)
    ).withAutoAcl(true)

}
