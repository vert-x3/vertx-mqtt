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

package io.vertx.mqtt.test;

import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.mqtt.MqttServerOptions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * MQTT server testing about using SSL/TLS
 */
@RunWith(VertxUnitRunner.class)
public class MqttSslTest extends MqttBaseTest {

  @Before
  public void before(TestContext context) {

    PemKeyCertOptions pemKeyCertOptions = new PemKeyCertOptions()
      .setKeyPath("tls/server-key.pem")
      .setCertPath("tls/server-cert.pem");

    MqttServerOptions options = new MqttServerOptions()
      .setPort(MQTT_SERVER_TLS_PORT)
      .setKeyCertOptions(pemKeyCertOptions)
      .setSsl(true);

    this.setUp(context, options);
  }

  @Test
  public void connection(TestContext context) {
    // TODO:
  }

  @After
  public void after(TestContext context) {

    this.tearDown(context);
  }

}
