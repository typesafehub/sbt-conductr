import org.scalatest.Matchers._
import ByteConversions._

lazy val root = (project in file(".")).enablePlugins(PlayScala)

name := "custom-config"

version := "0.1.0-SNAPSHOT"
scalaVersion := "2.11.8"

BundleKeys.memory := 64.MiB

val checkBundleDist = taskKey[Unit]("check-bundle-dist-contents")
checkBundleDist := {
  val bundleContentsConf = IO.read((target in Bundle).value / "bundle" / "tmp" / "bundle.conf")
  val expectedContentsConf =
    """start-command    = ["custom-config/bin/custom-config", "-J-Xms67108864", "-J-Xmx67108864"]""".stripMargin
  bundleContentsConf should include(expectedContentsConf)
}

val checkConfigDist = taskKey[Unit]("check-config-dist-contents")
checkConfigDist := {


  val runtimeConf = IO.read((target in BundleConfiguration).value  / "stage" / "configuration" / "runtime-config.sh")
  val expectedRuntimeConf = """export APPLICATION_SECRET="thisismyapplicationsecret-pleasedonttellanyone"""".stripMargin
  runtimeConf should include(expectedRuntimeConf)
}