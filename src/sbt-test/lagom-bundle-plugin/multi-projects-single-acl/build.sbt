import org.scalatest.Matchers._
import ByteConversions._

scalaVersion in ThisBuild := "2.11.7"
version in ThisBuild := "0.1.0-SNAPSHOT"

lazy val frontendApi = (project in file("frontend-api"))
  .settings(libraryDependencies += lagomJavadslApi)

lazy val frontendImpl = (project in file("frontend-impl"))
  .enablePlugins(LagomJava)
  .dependsOn(frontendApi)

lazy val backendApi = (project in file("backend-api"))
  .settings(libraryDependencies += lagomJavadslApi)

lazy val backendImpl = (project in file("backend-impl"))
  .enablePlugins(LagomJava)
  .dependsOn(backendApi)

// Test assertions
val checkBundleConf = taskKey[Unit]("")

checkBundleConf := {
  implicit class StringOps(s: String) {
    def indent: String = s.replaceAll("  ", "")
  }

  val frontendContent = IO.read((target in frontendImpl in Bundle).value / "bundle" / "tmp" / "bundle.conf").indent
  val expectedFrontendContent = """|endpoints = {
                                   |  "frontendservice" = {
                                   |    bind-protocol = "http"
                                   |    bind-port     = 0
                                   |    service-name  = "frontendservice"
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
                                   |  "akka-remote" = {
                                   |    bind-protocol = "tcp"
                                   |    bind-port = 0
                                   |    services= []
                                   |  }
                                   |}""".stripMargin.indent
  frontendContent should include (expectedFrontendContent)

  // Acls are not enabled for the backend. As a result the service is still written to the bundle.conf
  val backendContent = IO.read((target in backendImpl in Bundle).value / "bundle" / "tmp" / "bundle.conf").indent
  val expectedBackendContent = """|endpoints = {
                                  |  "backendservice" = {
                                  |    bind-protocol = "http"
                                  |    bind-port     = 0
                                  |    service-name  = "backendservice"
                                  |    acls          = [
                                  |
                                  |    ]
                                  |  },
                                  |  "akka-remote" = {
                                  |    bind-protocol = "tcp"
                                  |    bind-port = 0
                                  |    services= []
                                  |  }
                                  |}""".stripMargin.indent
  backendContent should include (expectedBackendContent)
}