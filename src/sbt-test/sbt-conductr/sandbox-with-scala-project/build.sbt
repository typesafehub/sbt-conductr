import org.scalatest.Matchers._

name := "sandbox-with-scala-project"

version := "0.1.0-SNAPSHOT"

val checkConductrIsRunning = taskKey[Unit]("")
checkConductrIsRunning := s"sandbox ps -q".lines_! should have size 2
