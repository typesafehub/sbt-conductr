import org.scalatest.Matchers._
import ByteConversions._

scalaVersion in ThisBuild := "2.11.7"
version in ThisBuild := "0.1.0-SNAPSHOT"

lazy val paymentApi = (project in file("payment-api"))
  .settings(libraryDependencies += lagomJavadslApi)

lazy val paymentImpl = (project in file("payment-impl"))
  .enablePlugins(LagomJava)
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
                                  |    services      = ["http://:9000/credit?preservePath", "http://:9000/paymentservice", "http://:9000/debit?preservePath"]
                                  |  },
                                  |  "akka-remote" = {
                                  |    bind-protocol = "tcp"
                                  |    bind-port = 0
                                  |    services= []
                                  |  }
                                  |}""".stripMargin.indent
  paymentContent should include (expectedPaymentContent)
}