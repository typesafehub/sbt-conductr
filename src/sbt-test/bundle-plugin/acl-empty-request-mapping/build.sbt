import ByteConversions._
import com.lightbend.conductr.sbt.BundlePlugin._
import org.scalatest.Matchers._

lazy val root = (project in file(".")).enablePlugins(JavaAppPackaging)

name := "acl-empty-request-mapping"

version := "0.1.0-SNAPSHOT"
BundleKeys.conductrTargetVersion := ConductrVersion.V2

javaOptions in Universal := Seq(
  "-J-Xms67108864",
  "-J-Xmx67108864"
)

BundleKeys.nrOfCpus := 0.1
BundleKeys.memory := 64.MiB
BundleKeys.diskSpace := 10.MB

BundleKeys.endpoints := Map(
  "empty-request-acl" -> Endpoint("http", 0, "empty-request-acl", RequestAcl())
)

val checkBundleConf = taskKey[Unit]("")

checkBundleConf := {
  val contents = IO.read((target in Bundle).value / "bundle" / "tmp" / "bundle.conf")
  val expectedContents = """|version              = "1"
                            |name                 = "acl-empty-request-mapping"
                            |compatibilityVersion = "0"
                            |system               = "acl-empty-request-mapping"
                            |systemVersion        = "0"
                            |nrOfCpus             = 0.1
                            |memory               = 67108864
                            |diskSpace            = 10000000
                            |roles                = ["web"]
                            |components = {
                            |  acl-empty-request-mapping = {
                            |    description      = "acl-empty-request-mapping"
                            |    file-system-type = "universal"
                            |    start-command    = ["acl-empty-request-mapping/bin/acl-empty-request-mapping", "-J-Xms67108864", "-J-Xmx67108864"]
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
