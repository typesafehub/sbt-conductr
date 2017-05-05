package com.lightbend.conductr

import java.lang.reflect.InvocationTargetException
import java.nio.charset.Charset
import _root_.sbt._
import scala.reflect.ClassTag
import scala.util.Try
import scala.annotation.tailrec

package object sbt {

  final val Sha256 = "SHA-256"
  final val Utf8 = Charset.forName("utf-8")

  /**
   * Create a hash based on a UTF-8 string
   */
  def hash(content: String): String =
    hash(content.getBytes(Utf8))

  def hash(bytes: Array[Byte]): String =
    Hash.toHex(bytes)

  def envName(name: String) =
    name.replaceAll("\\W", "_").toUpperCase

  @tailrec
  def recursiveListFiles(currentDirs: Array[File], filter: FileFilter, files: Array[File] = Array.empty): Array[File] =
    if (currentDirs.isEmpty)
      files
    else
      recursiveListFiles(currentDirs.flatMap(_.listFiles(DirectoryFilter)), filter, files ++ currentDirs.flatMap(_.listFiles(filter)))

  /**
   * Uses the given class loader for the given code block
   */
  def withContextClassloader[T](loader: ClassLoader)(body: ClassLoader => T): T = {
    val current = Thread.currentThread().getContextClassLoader
    try {
      Thread.currentThread().setContextClassLoader(loader)
      body(loader)
    } finally Thread.currentThread().setContextClassLoader(current)
  }

  object Reflection {

    /**
     * Resolves the singleton object instance via reflection.
     * The given `className` must end with "$", e.g. "com.lightbend.lagom.internal.api.tools.ServiceDetector$"
     */
    def getSingletonObject[T: ClassTag](classLoader: ClassLoader, className: String): Try[T] =
      Try {
        val clazz = classLoader.loadClass(className)
        val t = implicitly[ClassTag[T]].runtimeClass
        clazz.getField("MODULE$").get(null) match {
          case null                  => throw new NullPointerException
          case c if !t.isInstance(c) => throw new ClassCastException(s"${clazz.getName} is not a subtype of $t")
          case c: T                  => c
        }
      } recover {
        case i: InvocationTargetException if i.getTargetException ne null => throw i.getTargetException
      }
  }

  object Library {
    def playConductrBundleLib(playVersion: String, scalaVersion: String, conductrLibVersion: String) =
      "com.typesafe.conductr" % s"play${formatVersionMajorMinor(playVersion)}-conductr-bundle-lib_$scalaVersion" % conductrLibVersion
    def lagomConductrBundleLib(language: String, lagomVersion: String, scalaVersion: String, conductrLibVersion: String) =
      "com.typesafe.conductr" % s"lagom${formatVersionMajor(lagomVersion)}-$language-conductr-bundle-lib_$scalaVersion" % conductrLibVersion

    private def formatVersionMajorMinor(version: String): String =
      version.filterNot(_ == '.').take(2)

    private def formatVersionMajor(version: String): String =
      version.filterNot(_ == '.').take(1)
  }

  object Version {
    val conductrBundleLib = "1.9.0"
  }

  /**
   * Sbt keys used by multiple auto plugins
   */
  object BaseKeys {
    val conductrBundleLibVersion = SettingKey[String](
      "play-bundle-conductr-bundle-lib-version",
      s"The version of conductr-bundle-lib to depend on. Defaults to ${Version.conductrBundleLib}"
    )
  }

  /**
   * Default bundle keys for a Play project
   */
  object PlayBundleKeyDefaults {
    import com.lightbend.conductr.sbt.BundleImport.ByteConversions._

    val nrOfCpus = 0.1
    val heapMemory = 128.MiB
    val residentMemory = 384.MiB
    val diskSpace = 200.MB
  }

  object NonDirectoryFilter extends FileFilter {
    def accept(file: File) = !file.isDirectory
  }

  /**
   * An auto plugin that doesn't get triggered automatically
   * Add this plugin as a dependency in the `requires` method to your plugin to not trigger your plugin automatically
   */
  object NoOpPlugin extends AutoPlugin
}
