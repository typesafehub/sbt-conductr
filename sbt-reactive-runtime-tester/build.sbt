import com.typesafe.sbt.SbtNativePackager._

lazy val root = (project in file(".")).enablePlugins(JavaAppPackaging, SbtReactiveRuntime)

name := "sbt-rr-tester"
version := "1.0.0"

scalaVersion := "2.11.4"

// RR

ReactiveRuntimeKeys.nrOfCpus := 1.0
ReactiveRuntimeKeys.memory := 10000000
ReactiveRuntimeKeys.diskSpace := 5000000
ReactiveRuntimeKeys.roles := Set("web-server")
