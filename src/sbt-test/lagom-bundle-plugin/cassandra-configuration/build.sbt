import org.scalatest.Matchers._

scalaVersion in ThisBuild := "2.11.7"
version in ThisBuild := "0.1.0-SNAPSHOT"

lazy val simpleApi = (project in file("simple-api"))
  .settings(libraryDependencies += lagomJavadslApi)

lazy val simpleImpl = (project in file("simple-impl"))
  .enablePlugins(LagomJava)
  .dependsOn(simpleApi)

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

  val runtimeConfigContent = IO.read(cassandraConfigurationDir / "runtime-config.sh").indent
  val expectedRuntimeConfigContent = """|# Demonstrate providing an entirely new configuration directory
                                        |CURRENT_PATH=`dirname "$0"`
                                        |export $CASSANDRA_CONF=$CURRENT_PATH/cassandra-conf""".stripMargin.indent
  runtimeConfigContent should include (expectedRuntimeConfigContent)

  val cassandraYaml = cassandraConfigurationDir / "cassandra-conf" / "cassandra.yaml"
  cassandraYaml.exists shouldBe true

  val jvmOptions = cassandraConfigurationDir / "cassandra-conf" / "jvm.options"
  jvmOptions.exists shouldBe true

  val logbackXml = cassandraConfigurationDir / "cassandra-conf" / "logback.xml"
  logbackXml.exists shouldBe true
}