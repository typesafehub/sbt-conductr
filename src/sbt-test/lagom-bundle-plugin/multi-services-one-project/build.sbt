import org.scalatest.Matchers._
import ByteConversions._

scalaVersion in ThisBuild := "2.11.7"
version in ThisBuild := "0.1.0-SNAPSHOT"

lazy val multiServicesApi = (project in file("multi-services-api"))
  .settings(libraryDependencies += lagomJavadslApi)

lazy val multiServicesImpl = (project in file("multi-services-impl"))
  .enablePlugins(LagomJava)
  .dependsOn(multiServicesApi)
  .settings(
    BundleKeys.conductrTargetVersion := ConductrVersion.V2
  )

// Test assertions
val checkBundleConf = taskKey[Unit]("")

checkBundleConf := {
  implicit class StringOps(s: String) {
    def indent: String = s.replaceAll("  ", "")
  }

  val contents = IO.read((target in multiServicesImpl in Bundle).value / "bundle" / "tmp" / "bundle.conf").indent
  val expectedContent = """|endpoints = {
                           |  "fooservice" = {
                           |    bind-protocol = "http"
                           |    bind-port     = 0
                           |    service-name  = "fooservice"
                           |    acls          = [
                           |      {
                           |        http = {
                           |          requests = [
                           |            {
                           |              path-beg = "/foo"
                           |            }
                           |          ]
                           |        }
                           |      }
                           |    ]
                           |  },
                           |  "barservice" = {
                           |    bind-protocol = "http"
                           |    bind-port     = 0
                           |    service-name  = "barservice"
                           |    acls          = [
                           |      {
                           |        http = {
                           |          requests = [
                           |            {
                           |              path-beg = "/bar"
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
  contents should include (expectedContent)
}