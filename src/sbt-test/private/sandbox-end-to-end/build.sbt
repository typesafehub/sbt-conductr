import org.scalatest.Matchers._
import ByteConversions._

lazy val root = (project in file(".")).enablePlugins(JavaAppPackaging)

name := "sandbox-end-to-end"

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

def resolveRunningContainers = """sandbox ps -q""".lines_!

val checkInstances2 = taskKey[Unit]("Check that 4 instances of cores and agents are running.")
checkInstances2 := {
  resolveRunningContainers should have size 4
}

/**
 * Check ports after 'sandbox run' command
 */
val checkPorts = taskKey[Unit]("Check that the specified ports are exposed to HAProxy. Debug port should not be exposed.")
checkPorts := {
  val content = "docker port sandbox-haproxy".!!
  val expectedLines = Set(
    "3000/tcp -> 192.168.10.1:3000",
    "9999/tcp -> 192.168.10.1:9999",
    "2551/tcp -> 192.168.10.1:2551",
    "1111/tcp -> 192.168.10.1:1111",
    "2222/tcp -> 192.168.10.1:2222"
  )

  expectedLines.foreach(line => content should include(line))
}
