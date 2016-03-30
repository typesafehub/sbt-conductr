import org.scalatest.Matchers._
import ByteConversions._

lazy val root = (project in file(".")).enablePlugins(JavaAppPackaging)

name := "sandbox-with-features"

version := "0.1.0-SNAPSHOT"

// ConductR bundle keys
BundleKeys.nrOfCpus := 1.0
BundleKeys.memory := 64.MiB
BundleKeys.diskSpace := 10.MB

/**
 * Check ports after 'sandbox run' command
 */
val checkPorts = taskKey[Unit]("Check that the specified ports are exposed to docker. Debug port should not be exposed.")
checkPorts := {
  val content = "docker port cond-0".!!
  val expectedLines = Set(
    """9999/tcp -> 0.0.0.0:9999""",
    """9200/tcp -> 0.0.0.0:9200"""
  )

  expectedLines.foreach(line => content should include(line))
}

val checkEnvs = taskKey[Unit]("Check if environment variables for features are set.")
checkEnvs := {
  val content = "docker inspect --format='{{.Config.Env}}' cond-0".!!
  val expectedContent = "CONDUCTR_FEATURES=visualization,logging"
  content should include(expectedContent)
}

val checkConductRIsStopped = taskKey[Unit]("")
checkConductRIsStopped := {
  """docker ps --quiet --filter name=cond""".lines_! should have size 0
}