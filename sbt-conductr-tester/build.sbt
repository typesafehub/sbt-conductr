import ByteConversions._

lazy val root = project
  .in(file("."))
  .enablePlugins(JavaAppPackaging)

name := "sbt-conductr-tester"
version := "1.0.0"
scalaVersion := "2.11.6"

// ConductR

BundleKeys.nrOfCpus := 1.0
BundleKeys.memory := 10.MiB
BundleKeys.diskSpace := 5.MB
BundleKeys.endpoints := Map.empty

BundleKeys.configurationName := "web-server"