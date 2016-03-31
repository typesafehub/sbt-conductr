import ByteConversions._
import com.lightbend.conductr.sbt.BundlePlugin._
import org.scalatest.Matchers._

name := "simple-test"

version := "0.1.0-SNAPSHOT"

BundleKeys.nrOfCpus := 1.0
BundleKeys.memory := 64.MiB
BundleKeys.diskSpace := 10.MB

BundleKeys.nrOfCpus in BundleConfiguration := 11.0

lazy val Backend = config("backend").extend(Bundle)
BundlePlugin.bundleSettings(Backend)
inConfig(Backend)(Seq(
  BundleKeys.nrOfCpus := 2.0
))

lazy val BackendConfiguration = config("backend-config").extend(BundleConfiguration)
BundlePlugin.configurationSettings(BackendConfiguration)
inConfig(BackendConfiguration)(Seq(
  BundleKeys.nrOfCpus := 12.0
))

lazy val root = (project in file("."))
  .enablePlugins(JavaAppPackaging)
  .configs(Backend, BackendConfiguration)


val checkBundleConf = taskKey[Unit]("")

checkBundleConf := {
  val contents = IO.read((target in Backend).value / "backend" / "tmp" / "bundle.conf")
  val expectedContents = """|version              = "1.1.0"
                            |name                 = "simple-test"
                            |compatibilityVersion = "0"
                            |system               = "simple-test"
                            |systemVersion        = "0"
                            |nrOfCpus             = 2.0
                            |memory               = 67108864
                            |diskSpace            = 10000000
                            |roles                = ["web"]
                            |components = {
                            |  simple-test = {
                            |    description      = "simple-test"
                            |    file-system-type = "universal"
                            |    start-command    = ["simple-test/bin/simple-test", "-J-Xms67108864", "-J-Xmx67108864"]
                            |    endpoints = {
                            |      "web" = {
                            |        bind-protocol = "http"
                            |        bind-port     = 0
                            |        services      = ["http://:9000"]
                            |      }
                            |    }
                            |  }
                            |}""".stripMargin
  contents should include(expectedContents)
}

val checkBundleConfigConf = taskKey[Unit]("")

checkBundleConfigConf := {
  val contents = IO.read((target in BackendConfiguration).value / "stage" / "backend-config" / "bundle.conf")
  val expectedContents =
    """nrOfCpus             = 12.0
      |components = {
      |  simple-test = {
      |  }
      |}""".stripMargin
  contents should include(expectedContents)
}