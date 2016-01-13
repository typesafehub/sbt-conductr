package com.typesafe.conductr

import java.net.URL
import java.util.Date

import com.typesafe.conductr.clientlib.scala.models.{ BundleLog, BundleEvent }

/**
 * Common test methods and constants.
 */
object TestData {

  val BundleFile = "typesafe-conductr-tester-v0-5dd6695ed93ea6f10d856a97e2e90b56eb28bdc7d98555be944066b83f536a55.zip"
  lazy val testBundle: URL = TestData.getClass.getClassLoader.getResource(BundleFile)

  val ConfigFileWithBundleConf = "typesafe-conductr-tester-config-586cc6f71c0fa08f9c61e58607c66604410ab5583071b51ea932b905efc849fc.zip"
  lazy val testConfigWithBundleConf: URL = TestData.getClass.getClassLoader.getResource(ConfigFileWithBundleConf)

  val ConfigFileWithoutBundleConf = "typesafe-conductr-tester-config-379d73d388c33b96ee8d6677b972e98820af92b5a29a0f85e4e4fdccf1cf35e3.zip"
  lazy val testConfigWithoutBundleConf: URL = TestData.getClass.getClassLoader.getResource(ConfigFileWithoutBundleConf)

  val BundleEvents = Seq(
    BundleEvent(
      timestamp = new Date(1452614899549l),
      event = "conductr.loadExecutor.bundleWritten",
      description = "Bundle written: requestId=53fd9495-79cb-4098-b93a-ac66b8eb7b73, bundleName=conductr-elasticsearch, bundleId=ec1f9e50809bada6e1188fec7fe20d1f"
    ),
    BundleEvent(
      timestamp = new Date(1452614901104l),
      event = "conductr.scaleScheduler.scaleBundleRequested",
      description = "Scale bundle requested: requestId=258ee94e-2b45-4e33-aef3-8f1ba054f39d, bundleId=ec1f9e50809bada6e1188fec7fe20d1f, scale=1"
    )
  )

  val BundleLogs = Seq(
    BundleLog(
      timestamp = new Date(1452614899549l),
      host = "78a1db1ae29a",
      message = "[2016-01-12 16:08:58,651][INFO ][cluster.metadata] [Myron MacLain] [conductr] update_mapping [rfc5424] (dynamic)"
    ),
    BundleLog(
      timestamp = new Date(1452614901104l),
      host = "78a1db1ae29a",
      message = "[2016-01-12 16:08:30,268][INFO ][cluster.metadata] [Myron MacLain] [conductr] update_mapping [rfc5424] (dynamic)"
    )
  )
}
