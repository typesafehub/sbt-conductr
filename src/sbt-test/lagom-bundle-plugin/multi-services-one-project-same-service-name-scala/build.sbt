import org.scalatest.Matchers._
import ByteConversions._

scalaVersion in ThisBuild := "2.11.7"
version in ThisBuild := "0.1.0-SNAPSHOT"

val macwire = "com.softwaremill.macwire" %% "macros" % "2.2.5" % "provided"

lazy val paymentApi = (project in file("payment-api"))
  .settings(libraryDependencies += lagomScaladslApi)

lazy val paymentImpl = (project in file("payment-impl"))
  .enablePlugins(LagomScala)
  .settings(libraryDependencies += macwire)
  .dependsOn(paymentApi)

// Test assertions
val checkBundleConf = taskKey[Unit]("")

checkBundleConf := {
  implicit class StringOps(s: String) {
    def indent: String = s.replaceAll("  ", "")
  }

  val paymentContent = IO.read((target in paymentImpl in Bundle).value / "bundle" / "tmp" / "bundle.conf").indent
  val expectedPaymentContent = """|endpoints = {
                                  |  "paymentservice" = {
                                  |    bind-protocol = "http"
                                  |    bind-port     = 0
                                  |    service-name  = "paymentservice"
                                  |    acls          = [
                                  |      {
                                  |        http = {
                                  |          requests = [
                                  |            {
                                  |              path-beg = "/credit"
                                  |            }
                                  |          ]
                                  |        }
                                  |      },
                                  |      {
                                  |        http = {
                                  |          requests = [
                                  |            {
                                  |              path-beg = "/debit"
                                  |            }
                                  |          ]
                                  |        }
                                  |      }
                                  |    ]
                                  |  },
                                  |  "akka-remote" = {
                                  |    bind-protocol = "tcp"
                                  |    bind-port = 0
                                  |    services= []
                                  |  }
                                  |}""".stripMargin.indent
  paymentContent should include (expectedPaymentContent)
}