import org.scalatest.Matchers._
import ByteConversions._

name := "sandbox-scaling"

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

val checkInstances1 = taskKey[Unit]("Check that 2 instances of cores and agents are running.")
checkInstances1 := {
  resolveRunningContainers should have size 2
}

val checkInstances2 = taskKey[Unit]("Check that 4 instances of cores and agents are running.")
checkInstances2 := {
  resolveRunningContainers should have size 4
}

val checkInstances3 = taskKey[Unit]("Check that 6 instances of cores and agents are running.")
checkInstances3 := {
  resolveRunningContainers should have size 6
}
