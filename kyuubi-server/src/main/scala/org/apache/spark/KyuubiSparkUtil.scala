/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark

import java.io.File
import java.net.URI
import javax.print.DocFlavor.STRING

import scala.collection.Map
import scala.util.matching.Regex

import org.apache.hadoop.conf.Configuration
import org.apache.spark.deploy.SparkHadoopUtil
import org.apache.spark.util._
import org.slf4j.Logger

import yaooqinn.kyuubi.Logging

/**
 * Wrapper for [[Utils]] and [[SparkHadoopUtil]]
 */
object KyuubiSparkUtil extends Logging {
  // PREFIXES
  val SPARK_PREFIX = "spark."
  private[this] val YARN_PREFIX = "yarn."
  private[this] val HADOOP_PRFIX = "hadoop."
  val SPARK_HADOOP_PREFIX: String = SPARK_PREFIX + HADOOP_PRFIX
  private[this] val DRIVER_PREFIX = "driver."
  private[this] val AM_PREFIX = SPARK_PREFIX + YARN_PREFIX + "am."

  private[this] val UI_PREFIX = "ui."
  private[this] val SQL_PREFIX = "sql."
  private[this] val HIVE_PREFIX = "hive."
  private[this] val METASTORE_PREFIX = "metastore."


  // ENVIRONMENTS
  val SPARK_HOME: String = System.getenv("SPARK_HOME")
  val SPARK_JARS_DIR: String = SPARK_HOME + File.separator + "jars"

  // YARN
  val KEYTAB: String = SPARK_PREFIX + YARN_PREFIX + "keytab"
  val PRINCIPAL: String = SPARK_PREFIX + YARN_PREFIX + "principal"
  val MAX_APP_ATTEMPTS: String = SPARK_PREFIX + YARN_PREFIX + "maxAppAttempts"
  val SPARK_YARN_JARS: String = SPARK_PREFIX + YARN_PREFIX + "jars"

  // DRIVER
  val DRIVER_BIND_ADDR: String = SPARK_PREFIX + DRIVER_PREFIX + "bindAddress"
  val DRIVER_MEM: String = SPARK_PREFIX + DRIVER_PREFIX + "memory"
  val DRIVER_MEM_OVERHEAD: String = SPARK_PREFIX + YARN_PREFIX + DRIVER_PREFIX + "memoryOverhead"
  val DRIVER_CORES: String = SPARK_PREFIX + DRIVER_PREFIX + "cores"
  val DRIVER_EXTRA_JAVA_OPTIONS: String = SPARK_PREFIX + DRIVER_PREFIX + "extraJavaOptions"

  val AM_EXTRA_JAVA_OPTIONS: String = AM_PREFIX + "extraJavaOptions"

  val SPARK_UI_PORT: String = SPARK_PREFIX + UI_PREFIX + "port"
  val SPARK_UI_PORT_DEFAULT = "0"

  val MULTIPLE_CONTEXTS: String = SPARK_PREFIX + DRIVER_PREFIX + "allowMultipleContexts"
  val MULTIPLE_CONTEXTS_DEFAULT = "true"

  val CATALOG_IMPL: String = SPARK_PREFIX + SQL_PREFIX + "catalogImplementation"
  val CATALOG_IMPL_DEFAULT = "hive"

  val DEPLOY_MODE: String = SPARK_PREFIX + "submit.deployMode"
  val DEPLOY_MODE_DEFAULT = "client"

  // Hive
  val METASTORE_JARS: String = SPARK_PREFIX + SQL_PREFIX + HIVE_PREFIX + METASTORE_PREFIX + "jars"
  val METASTORE_KEYTAB: String =
    SPARK_HADOOP_PREFIX + HIVE_PREFIX + METASTORE_PREFIX + "kerberos.keytab.file"
  val METASTORE_PRINCIPAL: String =
    SPARK_HADOOP_PREFIX + HIVE_PREFIX + METASTORE_PREFIX + "kerberos.principal"

  val SPARK_LOCAL_DIR: String = SPARK_PREFIX + "local.dir"

  val HIVE_VAR_PREFIX: Regex = """set:hivevar:([^=]+)""".r
  val USE_DB: Regex = """use:([^=]+)""".r
  val QUEUE: String = SPARK_PREFIX + YARN_PREFIX + "queue"
  val DEPRECATED_QUEUE = "mapred.job.queue.name"
  val HDFS_CLIENT_CACHE: String = SPARK_HADOOP_PREFIX + "fs.hdfs.impl.disable.cache"
  val HDFS_CLIENT_CACHE_DEFAULT = "true"
  val FILE_CLIENT_CACHE: String = SPARK_HADOOP_PREFIX + "fs.file.impl.disable.cache"
  val FILE_CLIENT_CACHE_DEFAULT = "true"

  // Runtime Spark Version
  val SPARK_VERSION: String = org.apache.spark.SPARK_VERSION

