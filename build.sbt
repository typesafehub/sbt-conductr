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

publishMavenStyle := false
publishTo := {
  if (isSnapshot.value)
    Some(Classpaths.sbtPluginSnapshots)
  else
    Some(Classpaths.sbtPluginReleases)
}

scriptedSettings
scriptedLaunchOpts <+= version(v => s"-Dproject.version=$v")
