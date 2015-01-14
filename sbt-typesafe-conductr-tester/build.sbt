lazy val root = project
  .in(file("."))
  .enablePlugins(JavaAppPackaging, SbtTypesafeConductR)

name := "sbt-typesafe-conductr-tester"
version := "1.0.0"
scalaVersion := "2.11.4"

// ConductR

ConductRKeys.nrOfCpus := 1.0
ConductRKeys.memory := 10000000
ConductRKeys.diskSpace := 5000000
ConductRKeys.roles := Set("web-server")
BundleKeys.endpoints := Map.empty
