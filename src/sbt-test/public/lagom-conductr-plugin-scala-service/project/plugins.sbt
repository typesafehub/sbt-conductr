addSbtPlugin("com.lightbend.lagom" % "lagom-sbt-plugin" % "1.4.0")

addSbtPlugin("com.lightbend.conductr" % "sbt-conductr" % sys.props("project.version"))

libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.5"

// play-ws_2.10-2.3.10 depends on  com.typesafe.netty#netty-http-pipelining;1.1.2 which is only
// available in Resolver.typesafeRepo("releases")
resolvers += Resolver.typesafeRepo("releases")

libraryDependencies += "com.typesafe.play" % "play-ws_2.10" % "2.3.10"
