import org.scalatest.Matchers._

import scala.util.{Failure, Success, Try}

scalaVersion in ThisBuild := "2.11.7"
version in ThisBuild := "0.1.0-SNAPSHOT"

lazy val `lagom-service-api` = (project in file("lagom-service-api"))
  .settings(libraryDependencies += lagomJavadslApi)

lazy val `lagom-service-impl` = (project in file("lagom-service-impl"))
  .enablePlugins(LagomJava)
  .dependsOn(`lagom-service-api`)

// Test assertions
val verifyConductInfo = taskKey[Unit]("")
verifyConductInfo := {
  val output = conductInfo()
  output should include("lagom-service-impl")
}

def conductInfo(): String =
  (Process("conduct info") #| Process(Seq("awk", "{print $2}"))).!!

InputKey[Unit]("verifyIsStarted") := {
  val args = Def.spaceDelimited().parsed
  val bundleName = args(0)
  DevModeBuild.waitFor[String](
    Success(bundleStatus(bundleName).trim),
    _.equals("101"), // 101 -->  #REPlica==1  #STaRting==0  #RUNning== 1
    _ match {
      case Success(msg) =>
        val message = s"Timeout awaiting [$bundleName] to start."
        org.scalatest.Matchers.fail(message)
      case Failure(t) => throw t
    }
  )(maximumAttempts)
  // retry _maximumAttempts_ times with a hardcoded delay between attempts of 1 sec. This doesn't consider the
  // command execution time so this await could be around 2 minutes if `conduct info` takes 1 sec for every
  // time it runs.
}

def bundleStatus(bundleName:String): String ={

  (Process("conduct info") #| Process(Seq("grep", bundleName))  #| Process(Seq("awk", "{print $3 $4 $5}"))  ).!!
}


// copy/pasted from https://github.com/lagom/lagom/blob/d9f4df0f56f7c567e88602fa91698b8d8f5de3d9/dev/sbt-plugin/src/sbt-test/sbt-plugin/run-all-javadsl/build.sbt#L63
InputKey[Unit]("assertRequest") := {
  val args = Def.spaceDelimited().parsed
  val port = args(0)
  val path = args(1)
  val expect = args.drop(2).mkString(" ")

  DevModeBuild.waitForRequestToContain(s"http://192.168.10.1:${port}${path}", expect)(maximumAttempts)
}

val maximumAttempts = 5