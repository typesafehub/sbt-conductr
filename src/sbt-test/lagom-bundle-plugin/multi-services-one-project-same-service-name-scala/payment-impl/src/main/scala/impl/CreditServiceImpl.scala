package impl

import akka.NotUsed
import api.CreditService
import scala.concurrent.Future
import com.lightbend.lagom.scaladsl.api.ServiceCall

class CreditServiceImpl extends CreditService {
  override def credit = ServiceCall { x =>
    Future.successful(NotUsed)
  }
}