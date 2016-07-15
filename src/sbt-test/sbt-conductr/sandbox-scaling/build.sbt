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
BundleKeys.diskSpace := 10.MB

def resolveRunningContainers = """docker ps --quiet --filter name=cond""".lines_!

val checkContainers1 = taskKey[Unit]("Check that 1 container is running.")
checkContainers1 := {
  resolveRunningContainers should have size 1
}

val checkContainers2 = taskKey[Unit]("Check that 2 containers are running.")
checkContainers2 := {
  resolveRunningContainers should have size 2
}

val checkContainers3 = taskKey[Unit]("Check that 3 containers are running.")
checkContainers3 := {
  resolveRunningContainers should have size 3
}

val checkConductrIsStopped = taskKey[Unit]("")
checkConductrIsStopped := {
  resolveRunningContainers should have size 0
}