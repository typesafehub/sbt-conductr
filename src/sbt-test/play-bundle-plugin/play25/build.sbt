import org.scalatest.Matchers._
import ByteConversions._

lazy val root = (project in file(".")).enablePlugins(PlayScala)

name := "play25"

version := "0.1.0-SNAPSHOT"
scalaVersion := "2.11.8"

val checkBundleDist = taskKey[Unit]("check-bundle-dist-contents")
checkBundleDist := {
  val bundleContentsConf = IO.read((target in Bundle).value / "bundle" / "tmp" / "bundle.conf")
  val expectedContentsConf =
    """version              = "1.1.0"
      |name                 = "play25"
      |compatibilityVersion = "0"
      |system               = "play25"
      |systemVersion        = "0"
      |nrOfCpus             = 1.0
      |memory               = 134217728
      |diskSpace            = 200000000
      |roles                = ["web"]
      |components = {
      |  play25 = {
      |    description      = "play25"
      |    file-system-type = "universal"
      |    start-command    = ["play25/bin/play25", "-J-Xms134217728", "-J-Xmx134217728"]
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