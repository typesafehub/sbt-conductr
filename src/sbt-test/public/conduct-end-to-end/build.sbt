import ByteConversions._
import org.scalatest.Matchers._

lazy val root = project
  .in(file("."))
  .enablePlugins(JavaAppPackaging, ConductrPlugin)

name := "conduct-end-to-end"
version := "1.0.0"
scalaVersion := "2.10.4"

javaOptions in Universal := Seq(
  "-J-Xms10m",
  "-J-Xmx10m"
)

BundleKeys.nrOfCpus := 0.1
BundleKeys.memory := 10.MiB
BundleKeys.minMemoryCheckValue := 10.MiB
BundleKeys.diskSpace := 5.MB

val verifyConductLoad = taskKey[Unit]("")
verifyConductLoad := {
  conductInfo() should include("""reactive-maps-backend-region    v1     1     0     0  intranet
                                 |reactive-maps-backend-summary   v1     1     0     0  intranet""".stripMargin)
}

val verifyConductRun = taskKey[Unit]("")
verifyConductRun := {
  conductInfo() should include("""reactive-maps-backend-region    v1     1     0     1  intranet
                                 |reactive-maps-backend-summary   v1     1     0     1  intranet""".stripMargin)
}

val verifyConductStop = taskKey[Unit]("")
verifyConductStop := {
  val output = conductInfo()
  output should include("reactive-maps-backend-region    v1     1     0     0  intranet")
  output should include("reactive-maps-backend-summary   v1     1     0     0  intranet")
}

val verifyConductUnload = taskKey[Unit]("")
verifyConductUnload := {
  val output = conductInfo()
  output should not include("reactive-maps-backend-region")
  output should not include("reactive-maps-backend-summary")
}

def conductInfo(): String =
  (Process("conduct info") #| Process(Seq("awk", "{print substr($0, index($0, $2))}"))).!!
