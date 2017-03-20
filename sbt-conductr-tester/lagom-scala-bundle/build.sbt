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
  .enablePlugins(PlayScala, LagomPlay)
  .settings(
    routesGenerator := InjectedRoutesGenerator,
    // Need to explicitly add the dependency to lagom-client 1.3.1 because 1.3.1-RC1 is being pulled in
    libraryDependencies ++= Seq(
      "com.lightbend.lagom" %% "lagom-scaladsl-client" % "1.3.1",
      macwire)
  )
  .dependsOn(`lagom-service-api`)
