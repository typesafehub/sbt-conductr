import bintray.Keys._

lazy val sbtTypesafeConductR = project.in(file("."))

name := "sbt-typesafe-conductr"

libraryDependencies ++= List(
  Library.akkaContribExtra,
  Library.akkaHttp,
  Library.jansi,
  Library.jline,
  Library.playJson,
  Library.scalactic,
  Library.akkaTestkit % "test",
  Library.scalaTest   % "test"
)
addSbtPlugin(Library.sbtBundle)

resolvers ++= List(
  Resolver.akkaContribExtra,
  Resolver.patriknw,
  Resolver.typesafeReleases
)

sbtPlugin := true

licenses += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0.html"))
publishMavenStyle := false
bintrayPublishSettings
repository in bintray := "sbt-plugins"
bintrayOrganization in bintray := Some("sbt-typesafe-conductr")

scriptedSettings
scriptedLaunchOpts <+= version apply { v => s"-Dproject.version=$v" }
