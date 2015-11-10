name := "root"

lazy val root = project
  .in(file("."))
  .aggregate(sbtConductr, client)

lazy val sbtConductr = project
  .in(file("sbt-conductr"))
  .dependsOn(client)

lazy val client = project
  .in(file("client"))

publishArtifact := false