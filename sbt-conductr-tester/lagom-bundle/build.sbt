import ByteConversions._

scalaVersion in ThisBuild := "2.11.7"
version in ThisBuild := "0.1.0-SNAPSHOT"

lazy val simpleApi = (project in file("simple-api"))
  .settings(libraryDependencies += lagomJavadslApi)

lazy val simpleImpl = (project in file("simple-impl"))
  .enablePlugins(LagomJava)
  .dependsOn(simpleApi)