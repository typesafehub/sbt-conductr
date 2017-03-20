package impl

import api._
import com.lightbend.lagom.scaladsl.api.ServiceCall

import scala.concurrent.Future

class FooServiceImpl extends FooService {
  override def foo = ServiceCall { x =>
    Future.successful("hardcoded-foo-scala-response")
  }
}