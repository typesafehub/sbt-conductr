import sbt._

object Version {
  val sbtBundle         = "1.3.2"
  val scala             = "2.10.4"
}

object Library {
  val sbtBundle         = "com.typesafe.sbt"      %  "sbt-bundle"                 % Version.sbtBundle
}
