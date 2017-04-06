import ByteConversions._
import com.lightbend.conductr.sbt.BundlePlugin._
import org.scalatest.Matchers._

lazy val root = (project in file(".")).enablePlugins(JavaAppPackaging)

name := "acl-empty-request-mapping"

version := "0.1.0-SNAPSHOT"

javaOptions in Universal := Seq(
  "-J-Xms67108864",
  "-J-Xmx67108864"
)

BundleKeys.nrOfCpus := 0.1
BundleKeys.memory := 64.MiB
BundleKeys.minMemoryCheckValue := 64.MiB
BundleKeys.diskSpace := 10.MB

BundleKeys.endpoints := Map(
  "empty-request-acl-with-service-name" -> Endpoint("http", 0, "service-name", RequestAcl()),
  "empty-request-acl" -> Endpoint("http", 0, RequestAcl()),
  "service-name" -> Endpoint("http", 0, "service-name"),
  "protocol-and-port" -> Endpoint("tcp", 5555),
  "protocol" -> Endpoint("tcp")
)

val checkBundleConf = taskKey[Unit]("")

checkBundleConf := {
  val contents = IO.read((target in Bundle).value / "bundle" / "tmp" / "bundle.conf")
  val expectedContents = """|version              = "1"
                            |name                 = "acl-empty-request-mapping"
                            |compatibilityVersion = "0"
                            |tags                 = ["0.1.0-SNAPSHOT"]
                            |annotations          = {}
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
                            |      "protocol-and-port" = {
                            |        bind-protocol = "tcp"
                            |        bind-port     = 5555
                            |        services      = []
                            |      },
                            |      "empty-request-acl-with-service-name" = {
                            |        bind-protocol = "http"
                            |        bind-port     = 0
                            |        service-name  = "service-name"
                            |        services      = []
                            |      },
                            |      "protocol" = {
                            |        bind-protocol = "tcp"
                            |        bind-port     = 0
                            |        services      = []
                            |      },
                            |      "empty-request-acl" = {
                            |        bind-protocol = "http"
                            |        bind-port     = 0
                            |        services      = []
                            |      },
                            |      "service-name" = {
                            |        bind-protocol = "http"
                            |        bind-port     = 0
                            |        service-name  = "service-name"
                            |        services      = []
                            |      }
                            |    }
                            |  }
                            |}""".stripMargin
  contents should include(expectedContents)
}
