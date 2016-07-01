import org.scalatest.Matchers._
import ByteConversions._

scalaVersion in ThisBuild := "2.11.7"
version in ThisBuild := "0.1.0-SNAPSHOT"

lazy val `lagom-service-api` = (project in file("lagom-service-api"))
  .settings(libraryDependencies += lagomJavadslApi)

lazy val `lagom-service-impl` = (project in file("lagom-service-impl"))
  .enablePlugins(LagomJava)
  .dependsOn(`lagom-service-api`)
  .settings(
    javaOptions in Universal := Seq(
      "-J-Xms268435456",
      "-J-Xmx268435456"
    ),
    BundleKeys.conductrTargetVersion := ConductrVersion.V1_2,
    BundleKeys.memory := 256.MiB
  )

lazy val `play-service` = (project in file("play-service"))
  .enablePlugins(PlayJava, LagomPlay)
  .settings(
    javaOptions in Universal := Seq(
      "-J-Xms67108864",
      "-J-Xmx67108864"
    ),
    BundleKeys.conductrTargetVersion := ConductrVersion.V1_2,
    routesGenerator := InjectedRoutesGenerator,
    BundleKeys.memory := 64.MiB
  )

// Test assertions
val checkBundleConf = taskKey[Unit]("")

checkBundleConf := {
  implicit class StringOps(s: String) {
    def indent: String = s.replaceAll("  ", "")
  }

  val lagomContent = IO.read((target in `lagom-service-impl` in Bundle).value / "bundle" / "tmp" / "bundle.conf").indent
  val expectedLagomMemory = "memory = 268435456"
  val expectedLagomStartCommand =
    """start-command= ["lagom-service-impl/bin/lagom-service-impl", "-J-Xms268435456", "-J-Xmx268435456", "-Dhttp.address=$FOOSERVICE_BIND_IP", "-Dhttp.port=$FOOSERVICE_BIND_PORT", "-Dplay.crypto.secret=6c61676f6d2d736572766963652d696d706c"]"""
  val expectedLagomEndpoints =
    """|endpoints = {
       |  "fooservice" = {
       |    bind-protocol = "http"
       |    bind-port     = 0
       |    service-name  = "fooservice"
       |    acls          = [
       |      {
       |        http = {
       |          requests = [
       |            {
       |              path-beg = "/foo"
       |            }
       |          ]
       |        }
       |      }
       |    ]
       |  },
       |  "akka-remote" = {
       |    bind-protocol = "tcp"
       |    bind-port     = 0
       |    services      = []
       |  }
       |}""".stripMargin.indent

  lagomContent should include (expectedLagomMemory)
  lagomContent should include (expectedLagomStartCommand)
  lagomContent should include (expectedLagomEndpoints)

  val playContent = IO.read((target in `play-service` in Bundle).value / "bundle" / "tmp" / "bundle.conf").indent
  val expectedPlayMemory = "memory = 67108864"
  val expectedPlayStartCommand =
    """start-command= ["play-service/bin/play-service", "-J-Xms67108864", "-J-Xmx67108864"]""".stripMargin
  val expectedPlayEndpoints =
    """|endpoints = {
       |  "web" = {
       |    bind-protocol = "http"
       |    bind-port     = 0
       |    service-name  = "web"
       |    acls          = [
       |      {
       |        http = {
       |          requests = [
       |            {
       |              path-beg = "/"
       |            }
       |          ]
       |        }
       |      }
       |    ]
       |  }
       |}""".stripMargin.indent

  playContent should include (expectedPlayMemory)
  playContent should include (expectedPlayStartCommand)
  playContent should include (expectedPlayEndpoints)
}