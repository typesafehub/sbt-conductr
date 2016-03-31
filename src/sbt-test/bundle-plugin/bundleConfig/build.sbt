import ByteConversions._
import com.lightbend.conductr.sbt.BundlePlugin._
import org.scalatest.Matchers._

lazy val root = (project in file(".")).enablePlugins(JavaAppPackaging)

name := "simple-test"

version := "0.1.0-SNAPSHOT"

BundleKeys.nrOfCpus := 1.0
BundleKeys.memory := 64.MiB
BundleKeys.diskSpace := 10.MB
BundleKeys.roles := Set("web-server")

val checkBundleConf = taskKey[Unit]("check-main-css-contents")

BundleKeys.configurationName := "backend"

val checkConfigDist = taskKey[Unit]("check-config-dist-contents")

checkConfigDist := {
  val bundleContentsConf = IO.read((target in BundleConfiguration).value  / "stage" / "configuration" / "bundle.conf")
  val expectedContentsConf = """components = {
                           |  "override-1.0.0" = {
                           |    start-command    = ["override-1.0.0/bin/override", "-J-Xms67108864", "-J-Xmx67108864"]
                           |  }
                           |}""".stripMargin
  bundleContentsConf should include(expectedContentsConf)

  val bundleContentsSh = IO.read((target in BundleConfiguration).value  / "stage" / "configuration" / "test.sh")
  val expectedContentsSh = """#!/bin/bash
                               |
                               |export TEST_HOME=/some/path/backend
                               |""".stripMargin

  bundleContentsSh should include(expectedContentsSh)

}