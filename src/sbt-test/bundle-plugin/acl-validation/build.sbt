import ByteConversions._
import com.lightbend.conductr.sbt.BundlePlugin._
import org.scalatest.Matchers._

lazy val root = (project in file(".")).enablePlugins(JavaAppPackaging)

name := "acl-validation-test"

version := "0.1.0-SNAPSHOT"

BundleKeys.nrOfCpus := 1.0
BundleKeys.memory := 64.MiB
BundleKeys.diskSpace := 10.MB
BundleKeys.roles := Set("web-server")

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
      "/regex1/[a|b|c]".r,
      "GET" -> "/regex2/[a|b|c]".r,
      "GET" -> "/regex3/[a|b|c]".r -> "/other-regex-3"
    )),
  RequestAcl(Tcp(9001, 9002)),
  RequestAcl(Udp(19001, 19002))
)

val checkBundleConfNotGenerated = taskKey[Unit]("")

checkBundleConfNotGenerated := {
  val bundleConfFile = (target in Bundle).value / "bundle" / "tmp" / "bundle.conf"
  bundleConfFile.exists() shouldBe false
}
