import sbt._
import sbt.Resolver.bintrayRepo

object Version {
  val akka             = "2.3.11"
  val akkaContribExtra = "1.17.0-SNAPSHOT"
  val akkaHttp         = "1.0-RC3"
  val jansi            = "1.11"
  val jline            = "2.12"
  val play             = "2.3.8"
  val sbtBundle        = "0.23.0"
  val scalaTest        = "2.2.4"
  val scalactic        = "2.2.4"
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
  val akkaContribExtra = bintrayRepo("typesafe", "maven-releases")
  val patriknw         = bintrayRepo("patriknw", "maven")
  val typesafeReleases = "typesafe-releases" at "http://repo.typesafe.com/typesafe/maven-releases"
}
