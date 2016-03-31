import bintray.Keys._

lazy val sbtConductR = project.in(file("."))

name := "sbt-conductr"

crossScalaVersions := List(scalaVersion.value, "2.11.7")

addSbtPlugin(Library.sbtBundle)

sbtPlugin := true

licenses += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0.html"))
publishMavenStyle := false
bintrayPublishSettings
repository in bintray := "sbt-plugins"
bintrayOrganization in bintray := Some("sbt-conductr")

scriptedSettings
scriptedLaunchOpts <+= version apply { v => s"-Dproject.version=$v" }
