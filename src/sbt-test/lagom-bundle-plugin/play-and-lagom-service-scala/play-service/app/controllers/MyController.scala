package controllers

import play.api._
import play.api.mvc._

class MyController extends Controller {

  def index = Action {
    Ok("Hello world")
  }

}
