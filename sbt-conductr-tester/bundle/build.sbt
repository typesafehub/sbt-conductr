import ByteConversions._

// Note that this bundle can not be started (`conduct run`) because the program does not signal that it has been started.

lazy val root = project
  .in(file("."))
  .enablePlugins(JavaAppPackaging)

name := "bundle"
version := "1.0.0"
scalaVersion := "2.11.8"

// ConductR
BundleKeys.nrOfCpus := 1.0
BundleKeys.memory := 64.MiB
BundleKeys.diskSpace := 10.MB
BundleKeys.endpoints := Map("web" -> Endpoint("http", 0, Set(URI("http://:9001"))))

BundleKeys.configurationName := "web-server"
