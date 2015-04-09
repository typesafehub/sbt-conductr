import ByteConversions._

lazy val root = project
  .in(file("."))
  .enablePlugins(JavaAppPackaging, SbtTypesafeConductR)

name := "sbt-typesafe-conductr-tester"
version := "1.0.0"
scalaVersion := "2.11.6"

// ConductR

BundleKeys.nrOfCpus := 1.0
BundleKeys.memory := 10.MiB
BundleKeys.diskSpace := 5.MB
BundleKeys.roles := Set("web-server")
BundleKeys.endpoints := Map.empty
