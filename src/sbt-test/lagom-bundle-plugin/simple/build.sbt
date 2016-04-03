import org.scalatest.Matchers._

scalaVersion in ThisBuild := "2.11.7"
version in ThisBuild := "0.1.0-SNAPSHOT"

lazy val simpleApi = (project in file("simple-api"))
  .settings(libraryDependencies += lagomJavadslApi)

lazy val simpleImpl = (project in file("simple-impl"))
  .enablePlugins(LagomJava)
  .dependsOn(simpleApi)

// Test assertions
val checkBundleConf = taskKey[Unit]("")

checkBundleConf := {
  implicit class StringOps(s: String) {
    def indent: String = s.replaceAll("  ", "")
  }

  val contents = IO.read((target in simpleImpl in Bundle).value / "bundle" / "tmp" / "bundle.conf").indent
  val expectedContent = """|endpoints = {
                           |  "fooservice" = {
                           |    bind-protocol = "http"
                           |    bind-port     = 0
                           |    services      = ["http://:9000/fooservice", "http://:9000/foo?preservePath"]
                           |  },
                           |  "akka-remote" = {
                           |    bind-protocol = "tcp"
                           |    bind-port = 0
                           |    services= []
                           |  }
                           |}""".stripMargin.indent
  contents should include (expectedContent)
}