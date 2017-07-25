package impl

import api.FooService
import com.lightbend.lagom.scaladsl.api.ServiceCall

import scala.concurrent.Future

class FooServiceImpl extends FooService {
  override def foo = ServiceCall { _ =>
    Future.successful("foo-response-hardcoded in FooServiceImpl")
  }
}
