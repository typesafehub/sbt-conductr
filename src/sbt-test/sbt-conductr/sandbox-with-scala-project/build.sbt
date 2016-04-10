import org.scalatest.Matchers._

name := "sandbox-with-scala-project"

version := "0.1.0-SNAPSHOT"

val checkConductRIsRunning = taskKey[Unit]("")
checkConductRIsRunning := s"docker ps -q -f name=cond-".lines_! should have size 1

val checkConductRIsStopped = taskKey[Unit]("")
checkConductRIsStopped := """docker ps --quiet --filter name=cond""".lines_! should have size 0