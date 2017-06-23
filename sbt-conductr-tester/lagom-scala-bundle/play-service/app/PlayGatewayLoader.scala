import api.FooService
import com.lightbend.lagom.internal.client.CircuitBreakerMetricsProviderImpl
import com.lightbend.lagom.internal.spi.CircuitBreakerMetricsProvider
import com.lightbend.lagom.scaladsl.api.{ServiceAcl, ServiceInfo}
import com.lightbend.lagom.scaladsl.client.LagomServiceClientComponents
import com.lightbend.lagom.scaladsl.devmode.LagomDevModeComponents
import com.typesafe.conductr.bundlelib.lagom.scaladsl.ConductRServiceLocatorComponents
import com.typesafe.conductr.bundlelib.play.api.ConductRLifecycleComponents
import controllers.{Assets, MyController}
import play.api.ApplicationLoader.Context
import play.api.{ApplicationLoader, BuiltInComponentsFromContext, Mode}
import play.api.i18n.I18nComponents
import play.api.libs.ws.ahc.AhcWSComponents
import router.Routes

import scala.collection.immutable
import scala.concurrent.ExecutionContext
import com.softwaremill.macwire.wire


abstract class PlayGateway(context: Context) extends BuiltInComponentsFromContext(context)
  with I18nComponents
  with AhcWSComponents
  with LagomServiceClientComponents {

  override lazy val serviceInfo: ServiceInfo = ServiceInfo(
    "web-gateway",
    Map(
      "web-gateway" -> immutable.Seq(ServiceAcl.forPathRegex("(?!/api/).*"))
    )
  )
  override implicit lazy val executionContext: ExecutionContext = actorSystem.dispatcher
  lazy val assets = wire[Assets]
  override lazy val router = {
    val prefix = "/"
    wire[Routes]
  }

  lazy val fooService: FooService = serviceClient.implement[FooService]

  lazy val itemController = wire[MyController]
}

class PlayGatewayLoader extends ApplicationLoader {
  override def load(context: Context) = context.environment.mode match {
    case Mode.Dev =>
      new PlayGateway(context) with LagomDevModeComponents {}.application
    case _ =>
      new PlayGateway(context) with ConductRServiceLocatorComponents with ConductRLifecycleComponents {
        // Workaround for https://github.com/typesafehub/conductr-lib/issues/145
        override lazy val circuitBreakerMetricsProvider: CircuitBreakerMetricsProvider =
          new CircuitBreakerMetricsProviderImpl(actorSystem)
      }.application
  }
}
