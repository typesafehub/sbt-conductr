import org.scalatest.Matchers._
import ByteConversions._

scalaVersion in ThisBuild := "2.11.7"
version in ThisBuild := "0.1.0-SNAPSHOT"

lazy val debitApi = (project in file("debit-api"))
  .settings(libraryDependencies += lagomJavadslApi)

lazy val debitImpl = (project in file("debit-impl"))
  .enablePlugins(LagomJava)
  .dependsOn(debitApi)

lazy val creditApi = (project in file("credit-api"))
  .settings(libraryDependencies += lagomJavadslApi)

lazy val creditImpl = (project in file("credit-impl"))
  .enablePlugins(LagomJava)
  .dependsOn(creditApi)

// Test assertions
val checkBundleConf = taskKey[Unit]("")

checkBundleConf := {
  implicit class StringOps(s: String) {
    def indent: String = s.replaceAll("  ", "")
  }

  val creditContent = IO.read((target in creditImpl in Bundle).value / "bundle" / "tmp" / "bundle.conf").indent
  val expectedCreditContent = """|endpoints = {
                                 |  "creditservice" = {
                                 |    bind-protocol = "http"
                                 |    bind-port     = 0
                                 |    services      = ["http://:9000/creditservice", "http://:9000/credit?preservePath"]
                                 |  },
                                 |  "akka-remote" = {
                                 |    bind-protocol = "tcp"
                                 |    bind-port = 0
                                 |    services= []
                                 |  }
                                 |}""".stripMargin.indent
  creditContent should include (expectedCreditContent)

  val debitContent = IO.read((target in debitImpl in Bundle).value / "bundle" / "tmp" / "bundle.conf").indent
  val expectedDebitContent = """|endpoints = {
                                |  "debitservice" = {
                                |    bind-protocol = "http"
                                |    bind-port     = 0
                                |    services      = ["http://:9000/debitservice", "http://:9000/debit?preservePath"]
                                |  },
                                |  "akka-remote" = {
                                |    bind-protocol = "tcp"
                                |    bind-port = 0
                                |    services= []
                                |  }
                                |}""".stripMargin.indent
  debitContent should include (expectedDebitContent)
}