import com.typesafe.sbt.SbtNativePackager._

lazy val root = (project in file(".")).enablePlugins(JavaAppPackaging, SbtReactiveRuntime)

name := "sbt-rr-tester"
version := "1.0.0"

scalaVersion := "2.11.4"

// RR

ReactiveRuntimeKeys.cpusRequired := 1.0
ReactiveRuntimeKeys.memoryRequired := 10000000
ReactiveRuntimeKeys.totalFileSize := 5000000
ReactiveRuntimeKeys.roles := Set("web-server")
