import ByteConversions._
import org.scalatest.Matchers._

lazy val root = project
  .in(file("."))
  .enablePlugins(JavaAppPackaging, ConductrPlugin)

name := "conduct-common-args"
version := "1.0.0"
scalaVersion := "2.10.4"

BundleKeys.nrOfCpus := 1.0
BundleKeys.memory := 10.MiB
BundleKeys.diskSpace := 5.MB
