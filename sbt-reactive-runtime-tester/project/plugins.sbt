lazy val root = Project("plugins", file(".")).dependsOn(plugin)
lazy val plugin = file("../").getCanonicalFile.toURI

addSbtPlugin("com.typesafe.sbt" % "sbt-bundle" % "0.1.0")
