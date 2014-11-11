lazy val sbtReactiveRuntime = project.in(file("."))

name := "sbt-reactive-runtime"

libraryDependencies ++= List(
)

initialCommands := """|import com.typesafe.reactiveruntime.sbtreactiveruntime._""".stripMargin
