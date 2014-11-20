lazy val root = project
  .in(file("."))
  .enablePlugins(JavaAppPackaging, SbtReactiveRuntime)

name := "sbt-reactive-runtime-tester"
version := "1.0.0"
scalaVersion := "2.11.4"

// ReactiveRuntime

ReactiveRuntimeKeys.nrOfCpus := 1.0
ReactiveRuntimeKeys.memory := 10000000
ReactiveRuntimeKeys.diskSpace := 5000000
ReactiveRuntimeKeys.roles := Set("web-server")
