package api

import akka.NotUsed
import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.api.Descriptor
import com.lightbend.lagom.scaladsl.api.Service
import com.lightbend.lagom.scaladsl.api.transport.Method

trait FooService extends Service {

  def foo: ServiceCall[NotUsed, NotUsed]

  override def descriptor = {
    import Service._

    named("fooservice").withCalls(
      restCall(Method.GET, "/foo", foo)
    ).withAutoAcl(true)
  }
}
