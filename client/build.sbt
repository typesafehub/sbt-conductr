import bintray.Keys._

name := "sbt-conductr-client"

libraryDependencies ++= List(
  Library.akkaContribExtra,
  Library.akkaHttp,
  Library.playJson,
  Library.scalactic,
  Library.akkaTestkit % "test",
  Library.scalaTest   % "test"
)

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
bintrayOrganization in bintray := Some("sbt-conductr-client")

scriptedSettings
scriptedLaunchOpts <+= version apply { v => s"-Dproject.version=$v" }
