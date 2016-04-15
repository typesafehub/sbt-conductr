import org.scalatest.Matchers._
import ByteConversions._

scalaVersion in ThisBuild := "2.11.7"
version in ThisBuild := "0.1.0-SNAPSHOT"

lazy val debitApi = (project in file("debit-api"))
  .settings(libraryDependencies += lagomJavadslApi)

lazy val debitImpl = (project in file("debit-impl"))
  .enablePlugins(LagomJava)
  .dependsOn(debitApi)
  .settings(
    BundleKeys.conductrTargetVersion := ConductrVersion.V1_2
  )

lazy val creditApi = (project in file("credit-api"))
  .settings(libraryDependencies += lagomJavadslApi)

lazy val creditImpl = (project in file("credit-impl"))
  .enablePlugins(LagomJava)
  .dependsOn(creditApi)
  .settings(
    BundleKeys.conductrTargetVersion := ConductrVersion.V1_2
  )

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
                                 |    service-name  = "creditservice"
                                 |    acls          = [
                                 |      {
                                 |        http = {
                                 |          requests = [
                                 |            {
                                 |              path-beg = "/credit"
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
  creditContent should include (expectedCreditContent)

  val debitContent = IO.read((target in debitImpl in Bundle).value / "bundle" / "tmp" / "bundle.conf").indent
  val expectedDebitContent = """|endpoints = {
                                |  "debitservice" = {
                                |    bind-protocol = "http"
                                |    bind-port     = 0
                                |    service-name  = "debitservice"
                                |    acls          = [
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
  debitContent should include (expectedDebitContent)
}