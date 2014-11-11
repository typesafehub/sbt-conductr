lazy val sbtReactiveRuntime = project.in(file("."))

name := "sbt-reactive-runtime"

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

resolvers += Resolver.typesafeInternalReleases

sbtPlugin := true

publishMavenStyle := false
publishTo := {
  if (isSnapshot.value)
    Some(Classpaths.sbtPluginSnapshots)
  else
    Some(Classpaths.sbtPluginReleases)
}

scriptedSettings
scriptedLaunchOpts <+= version.apply { v => s"-Dproject.version=$v" }
