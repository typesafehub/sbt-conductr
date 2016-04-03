import org.scalatest.Matchers._
import ByteConversions._

scalaVersion in ThisBuild := "2.11.7"
version in ThisBuild := "0.1.0-SNAPSHOT"

lazy val paymentApi = (project in file("payment-api"))
  .settings(libraryDependencies += lagomJavadslApi)

lazy val paymentImpl = (project in file("payment-impl"))
  .enablePlugins(LagomJava)
  .dependsOn(paymentApi)

lazy val socialApi = (project in file("social-api"))
  .settings(libraryDependencies += lagomJavadslApi)

lazy val socialImpl = (project in file("social-impl"))
  .enablePlugins(LagomJava)
  .dependsOn(socialApi)

// Test assertions
val checkBundleConf = taskKey[Unit]("")

checkBundleConf := {
  implicit class StringOps(s: String) {
    def indent: String = s.replaceAll("  ", "")
  }

  val paymentContent = IO.read((target in paymentImpl in Bundle).value / "bundle" / "tmp" / "bundle.conf").indent
  val expectedPaymentContent = """|endpoints = {
                                  |  "debitservice" = {
                                  |    bind-protocol = "http"
                                  |    bind-port     = 0
                                  |    services      = ["http://:9000/debitservice", "http://:9000?preservePath"]
                                  |  },
                                  |  "creditservice" = {
                                  |    bind-protocol = "http"
                                  |    bind-port     = 0
                                  |    services      = ["http://:9000/creditservice", "http://:9000?preservePath"]
                                  |  },
                                  |  "akka-remote" = {
                                  |    bind-protocol = "tcp"
                                  |    bind-port = 0
                                  |    services= []
                                  |  }
                                  |}""".stripMargin.indent
  paymentContent should include (expectedPaymentContent)

  val socialContent = IO.read((target in socialImpl in Bundle).value / "bundle" / "tmp" / "bundle.conf").indent
  val expectedSocialContent = """|endpoints = {
                                 |  "socialservice" = {
                                 |    bind-protocol = "http"
                                 |    bind-port     = 0
                                 |    services      = ["http://:9000/socialservice", "http://:9000?preservePath"]
                                 |  },
                                 |  "akka-remote" = {
                                 |    bind-protocol = "tcp"
                                 |    bind-port = 0
                                 |    services= []
                                 |  }
                                 |}""".stripMargin.indent
  socialContent should include (expectedSocialContent)
}