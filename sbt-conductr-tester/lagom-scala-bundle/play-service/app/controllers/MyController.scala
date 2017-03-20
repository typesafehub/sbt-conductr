package controllers

import api.FooService
import play.api.mvc._

import scala.concurrent.ExecutionContext

class MyController(fooService: FooService)(implicit ec: ExecutionContext) extends Controller {

  def index = Action {
    Ok("Hello world")
  }

  def proxyToLagom = Action.async {
    fooService.foo.invoke().map(str =>
      Ok(s"via-play $str")
    )
  }

}
