/*
 * Copyright © 2014 Typesafe, Inc. All rights reserved.
 */

package com.typesafe.conductr

import java.net.URL

/**
 * Common test methods and constants.
 */
object TestBundle {

  val BundleFile = "typesafe-conductr-tester-v0-5dd6695ed93ea6f10d856a97e2e90b56eb28bdc7d98555be944066b83f536a55.zip"
  lazy val testBundle: URL = TestBundle.getClass.getClassLoader.getResource(BundleFile)

  val ConfigFileWithBundleConf = "typesafe-conductr-tester-config-586cc6f71c0fa08f9c61e58607c66604410ab5583071b51ea932b905efc849fc.zip"
  lazy val testConfigWithBundleConf: URL = TestBundle.getClass.getClassLoader.getResource(ConfigFileWithBundleConf)

  val ConfigFileWithoutBundleConf = "typesafe-conductr-tester-config-379d73d388c33b96ee8d6677b972e98820af92b5a29a0f85e4e4fdccf1cf35e3.zip"
  lazy val testConfigWithoutBundleConf: URL = TestBundle.getClass.getClassLoader.getResource(ConfigFileWithoutBundleConf)
}
