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
package org.apache.livy.rsc.driver;

import org.apache.livy.Job;
import org.apache.livy.JobContext;
import org.apache.spark.scheduler.SparkListener;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ConfigSessionConfJob implements Job<Object> {

  private final Map<String, String> conf;
  private final UUID uuid;

  ConfigSessionConfJob() {
    this(null, null);
}

  public ConfigSessionConfJob(Map<String, String> conf, UUID uuid) {
    this.conf = conf;
    this.uuid = uuid;
}

  @Override
  public Object call(JobContext jc) throws Exception {
    JobContextImpl jobContextImpl = (JobContextImpl)jc;
    jobContextImpl.configSessionConf(uuid, conf);
    return null;
  }
}
