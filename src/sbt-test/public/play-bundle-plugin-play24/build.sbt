import org.scalatest.Matchers._
import ByteConversions._

lazy val root = (project in file(".")).enablePlugins(PlayScala)

name := "play24"

version := "0.1.0-SNAPSHOT"
scalaVersion := "2.11.8"

val checkBundleDist = taskKey[Unit]("check-bundle-dist-contents")
checkBundleDist := {
  val bundleContentsConf = IO.read((target in Bundle).value / "bundle" / "tmp" / "bundle.conf")
  val expectedContentsConf =
    """version              = "1"
      |name                 = "play24"
      |compatibilityVersion = "0"
      |tags                 = ["0.1.0-SNAPSHOT"]
      |system               = "play24"
      |systemVersion        = "0"
      |nrOfCpus             = 0.1
      |memory               = 402653184
      |diskSpace            = 200000000
      |roles                = ["web"]
      |components = {
      |  play24 = {
      |    description      = "play24"
      |    file-system-type = "universal"
      |    start-command    = ["play24/bin/play24", "-J-Xms134217728", "-J-Xmx134217728"]
      |    endpoints = {
      |      "web" = {
      |        bind-protocol = "http"
      |        bind-port     = 0
      |        service-name  = "web"
      |        acls          = [
      |          {
      |            http = {
      |              requests = [
      |                {
      |                  path-beg = "/"
      |                }
      |              ]
      |            }
      |          }
      |        ]
      |      }
      |    }
      |  }
      |}""".stripMargin
  bundleContentsConf should include(expectedContentsConf)
}