  def addShutdownHook(f: () => Unit): Unit = {
    ShutdownHookManager.addShutdownHook(f)
  }

  def initDaemon(log: Logger): Unit = {
    Utils.initDaemon(log)
  }

  def getJobGroupIDKey: String = SparkContext.SPARK_JOB_GROUP_ID

  def exceptionString(e: Throwable): String = {
    Utils.exceptionString(e)
  }

  def getCurrentUserName: String = {
    Utils.getCurrentUserName()
  }

  def getContextOrSparkClassLoader: ClassLoader = {
    Utils.getContextOrSparkClassLoader
  }

  def createTempDir(
      root: String = System.getProperty("java.io.tmpdir"),
      namePrefix: String = "spark"): File = {
    Utils.createTempDir(root, namePrefix)
  }

  def newConfiguration(conf: SparkConf): Configuration = {
    SparkHadoopUtil.get.newConfiguration(conf)
  }

  def localHostName(): String = Utils.localHostName()

  // org.apache.spark.util.VersionUtils: Utilities for working with Spark version strings

  def majorVersion(sparkVersion: String): Int = VersionUtils.majorVersion(sparkVersion)

  def minorVersion(sparkVersion: String): Int = VersionUtils.minorVersion(sparkVersion)

  /**
   * Check whether the runtime version of Spark is >= the specified one.
   */
  def equalOrHigherThan(version: String): Boolean = {
    val tMajor = majorVersion(SPARK_VERSION)
    val tMinor = minorVersion(SPARK_VERSION)
    val sMajor = majorVersion(version)
    val sMinor = minorVersion(version)
    tMajor > sMajor || (tMajor == sMajor && tMinor >= sMinor)
  }

  /**
   * Convert a time parameter such as (50s, 100ms, or 250us) to microseconds for internal use. If
   * no suffix is provided, the passed number is assumed to be in ms.
   */
  def timeStringAsMs(str: String): Long = {
    Utils.timeStringAsMs(str)
  }

  /**
   * Return a well-formed URI for the file described by a user input string.
   *
   * If the supplied path does not contain a scheme, or is a relative path, it will be
   * converted into an absolute path with a file:// scheme.
   */
  def resolveURI(path: String): URI = {
    Utils.resolveURI(path)
  }

  /**
   * Get the path of a temporary directory.  Spark's local directories can be configured through
   * multiple settings, which are used with the following precedence:
   *
   *   - If called from inside of a YARN container, this will return a directory chosen by YARN.
   *   - If the SPARK_LOCAL_DIRS environment variable is set, this will return a directory from it.
   *   - Otherwise, if the spark.local.dir is set, this will return a directory from it.
   *   - Otherwise, this will return java.io.tmpdir.
   *
   * Some of these configuration options might be lists of multiple paths, but this method will
   * always return a single directory.
   */
  def getLocalDir(conf: SparkConf): String = {
    Utils.getLocalDir(conf)
  }

  /**
   * Split a string of potentially quoted arguments from the command line the way that a shell
   * would do it to determine arguments to a command. For example, if the string is 'a "b c" d',
   * then it would be parsed as three arguments: 'a', 'b c' and 'd'.
   */
  def splitCommandString(s: String): Seq[String] = {
    Utils.splitCommandString(s)
  }

  /**
   * Replaces all the {{APP_ID}} occurrences with the App Id.
   */
  def substituteAppId(opt: String, appId: String): String = {
    opt.replace("{{APP_ID}}", appId)
  }

  /**
   * Escapes a string for inclusion in a command line executed by Yarn. Yarn executes commands
   * using either
   *
   * (Unix-based) `bash -c "command arg1 arg2"` and that means plain quoting doesn't really work.
   * The argument is enclosed in single quotes and some key characters are escaped.
   *
   * @param arg A single argument.
   *
   * @return Argument quoted for execution via Yarn's generated shell script.
   */
  def escapeForShell(arg: String): String = {
    val escaped = new StringBuilder("'")
    arg.foreach {
      case '$' => escaped.append("\\$")
      case '"' => escaped.append("\\\"")
      case '\'' => escaped.append("'\\''")
      case c => escaped.append(c)
    }
    escaped.append("'").toString()
  }

  /** Load properties present in the given file. */
  def getPropertiesFromFile(filename: String): Map[String, String] = {
    Utils.getPropertiesFromFile(filename)
  }

  /**
   * Get and set Kyuubi Jar First ClassLoader
   */
  def getAndSetKyuubiFirstClassLoader: MutableURLClassLoader = {
    val url = this.getClass.getProtectionDomain.getCodeSource.getLocation
    val loader = new ChildFirstURLClassLoader(
      Array(url),
      Thread.currentThread.getContextClassLoader)
    Thread.currentThread.setContextClassLoader(loader)
    info(s"Kyuubi first classloader is set to $url")
    loader
  }
}
