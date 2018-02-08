addSbtPlugin("com.github.gseitz" % "sbt-release"      % "1.0.6")
addSbtPlugin("org.scalariform"   % "sbt-scalariform"  % "1.6.0")
addSbtPlugin("me.lessis"         % "bintray-sbt"      % "0.2.1")

libraryDependencies += "org.scala-sbt" % "scripted-plugin" % sbtVersion.value
