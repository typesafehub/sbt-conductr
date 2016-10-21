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
BundleKeys.diskSpace := 10.MB
BundleKeys.overrideEndpoints in Bundle := Some(Map("other" -> Endpoint("http", services = Set(URI("http://:9001")))))

/**
  * Check ports after 'sandbox run' command
  */
val checkPorts = taskKey[Unit]("Check that the specified ports are exposed to docker. Debug port should not be exposed.")
checkPorts := {
    val content = s"docker port cond-0".!!
    val expectedLines = Set(
      """9004/tcp -> 0.0.0.0:9004""",
      """9005/tcp -> 0.0.0.0:9005""",
      """9006/tcp -> 0.0.0.0:9006""",
      """9001/tcp -> 0.0.0.0:9001"""
    )

    expectedLines.foreach(line => content should include(line))
}

val checkConductrIsStopped = taskKey[Unit]("")
checkConductrIsStopped := {
  """docker ps --quiet --filter name=cond""".lines_! should have size 0
}