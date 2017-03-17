package impl

import java.nio.file.{Files, StandardOpenOption}
import java.util.Date

import com.typesafe.conductr.bundlelib.lagom.scaladsl.ConductRApplicationComponents
import com.lightbend.lagom.scaladsl.server._
import com.lightbend.lagom.scaladsl.devmode.LagomDevModeComponents
import play.api.libs.ws.ahc.AhcWSComponents
import api.FooService

class FooLoader extends LagomApplicationLoader {

	override def load(context: LagomApplicationContext): LagomApplication =
		new FooApplication(context) with ConductRApplicationComponents

	override def loadDevMode(context: LagomApplicationContext): LagomApplication =
		new FooApplication(context) with LagomDevModeComponents

	override def describeServices = List(
			readDescriptor[FooService]
  )
}

abstract class FooApplication(context: LagomApplicationContext)
	extends LagomApplication(context)
		with AhcWSComponents {

	override lazy val lagomServer = LagomServer.forServices(
		bindService[FooService].to(new FooServiceImpl)
	)
}