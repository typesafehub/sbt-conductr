import sbt._

object Version {
  val akka             = "2.3.9"
  val akkaContribExtra = "1.2.0"
  val akkaHttp         = "1.0-M2"
  val jansi            = "1.11"
  val jline            = "2.12"
  val play             = "2.4.0-M1"
  val sbtBundle        = "0.11.0"
  val scalaTest        = "2.2.3"
  val scalactic        = "2.2.3"
}

object Library {
  val akkaContribExtra = "com.typesafe.akka"    %% "akka-contrib-extra"     % Version.akkaContribExtra
  val akkaHttp         = "com.typesafe.akka"    %% "akka-http-experimental" % Version.akkaHttp
  val akkaTestkit      = "com.typesafe.akka"    %% "akka-testkit"           % Version.akka
  val jansi            = "org.fusesource.jansi" %  "jansi"                  % Version.jansi
  val jline            = "jline"                %  "jline"                  % Version.jline
  val playJson         = "com.typesafe.play"    %% "play-json"              % Version.play
  val sbtBundle        = "com.typesafe.sbt"     %  "sbt-bundle"             % Version.sbtBundle
  val scalaTest        = "org.scalatest"        %% "scalatest"              % Version.scalaTest
  val scalactic        = "org.scalactic"        %% "scalactic"              % Version.scalactic
}

object Resolver {
  val patriknw         = "patriknw at bintray" at "http://dl.bintray.com/patriknw/maven"
  val typesafeReleases = "typesafe-releases" at "http://repo.typesafe.com/typesafe/maven-releases"
}
