import org.scalatest.Matchers._
import ByteConversions._

lazy val root = (project in file(".")).enablePlugins(JavaAppPackaging)

name := "sandbox-all-args"

version := "0.1.0-SNAPSHOT"

javaOptions in Universal := Seq(
  "-J-Xms64m",
  "-J-Xmx64m"
)

// ConductR bundle keys
BundleKeys.nrOfCpus := 0.1
BundleKeys.memory := 64.MiB
BundleKeys.minMemoryCheckValue := 64.MiB
BundleKeys.diskSpace := 10.MB
