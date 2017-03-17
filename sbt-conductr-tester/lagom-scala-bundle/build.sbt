scalaVersion in ThisBuild := "2.11.8"
version in ThisBuild := "0.1.0-SNAPSHOT"

val macwire = "com.softwaremill.macwire" %% "macros" % "2.2.5" % "provided"

lazy val `lagom-service-api` = (project in file("lagom-service-api"))
  .settings(libraryDependencies += lagomScaladslApi)

lazy val `lagom-service-impl` = (project in file("lagom-service-impl"))
  .enablePlugins(LagomScala)
  .settings(libraryDependencies += macwire)
  .dependsOn(`lagom-service-api`)

lazy val `play-service` = (project in file("play-service"))
    .enablePlugins(PlayJava, LagomPlay)
    .settings(
      routesGenerator := InjectedRoutesGenerator
    )