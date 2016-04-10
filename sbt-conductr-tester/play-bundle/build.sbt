name := "play-bundle-tester"
version := "1.0.0"
scalaVersion := "2.11.8"

lazy val root = project
  .in(file("."))
  .enablePlugins(PlayScala)
  .settings(routesGenerator := InjectedRoutesGenerator)
