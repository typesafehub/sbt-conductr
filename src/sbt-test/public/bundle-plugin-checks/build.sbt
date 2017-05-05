import ByteConversions._
import com.lightbend.conductr.sbt.BundlePlugin._
import org.scalatest.Matchers._
import scala.concurrent.duration._

lazy val root = (project in file(".")).enablePlugins(JavaAppPackaging)

name := "checks"

version := "0.1.0-SNAPSHOT"

javaOptions in Universal := Seq(
  "-J-Xms67108864",
  "-J-Xmx67108864"
)

BundleKeys.nrOfCpus := 0.1
BundleKeys.memory := 64.MiB
BundleKeys.minMemoryCheckValue := 64.MiB
BundleKeys.diskSpace := 10.MB
BundleKeys.checkInitialDelay := 1400.milliseconds
BundleKeys.checks := Seq(uri("$CHECKS_HOST?retry-count=5&retry-delay=3"))

val checkBundleConf = taskKey[Unit]("")

checkBundleConf := {
  val contents = IO.read(target.value / "bundle" / "bundle" / "tmp" / "bundle.conf")
  val expectedContents = """|version              = "1"
                            |name                 = "checks"
                            |compatibilityVersion = "0"
                            |tags                 = ["0.1.0-SNAPSHOT"]
                            |annotations          = {}
                            |system               = "checks"
                            |systemVersion        = "0"
                            |nrOfCpus             = 0.1
                            |memory               = 67108864
                            |diskSpace            = 10000000
                            |roles                = ["web"]
                            |components = {
                            |  checks = {
                            |    description      = "checks"
                            |    file-system-type = "universal"
                            |    start-command    = ["checks/bin/checks", "-J-Xms67108864", "-J-Xmx67108864"]
                            |    endpoints = {
                            |      "web" = {
                            |        bind-protocol = "http"
                            |        bind-port     = 0
                            |        service-name  = "web"
                            |        services      = []
                            |      }
                            |    }
                            |  }
                            |}
                            |components = {
                            |  bundle-status = {
                            |    description      = "Status check for the bundle component"
                            |    file-system-type = "universal"
                            |    start-command    = ["check", "--initial-delay", "1", "$CHECKS_HOST?retry-count=5&retry-delay=3"]
                            |    endpoints        = {}
                            |  }
                            |}""".stripMargin
  contents should include(expectedContents)
}
