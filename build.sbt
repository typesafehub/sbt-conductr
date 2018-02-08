import bintray.Keys._
import scalariform.formatter.preferences._
import com.typesafe.sbt.SbtScalariform
import com.typesafe.sbt.SbtScalariform.ScalariformKeys
import ReleaseTransformations._

lazy val `sbt-conductr` = project.in(file("."))

sbtPlugin := true

name := "sbt-conductr"
organization := "com.lightbend.conductr"

crossSbtVersions := Vector(Version.sbt013, Version.sbt10)

// Scala settings
scalaVersion := (CrossVersion partialVersion (sbtVersion in pluginCrossBuild).value match {
  case Some((0, 13)) => Version.scala210
  case Some((1, _))  => Version.scala212
  case _             => sys error s"Unhandled sbt version ${(sbtVersion in pluginCrossBuild).value}"
})
scalacOptions ++= List(
  "-unchecked",
  "-deprecation",
  "-language:_",
  "-target:jvm-1.6",
  "-encoding", "UTF-8"
)
unmanagedSourceDirectories in Compile := List((scalaSource in Compile).value)
unmanagedSourceDirectories in Test := List((scalaSource in Test).value)

// Plugin dependencies
libraryDependencies += {
  val currentSbtVersion = (sbtBinaryVersion in pluginCrossBuild).value
  Defaults.sbtPluginExtra(Library.nativePackager, currentSbtVersion, scalaBinaryVersion.value)
}

// Library dependencies
libraryDependencies ++= List(
  Library.config,
  Library.playJson,
  Library.scalaTest,
  Library.sjsonnew
)

// Scalariform settings
SbtScalariform.scalariformSettings
ScalariformKeys.preferences := ScalariformKeys.preferences.value
  .setPreference(AlignSingleLineCaseStatements, true)
  .setPreference(AlignSingleLineCaseStatements.MaxArrowIndent, 100)
  .setPreference(DoubleIndentClassDeclaration, true)
  .setPreference(DanglingCloseParenthesis, Force)

// Release + Bintray settings
licenses += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0.html"))
publishMavenStyle := false
bintrayPublishSettings
repository in bintray := "sbt-plugins"
bintrayOrganization in bintray := Some("sbt-conductr")

releaseCrossBuild := false
releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  releaseStepCommand("^test"),
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  releaseStepCommand("^publish"),
  setNextVersion,
  commitNextVersion,
  pushChanges
)

// Scripted test settings
scriptedSettings
scriptedLaunchOpts += s"-Dproject.version=${version.value}"

// Test aliases
addCommandAlias("test-013", s";^^${Version.sbt013};test;scripted public/*")
// We only test a subset for 1.0, since many tests use older Play/Lagom versions
addCommandAlias("test-10", s";^^${Version.sbt10};test;scripted public/bundle-plugin-*")

