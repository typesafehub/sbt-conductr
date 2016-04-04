package controllers

import play.api._
import play.api.mvc._

object MyController extends Controller {

  def index = Action {
    Ok("Hello world")
  }
}
