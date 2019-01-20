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
package io.vertx.mqtt.test.server;

import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.MqttServerOptions;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.eclipse.paho.client.mqttv3.MqttConnectOptions.MQTT_VERSION_3_1;

/**
 * MQTT server testing about invalid client identifier with 3.1 spec
 * (less then 24 characters)
 */
@RunWith(VertxUnitRunner.class)
public class MqttServerWebSocketClientIdentifierTest extends MqttServerBaseTest {

  @Before
  public void before(TestContext context) {
    this.setUp(context, new MqttServerOptions().setUseWebSocket(true));
  }

  @After
  public void after(TestContext context) {
    this.tearDown(context);
  }

  @Test
  public void testInvalidClientIdentifier(TestContext context) throws Exception {

    MemoryPersistence persistence = new MemoryPersistence();
    MqttClient client = new MqttClient(String.format("ws://%s:%d", MQTT_SERVER_HOST, MQTT_SERVER_PORT), "invalid-id-with-24-chars", persistence);
    MqttConnectOptions options = new MqttConnectOptions();
    options.setMqttVersion(MQTT_VERSION_3_1);

    try {

      client.connect(options);
      context.assertTrue(false);

    } catch (MqttException ignore) {
      context.assertTrue(true);
    }
  }

  @Test
  public void testValidClientIdentifier(TestContext context) throws Exception {

    MemoryPersistence persistence = new MemoryPersistence();
    MqttClient client = new MqttClient(String.format("ws://%s:%d", MQTT_SERVER_HOST, MQTT_SERVER_PORT), "id-madeof-23-characters", persistence);
    MqttConnectOptions options = new MqttConnectOptions();
    options.setMqttVersion(MQTT_VERSION_3_1);

    try {

      client.connect(options);
      context.assertTrue(true);

    } catch (MqttException ignore) {
      context.assertTrue(false);
    }
  }

  @Override
  protected void endpointHandler(MqttEndpoint endpoint, TestContext context) {
    endpoint.accept(false);
  }
}
