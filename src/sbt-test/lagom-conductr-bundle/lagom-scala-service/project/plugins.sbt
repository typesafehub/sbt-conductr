addSbtPlugin("com.lightbend.lagom" % "lagom-sbt-plugin" % "1.3.0-SNAPSHOT")

addSbtPlugin("com.lightbend.conductr" % "sbt-conductr" % sys.props("project.version"))
libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.6"
