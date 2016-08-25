import org.scalatest.Matchers._
import ByteConversions._

lazy val root = (project in file(".")).enablePlugins(PlayScala)

name := "with-service-endpoint"

version := "0.1.0-SNAPSHOT"
scalaVersion := "2.11.8"

BundleKeys.enableAcls := false

val checkBundleDist = taskKey[Unit]("check-bundle-dist-contents")
checkBundleDist := {
  val bundleContentsConf = IO.read((target in Bundle).value / "bundle" / "tmp" / "bundle.conf")
  val expectedContentsConf =
    """version              = "1"
      |name                 = "with-service-endpoint"
      |compatibilityVersion = "0"
      |system               = "with-service-endpoint"
      |systemVersion        = "0"
      |nrOfCpus             = 0.1
      |memory               = 402653184
      |diskSpace            = 200000000
      |roles                = ["web"]
      |components = {
      |  with-service-endpoint = {
      |    description      = "with-service-endpoint"
      |    file-system-type = "universal"
      |    start-command    = ["with-service-endpoint/bin/with-service-endpoint", "-J-Xms134217728", "-J-Xmx134217728"]
      |    endpoints = {
      |      "web" = {
      |        bind-protocol = "http"
      |        bind-port     = 0
      |        services      = ["http://:9000"]
      |      }
      |    }
      |  }
      |}""".stripMargin
  bundleContentsConf should include(expectedContentsConf)
}