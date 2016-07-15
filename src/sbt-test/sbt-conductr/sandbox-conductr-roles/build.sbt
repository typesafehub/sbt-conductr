import org.scalatest.Matchers._
import ByteConversions._

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
BundleKeys.diskSpace := 10.MB
BundleKeys.roles := Set("bundle-role-1", "bundle-role-2")

val checkConductrRolesByBundle = taskKey[Unit]("Check that the bundle roles are used if no sandbox roles are specified.")
checkConductrRolesByBundle := {
  for (i <- 0 to 2) {
    val content = s"docker inspect --format='{{.Config.Env}}' cond-$i".!!
    val expectedContent = "CONDUCTR_ROLES=bundle-role-1,bundle-role-2"
    content should not include(expectedContent)
  }
}

val checkConductrRolesBySandboxKey = taskKey[Unit]("Check that the declared sandbox roles are used.")
checkConductrRolesBySandboxKey := {
  for (i <- 0 to 3) {
    val content = s"docker inspect --format='{{.Config.Env}}' cond-$i".!!
    val expectedContent =
      if(i % 2 == 0) "CONDUCTR_ROLES=new-role"
      else           "CONDUCTR_ROLES=other-role"
    content should include(expectedContent)
  }
}

val checkConductrIsStopped = taskKey[Unit]("")
checkConductrIsStopped := {
  """docker ps --quiet --filter name=cond""".lines_! should have size 0
}