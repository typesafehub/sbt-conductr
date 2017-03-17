import ByteConversions._
import com.lightbend.conductr.sbt.BundlePlugin._
import org.scalatest.Matchers._

lazy val root = (project in file(".")).enablePlugins(JavaAppPackaging)

name := "basic"

version := "0.1.0-SNAPSHOT"

javaOptions in Universal := Seq(
  "-J-Xms67108864",
  "-J-Xmx67108864"
)

BundleKeys.nrOfCpus := 0.1
BundleKeys.memory := 64.MiB
BundleKeys.minMemoryCheckValue := 64.MiB
BundleKeys.diskSpace := 10.MB

BundleKeys.endpoints += "other" -> Endpoint("http", 0, Set(URI("http://:9001/simple-test")))
BundleKeys.endpoints += "akka-remote" -> Endpoint("tcp")
BundleKeys.endpoints += "extras" -> Endpoint("http", 0, "ping-service",
  RequestAcl(
    Http(
      "/bar-1",
      "GET" -> "/bar-2",
      "GET" -> "/bar-3" -> "/other-bar-3",
      "^/beg-1".r,
      "GET" -> "^/beg-2".r,
      "GET" -> "^/beg-3".r -> "/other-beg-3",
      "^/beg-4".r -> "/other-beg-4",
      "/regex1/[a|b|c]".r,
      "GET" -> "/regex2/[a|b|c]".r,
      "GET" -> "/regex3/[a|b|c]".r -> "/other-regex-3",
      "/regex4/[a|b|c]".r -> "/other-regex-4"
    )),
  RequestAcl(Tcp(9001, 9002)),
  RequestAcl(Udp(19001, 19002))
)

val checkBundleConf = taskKey[Unit]("")

checkBundleConf := {
  val contents = IO.read((target in Bundle).value / "bundle" / "tmp" / "bundle.conf")
  val expectedContents = """|version              = "1"
                            |name                 = "basic"
                            |compatibilityVersion = "0"
                            |system               = "basic"
                            |systemVersion        = "0"
                            |nrOfCpus             = 0.1
                            |memory               = 67108864
                            |diskSpace            = 10000000
                            |roles                = ["web"]
                            |components = {
                            |  basic = {
                            |    description      = "basic"
                            |    file-system-type = "universal"
                            |    start-command    = ["basic/bin/basic", "-J-Xms67108864", "-J-Xmx67108864"]
                            |    endpoints = {
                            |      "web" = {
                            |        bind-protocol = "http"
                            |        bind-port     = 0
                            |        service-name  = "web"
                            |        services      = []
                            |      },
                            |      "other" = {
                            |        bind-protocol = "http"
                            |        bind-port     = 0
                            |        services      = ["http://:9001/simple-test"]
                            |      },
                            |      "akka-remote" = {
                            |        bind-protocol = "tcp"
                            |        bind-port     = 0
                            |        services      = []
                            |      },
                            |      "extras" = {
                            |        bind-protocol = "http"
                            |        bind-port     = 0
                            |        service-name  = "ping-service"
                            |        acls          = [
                            |          {
                            |            http = {
                            |              requests = [
                            |                {
                            |                  path = "/bar-1"
                            |                },
                            |                {
                            |                  path = "/bar-2"
                            |                  method = "GET"
                            |                },
                            |                {
                            |                  path = "/bar-3"
                            |                  method = "GET"
                            |                  rewrite = "/other-bar-3"
                            |                },
                            |                {
                            |                  path-beg = "/beg-1"
                            |                },
                            |                {
                            |                  path-beg = "/beg-2"
                            |                  method = "GET"
                            |                },
                            |                {
                            |                  path-beg = "/beg-3"
                            |                  method = "GET"
                            |                  rewrite = "/other-beg-3"
                            |                },
                            |                {
                            |                  path-beg = "/beg-4"
                            |                  rewrite = "/other-beg-4"
                            |                },
                            |                {
                            |                  path-regex = "/regex1/[a|b|c]"
                            |                },
                            |                {
                            |                  path-regex = "/regex2/[a|b|c]"
                            |                  method = "GET"
                            |                },
                            |                {
                            |                  path-regex = "/regex3/[a|b|c]"
                            |                  method = "GET"
                            |                  rewrite = "/other-regex-3"
                            |                },
                            |                {
                            |                  path-regex = "/regex4/[a|b|c]"
                            |                  rewrite = "/other-regex-4"
                            |                }
                            |              ]
                            |            }
                            |          },
                            |          {
                            |            tcp = {
                            |              requests = [9001, 9002]
                            |            }
                            |          },
                            |          {
                            |            udp = {
                            |              requests = [19001, 19002]
                            |            }
                            |          }
                            |        ]
                            |      }
                            |    }
                            |  }
                            |}""".stripMargin
  contents should include(expectedContents)
}
