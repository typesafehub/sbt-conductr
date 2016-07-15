import ByteConversions._
import org.scalatest.Matchers._

lazy val root = project
  .in(file("."))
  .enablePlugins(JavaAppPackaging, ConductrPlugin)

name := "conduct-common-args"
version := "1.0.0"
scalaVersion := "2.10.4"

javaOptions in Universal := Seq(
  "-J-Xms10m",
  "-J-Xmx10m"
)

BundleKeys.nrOfCpus := 0.1
BundleKeys.memory := 10.MiB
BundleKeys.diskSpace := 5.MB
