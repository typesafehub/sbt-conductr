import org.scalatest.Matchers._
import ByteConversions._

lazy val root = (project in file(".")).enablePlugins(JavaAppPackaging)

name := "sandbox-with-features"

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

/**
 * Check ports after 'sandbox run' command
 */
val checkPorts = taskKey[Unit]("Check that the specified ports are exposed to docker. Debug port should not be exposed.")
checkPorts := {
  val content = "docker port sandbox-haproxy".!!
  val expectedLines = Set(
    "3000/tcp -> 192.168.10.1:3000",
    "9999/tcp -> 192.168.10.1:9999"
  )

  expectedLines.foreach(line => content should include(line))
}
