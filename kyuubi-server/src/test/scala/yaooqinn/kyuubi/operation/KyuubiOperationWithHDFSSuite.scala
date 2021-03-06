/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package yaooqinn.kyuubi.operation

import java.io.File

import org.apache.hadoop.fs.Path
import org.apache.hadoop.hdfs.{HdfsConfiguration, MiniDFSCluster}
import org.apache.spark.sql.catalyst.catalog.FunctionResource
import org.apache.spark.sql.execution.SparkSqlParser
import org.apache.spark.sql.internal.SQLConf

import yaooqinn.kyuubi.utils.ReflectUtils

class KyuubiOperationWithHDFSSuite extends KyuubiOperationSuite {
  val hdfsConf = new HdfsConfiguration
  var cluster: MiniDFSCluster = new MiniDFSCluster.Builder(hdfsConf).build()
  cluster.waitClusterUp()
  val fs = cluster.getFileSystem
  val homeDirectory: Path = fs.getHomeDirectory
  private val fileName = "example-1.0.0-SNAPSHOT.jar"
  private val remoteUDFFile = new Path(homeDirectory, fileName)

  override def beforeAll(): Unit = {
    val file = new File(this.getClass.getProtectionDomain.getCodeSource.getLocation + fileName)
    val localUDFFile = new Path(file.getPath)
    fs.copyFromLocalFile(localUDFFile, remoteUDFFile)
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    fs.delete(remoteUDFFile, true)
    fs.close()
    cluster.shutdown()
    super.afterAll()
  }

  test("transform logical plan") {
    val op = sessionMgr.getOperationMgr.newExecuteStatementOperation(session, statement)
    val parser = new SparkSqlParser(new SQLConf)
    val plan0 = parser.parsePlan(
      s"create temporary function a as 'a.b.c' using file '$remoteUDFFile'")
    val plan1 = op.transform(plan0)
    assert(plan0 === plan1)
    assert(
      ReflectUtils.getFieldValue(plan1, "resources").asInstanceOf[Seq[FunctionResource]].isEmpty)

    val plan2 = parser.parsePlan(
      s"create temporary function a as 'a.b.c' using jar '$remoteUDFFile'")
    val plan3 = op.transform(plan2)
    assert(plan3 === plan2)
    assert(
      ReflectUtils.getFieldValue(plan3, "resources").asInstanceOf[Seq[FunctionResource]].isEmpty)
  }

}
