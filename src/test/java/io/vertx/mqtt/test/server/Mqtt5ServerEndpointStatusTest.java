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

import io.netty.handler.codec.mqtt.MqttProperties;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.messages.codes.MqttDisconnectReasonCode;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.packet.MqttReturnCode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * MQTT server testing about endpoint status
 */
@RunWith(VertxUnitRunner.class)
public class Mqtt5ServerEndpointStatusTest extends MqttServerBaseTest {

  private static final Logger log = LoggerFactory.getLogger(Mqtt5ServerEndpointStatusTest.class);

  private MqttEndpoint endpoint;

  @Before
  public void before(TestContext context) {

    this.setUp(context);
  }

  @After
  public void after(TestContext context) {

    this.tearDown(context);
  }

  @Test
  public void connected(TestContext context) {

    try {
      MemoryPersistence persistence = new MemoryPersistence();
      MqttClient client = new MqttClient(String.format("tcp://%s:%d", MQTT_SERVER_HOST, MQTT_SERVER_PORT), "12345", persistence);
      client.connect();
      context.assertTrue(client.isConnected() && this.endpoint.isConnected());
    } catch (MqttException e) {
      context.assertTrue(false);
      e.printStackTrace();
    }
  }

  @Test
  public void disconnectedByClient(TestContext context) {

    Async async = context.async();

    try {
      MemoryPersistence persistence = new MemoryPersistence();
      MqttClient client = new MqttClient(String.format("tcp://%s:%d", MQTT_SERVER_HOST, MQTT_SERVER_PORT), "12345", persistence);
      client.connect();
      client.disconnect();

      // give more time to the MqttClient to update its connection state
      this.vertx.setTimer(1000, t1 -> {
        async.complete();
      });

      async.await();

      context.assertTrue(!client.isConnected() && !this.endpoint.isConnected());

    } catch (MqttException e) {
      context.assertTrue(false);
      e.printStackTrace();
    }
  }

  @Test
  public void disconnectedByServer(TestContext context) {

    Async async = context.async();

    try {
      MemoryPersistence persistence = new MemoryPersistence();
      MqttClient client = new MqttClient(String.format("tcp://%s:%d", MQTT_SERVER_HOST, MQTT_SERVER_PORT), "12345", persistence);
      Mqtt5ProbeCallback callback = new Mqtt5ProbeCallback();
      client.setCallback(callback);
      client.connect();

      // the local endpoint closes connection after a few seconds
      this.vertx.setTimer(1000, t -> {
        this.endpoint.disconnect(MqttDisconnectReasonCode.SERVER_SHUTTING_DOWN, MqttProperties.NO_PROPERTIES);

        async.complete();
      });

      async.await();

      context.assertTrue(!client.isConnected() && !this.endpoint.isConnected());
      context.assertNotNull(callback.getDisconnectResponse());
      context.assertEquals(callback.getDisconnectResponse().getReturnCode(), MqttReturnCode.RETURN_CODE_SERVER_SHUTTING_DOWN);

    } catch (MqttException e) {
      context.assertTrue(false);
      e.printStackTrace();
    }
  }

  @Override
  protected void endpointHandler(MqttEndpoint endpoint, TestContext context) {

    this.endpoint = endpoint;

    endpoint.disconnectHandler(v -> {

      log.info("MQTT remote client disconnected");

    });

    endpoint.accept(false);
  }
}
