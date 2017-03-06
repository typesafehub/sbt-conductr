package impl

import akka.NotUsed
import api.DebitService
import scala.concurrent.Future
import com.lightbend.lagom.scaladsl.api.ServiceCall

class DebitServiceImpl extends DebitService {
  override def debit = ServiceCall { x =>
    Future.successful(NotUsed)
  }
}