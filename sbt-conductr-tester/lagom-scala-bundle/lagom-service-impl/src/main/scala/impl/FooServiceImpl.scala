package impl

import akka.NotUsed
import api._
import scala.concurrent.Future
import com.lightbend.lagom.scaladsl.api.ServiceCall

class FooServiceImpl extends FooService {
  override def foo = ServiceCall { x =>
    Future.successful(NotUsed)
  }
}