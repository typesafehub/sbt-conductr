import bintray.Keys._

lazy val sbtConductR = project.in(file("."))

name := "sbt-conductr"

crossScalaVersions := List(scalaVersion.value, "2.11.7")

libraryDependencies ++= List(
  Library.conductrClientLib,
  Library.jansi,
  Library.jline,
  Library.jodaTime,
  Library.scalactic,
  Library.scalaTest   % "test",
  Library.mockito     % "test"
)
addSbtPlugin(Library.sbtBundle)

resolvers ++= List(
  Resolver.typesafeReleases,
  Resolver.typesafeBintrayReleases
)

sbtPlugin := true

licenses += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0.html"))
publishMavenStyle := false
bintrayPublishSettings
repository in bintray := "sbt-plugins"
bintrayOrganization in bintray := Some("sbt-conductr")

scriptedSettings
scriptedLaunchOpts <+= version apply { v => s"-Dproject.version=$v" }
