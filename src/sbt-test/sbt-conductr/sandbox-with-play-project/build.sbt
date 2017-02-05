import org.scalatest.Matchers._

name := "sandbox-with-play-project"
version := "0.1.0-SNAPSHOT"

lazy val root = project
  .in(file("."))
  .enablePlugins(PlayScala)
  .settings(routesGenerator := InjectedRoutesGenerator)

BundleKeys.endpoints := Map("web" -> Endpoint("http", 0, Set(URI("http://:9000"))))

val checkConductrIsRunning = taskKey[Unit]("")
checkConductrIsRunning := s"sandbox ps -q".lines_! should have size 2
