import sbt._
import sbt.Resolver.bintrayRepo

object Version {
  val sbtBundle         = "1.3.2"
  val scala             = "2.10.4"
}

object Library {
  val sbtBundle         = "com.typesafe.sbt"      %  "sbt-bundle"                 % Version.sbtBundle
}

object Resolver {
  val typesafeReleases        = "typesafe-releases" at "http://repo.typesafe.com/typesafe/maven-releases"
  val typesafeBintrayReleases = bintrayRepo("typesafe", "maven-releases")
}
