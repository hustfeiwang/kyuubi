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

package yaooqinn.kyuubi.operation

import org.apache.hadoop.security.UserGroupInformation
import org.apache.hive.service.cli.thrift.TProtocolVersion
import org.apache.spark.{KyuubiSparkUtil, SparkConf, SparkFunSuite}
import org.scalatest.mock.MockitoSugar

import yaooqinn.kyuubi.session.{IKyuubiSession, SessionManager}

abstract class AbstractKyuubiOperationSuite extends SparkFunSuite with MockitoSugar {

  val conf = new SparkConf(loadDefaults = true).setAppName("operation test")
  KyuubiSparkUtil.setupCommonConfig(conf)
  conf.remove(KyuubiSparkUtil.CATALOG_IMPL)
  conf.setMaster("local")
  var sessionMgr: SessionManager = _
  val proto = TProtocolVersion.HIVE_CLI_SERVICE_PROTOCOL_V8
  val user = UserGroupInformation.getCurrentUser
  val userName = user.getShortUserName
  val passwd = ""
  val statement = "show tables"
  var session: IKyuubiSession = _

  override protected def beforeAll(): Unit = {
    sessionMgr = new SessionManager()
    sessionMgr.init(conf)
    sessionMgr.start()
  }

  override protected def afterAll(): Unit = {
    session.close()
    session = null
    sessionMgr.stop()
  }

  test("testCancel") {
    val op = sessionMgr.getOperationMgr.newExecuteStatementOperation(session, statement)
    assert(op.getStatus.getState === INITIALIZED)
    op.cancel()
    assert(op.getStatus.getState === CANCELED)
  }

  test("testGetHandle") {
    val op = sessionMgr.getOperationMgr.newExecuteStatementOperation(session, statement)
    assert(!op.getHandle.isHasResultSet)
    assert(!op.getHandle.toTOperationHandle.isHasResultSet)
    op.getHandle.setHasResultSet(true)
    assert(op.getHandle.isHasResultSet)
    assert(op.getHandle.toTOperationHandle.isHasResultSet)
    assert(op.getHandle.getProtocolVersion === TProtocolVersion.HIVE_CLI_SERVICE_PROTOCOL_V8)
    assert(op.getHandle.getOperationType === EXECUTE_STATEMENT)
  }

  test("testGetStatus") {
    val op = sessionMgr.getOperationMgr.newExecuteStatementOperation(session, statement)
    assert(op.getStatus.getState === INITIALIZED)
    assert(op.getStatus.getOperationException === null)
  }

  test("testIsTimedOut") {
    val op = sessionMgr.getOperationMgr.newExecuteStatementOperation(session, statement)
    assert(!op.isTimedOut)
  }

  test("testGetProtocolVersion") {
    val op = sessionMgr.getOperationMgr.newExecuteStatementOperation(session, statement)
    assert(op.getProtocolVersion === proto)
  }

  test("testGetOperationLog") {
    // TODO
  }

  test("testClose") {
    val op = sessionMgr.getOperationMgr.newExecuteStatementOperation(session, statement)
    assert(op.getStatus.getState === INITIALIZED)
    op.close()
    assert(op.getStatus.getState === CLOSED)
  }

  test("testGetSession") {
    val op = sessionMgr.getOperationMgr.newExecuteStatementOperation(session, statement)
    val s = op.getSession
    assert(s == session)
    assert(s.getUserName === userName)
  }

  test("is closed or canceled") {
    val op = sessionMgr.getOperationMgr.newExecuteStatementOperation(session, statement)
    assert(!op.isClosedOrCanceled)
    op.cancel()
    assert(op.isClosedOrCanceled)
    op.close()
    assert(op.isClosedOrCanceled)
    val op2 = sessionMgr.getOperationMgr.newExecuteStatementOperation(session, statement)
    op2.close()
    assert(op2.isClosedOrCanceled)
    val op3 = sessionMgr.getOperationMgr.newExecuteStatementOperation(session, null)
    op3.cancel()
    op3.close()
    assert(op3.isClosedOrCanceled)
  }
}
