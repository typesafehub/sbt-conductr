import ByteConversions._
import com.lightbend.conductr.sbt.BundlePlugin._
import org.scalatest.Matchers._

lazy val root = (project in file(".")).enablePlugins(JavaAppPackaging)

name := "validation-conductr-version-compatibility"

version := "0.1.0-SNAPSHOT"

javaOptions in Universal := Seq(
  "-J-Xms67108864",
  "-J-Xmx67108864"
)

BundleKeys.nrOfCpus := 0.1
BundleKeys.memory := 64.MiB
BundleKeys.diskSpace := 10.MB

val checkBundleConfNotGenerated = taskKey[Unit]("")

checkBundleConfNotGenerated := {
  val bundleConfFile = (target in Bundle).value / "bundle" / "tmp" / "bundle.conf"
  bundleConfFile.exists() shouldBe false
}
