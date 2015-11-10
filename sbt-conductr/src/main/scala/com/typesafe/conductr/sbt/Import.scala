/*
 * Copyright © 2014-2015 Typesafe, Inc. All rights reserved.
 */

package com.typesafe.conductr.sbt

import sbt._
import akka.util.Timeout

object Import {

  object ConductRKeys {
    val conduct = inputKey[Unit]("ConductR task.")
    val conductrDiscoveredDist = taskKey[File]("Any distribution produced by the current project")
    val conductrDiscoveredConfigDist = taskKey[File]("Any additional configuration distribution produced by the current project")
    val conductrControlServerUrl = settingKey[URL]("The URL of the ConductR. Defaults to the env variables 'CONDUCTR_IP:[CONDUCTR_PORT]', otherwise uses a default address. As the default ip address either the docker host ip address or the ip address returned by the terminal command `hostname` is used. If the calculation of the default ip address returns '127.0.0.1' the conductr control server url is 'http://127.0.0.1:9005'.")
    val conductrLoggingQueryUrl = settingKey[URL]("The URL of the Logging Query API. Defaults to the env variables 'LOGGING_QUERY_IP:[LOGGING_QUERY_PORT]', otherwise uses the default. As the default ip address either the docker host ip address or the ip address returned by the terminal command `hostname` is used. , e.g. 'http://127.0.0.1:9210'. If the calculation of the default ip address returns '127.0.0.1' the conductr query logging url is 'http://127.0.0.1:9210'.")
    val conductrConnectTimeout = settingKey[Timeout]("The timeout for ConductR communications when connecting")
    val conductrLoadTimeout = settingKey[Timeout]("The timeout for ConductR communications when loading")
    val conductrRequestTimeout = settingKey[Timeout]("The timeout for ConductR communications when requesting")
    val conductrApiVersion = settingKey[String]("The api version to use when communicating with ConductR. Defaults to 1.0 which is required by ConductR 1.0.")
  }

  val conduct = ConductRKeys.conduct
}