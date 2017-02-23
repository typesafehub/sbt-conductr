import org.scalatest.Matchers._

scalaVersion in ThisBuild := "2.11.7"
version in ThisBuild := "0.1.0-SNAPSHOT"

lazy val simpleApi = (project in file("simple-api"))
  .settings(libraryDependencies += lagomJavadslApi)

lazy val simpleImpl = (project in file("simple-impl"))
  .enablePlugins(LagomJava)
  .dependsOn(simpleApi)
  .settings(
    BundleKeys.conductrTargetVersion := ConductrVersion.V1_1,
    BundleKeys.enableAcls := false
  )

// Test assertions
val checkCassandraConf = taskKey[Unit]("")

checkCassandraConf := {
  implicit class StringOps(s: String) {
    def indent: String = s.replaceAll("  ", "")
  }

  val cassandraConfigurationDir = (target in LocalRootProject).value / "bundle-configuration" / "stage" / "cassandra-configuration"
  val bundleConfContent = IO.read(cassandraConfigurationDir / "bundle.conf").indent
  val expectedBundleConfContent = """|name   = "cassandra"
                                     |system = "cassandra"
                                     |components.cassandra = {
                                     |  endpoints = {
                                     |    "cas_native" = {
                                     |      bind-protocol = "tcp"
                                     |      bind-port     = 0
                                     |      services      = ["tcp://:9042/cas_native"]
                                     |    },
                                     |    // 'cas_rpc' endpoint need to be declared to override the endpoint from the cassandra bundle itself
                                     |    "cas_rpc" = {
                                     |      bind-protocol = "tcp"
                                     |      bind-port     = 0
                                     |      services      = []
                                     |    },
                                     |    "cas_storage" = {
                                     |      bind-protocol = "tcp"
                                     |      bind-port     = 7000
                                     |      services      = []
                                     |    }
                                     |  }
                                     |}
                                     |""".stripMargin.indent
  bundleConfContent should include (expectedBundleConfContent)
}