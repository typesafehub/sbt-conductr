import ByteConversions._
import com.lightbend.conductr.sbt.BundlePlugin._
import org.scalatest.Matchers._

lazy val root = (project in file(".")).enablePlugins(JavaAppPackaging)

name := "acl-no-service-name"

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
  "ep2" -> Endpoint("tcp", 0, RequestAcl(Tcp(17777)))
)

val checkBundleConf = taskKey[Unit]("")

checkBundleConf := {
  val contents = IO.read((target in Bundle).value / "bundle" / "tmp" / "bundle.conf")
  val expectedContents = """|version              = "1"
                            |name                 = "acl-no-service-name"
                            |compatibilityVersion = "0"
                            |system               = "acl-no-service-name"
                            |systemVersion        = "0"
                            |nrOfCpus             = 0.1
                            |memory               = 67108864
                            |diskSpace            = 10000000
                            |roles                = ["web"]
                            |components = {
                            |  acl-no-service-name = {
                            |    description      = "acl-no-service-name"
                            |    file-system-type = "universal"
                            |    start-command    = ["acl-no-service-name/bin/acl-no-service-name", "-J-Xms67108864", "-J-Xmx67108864"]
                            |    endpoints = {
                            |      "ep2" = {
                            |        bind-protocol = "tcp"
                            |        bind-port     = 0
                            |        acls          = [
                            |          {
                            |            tcp = {
                            |              requests = [17777]
                            |            }
                            |          }
                            |        ]
                            |      }
                            |    }
                            |  }
                            |}""".stripMargin
  contents should include(expectedContents)
}
