import org.scalatest.Matchers._
import ByteConversions._

name := "sandbox-ports-multi-module"
version := "0.1.0-SNAPSHOT"

lazy val common = (project in file("modules/common"))

lazy val frontend = (project in file("modules/frontend"))
  .enablePlugins(JavaAppPackaging)
  .dependsOn(common)
  .settings(
    javaOptions in Universal := Seq(
      "-J-Xms64m",
      "-J-Xmx64m"
    ),
    BundleKeys.nrOfCpus := 0.1,
    BundleKeys.memory := 64.MiB,
    BundleKeys.minMemoryCheckValue := 64.MiB,
    BundleKeys.diskSpace := 50.MiB,
    BundleKeys.roles := Set("frontend"),
    BundleKeys.endpoints := Map("frontend" -> Endpoint("http", services = Set(URI("http://:9000"))))
  )

lazy val backend = (project in file("modules/backend"))
  .enablePlugins(JavaAppPackaging)
  .dependsOn(common)
  .settings(
    javaOptions in Universal := Seq(
      "-J-Xms128m",
      "-J-Xmx128m"
    ),
    BundleKeys.nrOfCpus := 0.1,
    BundleKeys.memory := 128.MiB,
    BundleKeys.minMemoryCheckValue := 128.MiB,
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
  val contentSandbox = s"docker port sandbox-haproxy".!!
  val expectedLinesSandbox = Set(
    """2551/tcp -> 192.168.10.1:2551""",
    """1111/tcp -> 192.168.10.1:1111""",
    """2222/tcp -> 192.168.10.1:2222"""
  )
  expectedLinesSandbox.foreach(line => contentSandbox should include(line))

}
