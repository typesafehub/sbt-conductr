import org.scalatest.Matchers._

scalaVersion in ThisBuild := "2.11.7"
version in ThisBuild := "0.1.0-SNAPSHOT"

lazy val `lagom-service-api` = (project in file("lagom-service-api"))
  .settings(libraryDependencies += lagomJavadslApi)

lazy val `lagom-service-impl` = (project in file("lagom-service-impl"))
  .enablePlugins(LagomJava)
  .dependsOn(`lagom-service-api`)

// Test assertions
val verifyConductInfo = taskKey[Unit]("")
verifyConductInfo := {
  val output = conductInfo()
  output should include("lagom-service-impl")
  output should include("cassandra")
}

def conductInfo(): String =
  (Process("conduct info") #| Process(Seq("awk", "{print $2}"))).!!
