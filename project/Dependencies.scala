import sbt._
import sbt.Resolver.bintrayRepo

object Version {
  val conductrClientLib = "1.1.0"
  val jansi             = "1.11"
  val jline             = "2.12"
  val jodaTime          = "2.8.2"
  val sbtBundle         = "1.2.1"
  val scala             = "2.10.4"
  val scalaTest         = "2.2.4"
  val scalactic         = "2.2.4"
  val mockito           = "1.10.19"
}

object Library {
  val conductrClientLib = "com.typesafe.conductr" %% "akka23-conductr-client-lib" % Version.conductrClientLib
  val jansi             = "org.fusesource.jansi"  %  "jansi"                      % Version.jansi
  val jline             = "jline"                 %  "jline"                      % Version.jline
  val jodaTime          = "joda-time"             %  "joda-time"                  % Version.jodaTime
  val sbtBundle         = "com.typesafe.sbt"      %  "sbt-bundle"                 % Version.sbtBundle
  val scalaTest         = "org.scalatest"         %% "scalatest"                  % Version.scalaTest
  val scalactic         = "org.scalactic"         %% "scalactic"                  % Version.scalactic
  val mockito           = "org.mockito"           %  "mockito-core"               % Version.mockito
}

object Resolver {
  val typesafeReleases        = "typesafe-releases" at "http://repo.typesafe.com/typesafe/maven-releases"
  val typesafeBintrayReleases = bintrayRepo("typesafe", "maven-releases")
}
