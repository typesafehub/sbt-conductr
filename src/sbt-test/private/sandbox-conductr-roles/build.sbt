import org.scalatest.Matchers._
import org.scalatest.concurrent.Eventually._
import org.scalatest.concurrent.PatienceConfiguration.{ Interval, Timeout }
import ByteConversions._
import scala.concurrent.duration._

name := "conductr-roles"

version := "0.1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(JavaAppPackaging)

javaOptions in Universal := Seq(
  "-J-Xms64m",
  "-J-Xmx64m"
)

// ConductR bundle keys
BundleKeys.nrOfCpus := 0.1
BundleKeys.memory := 64.MiB
BundleKeys.minMemoryCheckValue := 64.MiB
BundleKeys.diskSpace := 10.MB
BundleKeys.roles := Set("bundle-role-1", "bundle-role-2")

val assertionTimeout = Timeout(3.seconds)
val assertionRetryInterval = Interval(200.millis)

val checkConductrRolesByBundle = taskKey[Unit]("Check that the bundle roles are used if no sandbox roles are specified.")
checkConductrRolesByBundle := {
  (1 to 3).foreach { i =>
    eventually(timeout = assertionTimeout, interval = assertionRetryInterval) {
      val agent = psOutput.filter(_.contains(s"-Dconductr.agent.ip=192.168.10.$i"))
      agent should have size 1
      val expectedContent = "-Dconductr.agent.roles.0=bundle-role-1 -Dconductr.agent.roles.1=bundle-role-2"
      agent.head should not include(expectedContent)
    }
  }
}

val checkConductrRolesBySandboxKey = taskKey[Unit]("Check that the declared sandbox roles are used.")
checkConductrRolesBySandboxKey := {
  (1 to 3).foreach { i =>
    eventually(timeout = assertionTimeout, interval = assertionRetryInterval) {
      val agent = psOutput.filter(_.contains(s"-Dconductr.agent.ip=192.168.10.$i"))
      agent should have size 1
      val expectedContent =
        if(i % 2 == 1) "-Dconductr.agent.roles.0=new-role"
        else           "-Dconductr.agent.roles.0=other-role"
      agent.head should include(expectedContent)
    }
  }
}

def psOutput: Seq[String] =
  "ps ax".lines_!.toList
