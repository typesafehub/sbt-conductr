addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.5.1")
addSbtPlugin("com.lightbend.conductr" % "sbt-conductr" % sys.props("project.version"))
libraryDependencies += "org.scalatest" %% "scalatest" % "2.2.6"
