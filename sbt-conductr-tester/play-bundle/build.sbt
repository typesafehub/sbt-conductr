import ByteConversions._

lazy val root = project
  .in(file("."))
  .enablePlugins(PlayScala)

name := "play-bundle-tester"
version := "1.0.0"
scalaVersion := "2.11.6"