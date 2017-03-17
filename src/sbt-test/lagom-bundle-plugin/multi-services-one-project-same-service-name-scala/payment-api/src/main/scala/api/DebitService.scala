package api

import akka.NotUsed
import com.lightbend.lagom.scaladsl.api._

trait DebitService extends Service {

  def debit: ServiceCall[NotUsed, NotUsed]

  override def descriptor: Descriptor = {
    import Service._
    named("paymentservice")
      .withCalls(call(debit))
      .withAutoAcl(true)
  }
}