package controllers

import java.util.concurrent.TimeUnit
import javax.inject._

import play.api._
import play.api.mvc._
import api.FooService

@Singleton
class MyController @Inject()(fooService: FooService) extends Controller {

  def index = Action {
    Ok("Hello world")
  }

  def proxyToLagom = Action {
    Ok(fooService.foo().invoke().toCompletableFuture.get(10, TimeUnit.SECONDS))
  }

}
