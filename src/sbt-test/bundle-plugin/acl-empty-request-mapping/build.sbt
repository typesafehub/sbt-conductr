import ByteConversions._
import com.lightbend.conductr.sbt.BundlePlugin._
import org.scalatest.Matchers._

lazy val root = (project in file(".")).enablePlugins(JavaAppPackaging)

name := "simple-test"

version := "0.1.0-SNAPSHOT"
BundleKeys.bundleConfVersion := BundleConfVersions.V_1_2_0

BundleKeys.nrOfCpus := 1.0
BundleKeys.memory := 64.MiB
BundleKeys.diskSpace := 10.MB
BundleKeys.roles := Set("web-server")

BundleKeys.endpoints := Map(
  "empty-request-acl" -> Endpoint("http", 0, "empty-request-acl", RequestAcl())
)

val checkBundleConf = taskKey[Unit]("")

checkBundleConf := {
  val contents = IO.read((target in Bundle).value / "bundle" / "tmp" / "bundle.conf")
  val expectedContents = """|version              = "1.2.0"
                            |name                 = "simple-test"
                            |compatibilityVersion = "0"
                            |system               = "simple-test"
                            |systemVersion        = "0"
                            |nrOfCpus             = 1.0
                            |memory               = 67108864
                            |diskSpace            = 10000000
                            |roles                = ["web-server"]
                            |components = {
                            |  simple-test = {
                            |    description      = "simple-test"
                            |    file-system-type = "universal"
                            |    start-command    = ["simple-test/bin/simple-test", "-J-Xms67108864", "-J-Xmx67108864"]
                            |    endpoints = {
                            |      "empty-request-acl" = {
                            |        bind-protocol = "http"
                            |        bind-port     = 0
                            |        service-name  = "empty-request-acl"
                            |        acls          = [
                            |
                            |        ]
                            |      }
                            |    }
                            |  }
                            |}""".stripMargin
  contents should include(expectedContents)
}
