import org.scalatest.Matchers._
import ByteConversions._

lazy val root = (project in file(".")).enablePlugins(JavaAppPackaging, PlayScala)

name := "play23"

version := "0.1.0-SNAPSHOT"
scalaVersion := "2.11.8"

BundleKeys.conductrTargetVersion := ConductrVersion.V1_2

val checkBundleDist = taskKey[Unit]("check-bundle-dist-contents")
checkBundleDist := {
  val bundleContentsConf = IO.read((target in Bundle).value / "bundle" / "tmp" / "bundle.conf")
  val expectedContentsConf =
    """version              = "1"
      |name                 = "play23"
      |compatibilityVersion = "0"
      |system               = "play23"
      |systemVersion        = "0"
      |nrOfCpus             = 0.1
      |memory               = 402653184
      |diskSpace            = 200000000
      |roles                = ["web"]
      |components = {
      |  play23 = {
      |    description      = "play23"
      |    file-system-type = "universal"
      |    start-command    = ["play23/bin/play23", "-J-Xms134217728", "-J-Xmx134217728"]
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