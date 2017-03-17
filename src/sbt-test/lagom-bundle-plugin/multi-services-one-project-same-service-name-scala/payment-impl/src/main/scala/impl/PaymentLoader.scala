package impl

import api._
import com.lightbend.lagom.scaladsl.devmode._
import com.lightbend.lagom.scaladsl.server._
import com.softwaremill.macwire.wire
import com.typesafe.conductr.bundlelib.lagom.scaladsl.ConductRApplicationComponents
import play.api.libs.ws.ahc.AhcWSComponents

abstract class PaymentApplication(context: LagomApplicationContext) extends LagomApplication(context) with AhcWSComponents {

  override lazy val lagomServer = LagomServer.forServices(
    bindService[CreditService].to(wire[CreditServiceImpl]),
    bindService[DebitService].to(wire[DebitServiceImpl])
  )
}

class PaymentApplicationLoader extends LagomApplicationLoader {
  override def load(context: LagomApplicationContext): LagomApplication =
    new PaymentApplication(context) with ConductRApplicationComponents

  override def loadDevMode(context: LagomApplicationContext): LagomApplication =
    new PaymentApplication(context) with LagomDevModeComponents

  override def describeServices = List(
    readDescriptor[CreditService],
    readDescriptor[DebitService]
  )
}