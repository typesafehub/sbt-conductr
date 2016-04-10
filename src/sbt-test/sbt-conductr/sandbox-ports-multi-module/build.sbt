import org.scalatest.Matchers._
import ByteConversions._

name := "sandbox-ports-multi-module"
version := "0.1.0-SNAPSHOT"

lazy val common = (project in file("modules/common"))

lazy val frontend = (project in file("modules/frontend"))
  .enablePlugins(JavaAppPackaging)
  .dependsOn(common)
  .settings(
    BundleKeys.nrOfCpus := 1.0,
    BundleKeys.memory := 64.MiB,
    BundleKeys.diskSpace := 50.MiB,
    BundleKeys.roles := Set("frontend"),
    BundleKeys.endpoints := Map("frontend" -> Endpoint("http", services = Set(URI("http://:9000"))))
  )

lazy val backend = (project in file("modules/backend"))
  .enablePlugins(JavaAppPackaging)
  .dependsOn(common)
  .settings(
    BundleKeys.nrOfCpus := 1.0,
    BundleKeys.memory := 128.MiB,
    BundleKeys.diskSpace := 50.MiB,
    BundleKeys.roles := Set("backend"),
    BundleKeys.endpoints := Map(
      "frontend" -> Endpoint("http", services = Set(URI("http://:9001"))),
      "backend" -> Endpoint("http", services = Set(URI("http://:2551")))
    )
  )

val checkDockerContainers = taskKey[Unit]("Check that the specified ports are exposed to docker.")
checkDockerContainers := {
  // cond-0
  val contentCond0 = s"docker port cond-0".!!
  val expectedLinesCond0 = Set(
    """9000/tcp -> 0.0.0.0:9000""",
    """9001/tcp -> 0.0.0.0:9001""",
    """2551/tcp -> 0.0.0.0:2551""",
    """9004/tcp -> 0.0.0.0:9004""",
    """9005/tcp -> 0.0.0.0:9005""",
    """9006/tcp -> 0.0.0.0:9006""",
    """9999/tcp -> 0.0.0.0:9999""",
    """1111/tcp -> 0.0.0.0:1111""",
    """2222/tcp -> 0.0.0.0:2222"""
  )
  expectedLinesCond0.foreach(line => contentCond0 should include(line))

}

val checkConductRIsStopped = taskKey[Unit]("")
checkConductRIsStopped := {
  """docker ps --quiet --filter name=cond""".lines_! should have size 0
}
