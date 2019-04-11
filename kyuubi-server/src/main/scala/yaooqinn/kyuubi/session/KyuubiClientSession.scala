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

package yaooqinn.kyuubi.session

import java.io.{File, IOException}

import org.apache.commons.io.FileUtils
import org.apache.hadoop.fs.FileSystem
import org.apache.hive.service.cli.thrift.TProtocolVersion
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.types.StructType

import yaooqinn.kyuubi.KyuubiSQLException
import yaooqinn.kyuubi.cli._
import yaooqinn.kyuubi.operation.{OperationHandle, OperationManager}
import yaooqinn.kyuubi.schema.RowSet
import yaooqinn.kyuubi.spark.SparkSessionWithUGI

/**
 * An Execution Session with [[SparkSession]] instance inside, which shares [[SparkContext]]
 * with other sessions create by the same user.
 *
 * One user, one [[SparkContext]]
 * One user, multi [[KyuubiClientSession]]s
 *
 * One [[KyuubiClientSession]], one [[SparkSession]]
 * One [[SparkContext]], multi [[SparkSession]]s
 *
 */
private[kyuubi] class KyuubiClientSession(
    protocol: TProtocolVersion,
    username: String,
    password: String,
    conf: SparkConf,
    ipAddress: String,
    withImpersonation: Boolean,
    sessionManager: SessionManager,
    operationManager: OperationManager)
  extends AbstractKyuubiSession(protocol,
    username, password, conf, ipAddress, withImpersonation, sessionManager, operationManager) {

  private var _isOperationLogEnabled = false
  private var sessionLogDir: File = _
  private var sessionResourcesDir: File = _

  private val sparkSessionWithUGI =
    new SparkSessionWithUGI(sessionUGI, conf, sessionManager.getCacheMgr)

  private def cleanupSessionLogDir(): Unit = {
    if (_isOperationLogEnabled) {
      try {
        FileUtils.forceDelete(sessionLogDir)
      } catch {
        case e: Exception =>
          error("Failed to cleanup session log dir: " + sessionLogDir, e)
      }
    }
  }

  def sparkSession: SparkSession = this.sparkSessionWithUGI.sparkSession

  @throws[KyuubiSQLException]
  override def open(sessionConf: Map[String, String]): Unit = {
    sparkSessionWithUGI.init(sessionConf)
    lastAccessTime = System.currentTimeMillis
    lastIdleTime = lastAccessTime
  }

  override def getInfo(getInfoType: GetInfoType): GetInfoValue = {
    acquire(true)
    try {
      getInfoType match {
        case GetInfoType.SERVER_NAME => new GetInfoValue("Kyuubi Server")
        case GetInfoType.DBMS_NAME => new GetInfoValue("Spark SQL")
        case GetInfoType.DBMS_VERSION =>
          new GetInfoValue(sparkSession.version)
        case _ =>
          throw new KyuubiSQLException("Unrecognized GetInfoType value " + getInfoType.toString)
      }
    } finally {
      release(true)
    }
  }

  /**
    * close the session
    */
  @throws[KyuubiSQLException]
  override def close(): Unit = {
    acquire(true)
    try {
      // Iterate through the opHandles and close their operations
      opHandleSet.foreach(closeOperation)
      opHandleSet.clear()
      // Cleanup session log directory.
      cleanupSessionLogDir()
    } finally {
      release(true)
      try {
        FileSystem.closeAllForUGI(sessionUGI)
      } catch {
        case ioe: IOException =>
          throw new KyuubiSQLException("Could not clean up file-system handles for UGI "
            + sessionUGI, ioe)
      }
    }
  }

  override def getResultSetMetadata(opHandle: OperationHandle): StructType = {
    acquire(true)
    try {
      operationManager.getResultSetSchema(opHandle)
    } finally {
      release(true)
    }
  }

  @throws[KyuubiSQLException]
  override def fetchResults(
      opHandle: OperationHandle,
      orientation: FetchOrientation,
      maxRows: Long,
      fetchType: FetchType): RowSet = {
    acquire(true)
    try {
      fetchType match {
        case FetchType.QUERY_OUTPUT =>
          operationManager.getOperationNextRowSet(opHandle, orientation, maxRows)
        case _ =>
          operationManager.getOperationLogRowSet(opHandle, orientation, maxRows)
      }
    } finally {
      release(true)
    }
  }

  /**
    * Check whether operation logging is enabled and session dir is created successfully
    */
  def isOperationLogEnabled: Boolean = _isOperationLogEnabled

  /**
    * Get the session log dir, which is the parent dir of operation logs
    *
    * @return a file representing the parent directory of operation logs
    */
  def getSessionLogDir: File = sessionLogDir

  /**
    * Set the session log dir, which is the parent dir of operation logs
    *
    * @param operationLogRootDir the parent dir of the session dir
    */
  def setOperationLogSessionDir(operationLogRootDir: File): Unit = {
    sessionLogDir = new File(operationLogRootDir,
      username + File.separator + sessionHandle.getHandleIdentifier.toString)
    _isOperationLogEnabled = true
    if (!sessionLogDir.exists) {
      if (!sessionLogDir.mkdirs) {
        warn("Unable to create operation log session directory: "
          + sessionLogDir.getAbsolutePath)
        _isOperationLogEnabled = false
      }
    }
    if (_isOperationLogEnabled) {
      info("Operation log session directory is created: " + sessionLogDir.getAbsolutePath)
    }
  }

  /**
    * Get the session resource dir, which is the parent dir of operation logs
    *
    * @return a file representing the parent directory of operation logs
    */
  def getResourcesSessionDir: File = sessionResourcesDir

  /**
    * Set the session log dir, which is the parent dir of operation logs
    *
    * @param resourcesRootDir the parent dir of the session dir
    */
  def setResourcesSessionDir(resourcesRootDir: File): Unit = {
    sessionResourcesDir = new File(resourcesRootDir,
      username + File.separator + sessionHandle.getHandleIdentifier.toString + "_resources")
    if (sessionResourcesDir.exists() && !sessionResourcesDir.isDirectory) {
      throw new RuntimeException("The resources directory exists but is not a directory: " +
        sessionResourcesDir)
    }

    if (!sessionResourcesDir.exists() && !sessionResourcesDir.mkdirs()) {
      throw new RuntimeException("Couldn't create session resources directory " +
        sessionResourcesDir)
    }
  }
}
