import org.scalatest.Matchers._
import ByteConversions._

lazy val root = (project in file(".")).enablePlugins(PlayScala)

name := "custom-config"

version := "0.1.0-SNAPSHOT"
scalaVersion := "2.11.8"

BundleKeys.enableAcls := false
BundleKeys.memory := 64.MiB
BundleKeys.minMemoryCheckValue := 64.MiB

val checkBundleDist = taskKey[Unit]("check-bundle-dist-contents")
checkBundleDist := {
  val bundleContentsConf = IO.read((target in Bundle).value / "bundle" / "tmp" / "bundle.conf")
  val expectedContentsConf =
    """version              = "1"
      |name                 = "custom-config"
      |compatibilityVersion = "0"
      |system               = "custom-config"
      |systemVersion        = "0"
      |nrOfCpus             = 0.1
      |memory               = 67108864
      |diskSpace            = 200000000
      |roles                = ["web"]
      |components = {
      |  custom-config = {
      |    description      = "custom-config"
      |    file-system-type = "universal"
      |    start-command    = ["custom-config/bin/custom-config", "-J-Xms134217728", "-J-Xmx134217728"]
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

val checkConfigDist = taskKey[Unit]("check-config-dist-contents")
checkConfigDist := {
  val runtimeConf = IO.read((target in BundleConfiguration).value  / "stage" / "configuration" / "runtime-config.sh")
  val expectedRuntimeConf = """export APPLICATION_SECRET="thisismyapplicationsecret-pleasedonttellanyone"""".stripMargin
  runtimeConf should include(expectedRuntimeConf)
}