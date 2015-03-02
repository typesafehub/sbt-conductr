/*
 * Copyright © 2014 Typesafe, Inc. All rights reserved.
 */

package com.typesafe.conductr

import akka.util.ByteString
import java.io.{ File, InputStream }
import java.net.URL
import java.nio.charset.Charset
import java.util.UUID

/**
 * Common test methods and constants.
 */
object TestBundle {

  private def toByteString(s: String): ByteString =
    ByteString(s.replaceAll("[^0-9A-Fa-f]", "").sliding(2, 2).toArray map (Integer.parseInt(_, 16).toByte))

  val BundleDigestStr = "4622e82968c6aab2b1d8a6233ae99bc0e10183cba5ace97035c62ad6a58268ef"

  val BundleDigestBytes = toByteString(BundleDigestStr)

  val BundleComponentName = "sbt-typesafe-conductr-tester-1.0.0"

  val BundleFile = s"$BundleComponentName-$BundleDigestStr.zip"

  val BundleFileSize = 4949655

  val ConfigDigestStr = "4d05332788c65307e829bf993a2454dc49f7ece2d343000f2fd7cf0d2f07a3db"

  val ConfigDigestBytes = toByteString(ConfigDigestStr)

  val ConfigComponentName = "bundle.conf"

  val ConfigFile = s"$ConfigComponentName-$ConfigDigestStr.zip"

  val ConfigFileSize = 303

  val Id = UUID.randomUUID()

  val DigestAlgorithm = "SHA-256"

  val Utf8 = Charset.forName("UTF-8")

  lazy val testConfig: URL =
    TestBundle.getClass.getClassLoader.getResource(ConfigFile)

  def testConfigStream: InputStream =
    TestBundle.getClass.getClassLoader.getResourceAsStream(ConfigFile)
}
