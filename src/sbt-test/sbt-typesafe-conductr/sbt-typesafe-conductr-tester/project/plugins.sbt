import java.io.FileInputStream
import java.util.Properties

addSbtPlugin("com.typesafe.conductr" % "sbt-typesafe-conductr" % {
  val s = new FileInputStream(file("version.properties"))
  try {
    val p = new Properties()
    p.load(s)
    p.getProperty("version")
  } finally s.close()
})