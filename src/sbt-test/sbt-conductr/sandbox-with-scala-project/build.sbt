import org.scalatest.Matchers._

name := "sandbox-with-scala-project"

version := "0.1.0-SNAPSHOT"

val checkConductrIsRunning = taskKey[Unit]("")
checkConductrIsRunning := s"docker ps -q -f name=cond-".lines_! should have size 1

val checkConductrIsStopped = taskKey[Unit]("")
checkConductrIsStopped := """docker ps --quiet --filter name=cond""".lines_! should have size 0