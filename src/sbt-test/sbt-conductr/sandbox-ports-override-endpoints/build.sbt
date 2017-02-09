import org.scalatest.Matchers._
import ByteConversions._

lazy val root = (project in file(".")).enablePlugins(JavaAppPackaging)

name := "sandbox-ports-override-endpoints"

version := "0.1.0-SNAPSHOT"

javaOptions in Universal := Seq(
  "-J-Xms64m",
  "-J-Xmx64m"
)

// ConductR bundle keys
BundleKeys.nrOfCpus := 0.1
BundleKeys.memory := 64.MiB
BundleKeys.minMemoryCheckValue := 64.MiB
BundleKeys.diskSpace := 10.MB
BundleKeys.overrideEndpoints in Bundle := Some(Map("other" -> Endpoint("http", services = Set(URI("http://:9001")))))

/**
  * Check ports after 'sandbox run' command
  */
val checkPorts = taskKey[Unit]("Check that the specified ports are exposed to docker. Debug port should not be exposed.")
checkPorts := {
    val content = s"docker port sandbox-haproxy".!!
    val expectedLines = Set(
      """9001/tcp -> 192.168.10.1:9001"""
    )

    expectedLines.foreach(line => content should include(line))
}
