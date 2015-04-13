import ByteConversions._
import com.typesafe.conductr.sbt.ConductRKeys._

lazy val root = project
  .in(file("."))
  .enablePlugins(JavaAppPackaging, ConductRPlugin)

name := "sbt-conductr-tester"
version := "1.0.0"
scalaVersion := "2.11.4"

// ConductR

BundleKeys.nrOfCpus := 1.0
BundleKeys.memory := 10.MiB
BundleKeys.diskSpace := 5.MB
BundleKeys.roles := Set("web-server")
BundleKeys.endpoints := Map.empty


// test task for controlServer scripted test
lazy val testControlServer = inputKey[Unit]("test value of conductrControlServerUrl")

testControlServer := {
  import sbt.complete.DefaultParsers._
  val args: Seq[String] = spaceDelimited("<arg>").parsed

  val inputMaybe = args.headOption

  inputMaybe.map{ input =>

    val extracted = Project.extract(state.value)
    val settings = extracted.structure.data

    // check input == value of conductrControlServerUrl
    input ==
      (conductrControlServerUrl in Global).get(settings).map(_.toString)
        .map(input => input.substring(7,input.length)) // string out 'http://'
        .getOrElse("")
  }.fold(
      sys.error("testControlServer requires an input string")
    )( value => if(!value) sys.error(s"${inputMaybe.getOrElse("")} does not equal $value"))
}