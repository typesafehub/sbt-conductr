import org.scalatest.Matchers._
import ByteConversions._

lazy val root = (project in file(".")).enablePlugins(PlayScala)

name := "play24"

version := "0.1.0-SNAPSHOT"
scalaVersion := "2.11.8"

BundleKeys.conductrTargetVersion := ConductrVersion.V1_2

val checkBundleDist = taskKey[Unit]("check-bundle-dist-contents")
checkBundleDist := {
  val bundleContentsConf = IO.read((target in Bundle).value / "bundle" / "tmp" / "bundle.conf")
  val expectedContentsConf =
    """version              = "1"
      |name                 = "play24"
      |compatibilityVersion = "0"
      |system               = "play24"
      |systemVersion        = "0"
      |nrOfCpus             = 1.0
      |memory               = 134217728
      |diskSpace            = 200000000
      |roles                = ["web"]
      |components = {
      |  play24 = {
      |    description      = "play24"
      |    file-system-type = "universal"
      |    start-command    = ["play24/bin/play24", "-J-Xms134217728", "-J-Xmx134217728", "-Dhttp.address=$PLAY24_BIND_IP", "-Dhttp.port=$PLAY24_BIND_PORT"]
      |    endpoints = {
      |      "play24" = {
      |        bind-protocol = "http"
      |        bind-port     = 0
      |        service-name  = "play24"
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