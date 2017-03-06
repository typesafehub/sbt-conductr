package api

import akka.NotUsed
import com.lightbend.lagom.scaladsl.api._

trait CreditService extends Service {

  def credit: ServiceCall[NotUsed, NotUsed]

  override def descriptor: Descriptor = {
    import Service._
    named("paymentservice")
      .withCalls(call(credit))
      .withAutoAcl(true)
  }
}