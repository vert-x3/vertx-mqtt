/*
 * Copyright 2016 Red Hat Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.vertx.mqtt.test.client;


import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

class TestUtil {

  private static final Logger log = LoggerFactory.getLogger(TestUtil.class);

  static final String BROKER_ADDRESS;

  static {
    InputStream inputStream;
    Properties properties = null;

    try {
      inputStream = new FileInputStream("./target/project.properties");
      properties = new Properties();
      properties.load(inputStream);
      log.debug("Properties was loaded successfully");
    } catch (IOException e) {
      log.error("Properties was not loaded, it should be generated during mvn verify -Plocal_test", e);
    }

    BROKER_ADDRESS = properties.getProperty("SERVER");
  }
}
