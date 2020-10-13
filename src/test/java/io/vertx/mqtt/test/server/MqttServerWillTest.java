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

import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;
import io.vertx.mqtt.MqttServer;
import io.vertx.mqtt.MqttServerOptions;
import io.vertx.mqtt.MqttWill;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * MQTT server testing
 */
@RunWith(VertxUnitRunner.class)
public class MqttServerWillTest {

  protected static final String MQTT_SERVER_HOST = "localhost";
  protected static final int MQTT_SERVER_PORT = 1883;

  private Vertx vertx;
  private MqttServer server;
  private MqttClient client;

  @Before
  public void before() {

    this.vertx = Vertx.vertx();
  }

  @After
  public void after(TestContext context) {
    Async async = context.async(2);
    MqttServer server = this.server;
    if (server != null) {
      this.server = null;
      server.close(context.asyncAssertSuccess(v -> async.countDown()));
    }
    MqttClient client = this.client;
    if (client != null) {
      this.client = null;
      try {
        client.disconnect(context.asyncAssertSuccess(v -> async.countDown()));
      } catch (Exception ignore) {
      }
    }
    async.await(20_000);
    this.vertx.close(context.asyncAssertSuccess(v2 -> {
      this.vertx = null;
    }));
  }

  @Test
  public void testNullWill(TestContext context) {
    server = MqttServer.create(this.vertx, new MqttServerOptions().setHost(MQTT_SERVER_HOST).setPort(MQTT_SERVER_PORT));
    server.endpointHandler(endpoint -> {
      MqttWill will = endpoint.will();
      context.assertNull(will.getWillMessage());
      endpoint.accept(false);
    });
    server.listen(context.asyncAssertSuccess(v -> {
      client = MqttClient.create(vertx);
      client.connect(MQTT_SERVER_PORT, MQTT_SERVER_HOST, context.asyncAssertSuccess(ack -> {
      }));
    }));
  }

  @Test
  public void testWill(TestContext context) {
    server = MqttServer.create(this.vertx, new MqttServerOptions().setHost(MQTT_SERVER_HOST).setPort(MQTT_SERVER_PORT));
    server.endpointHandler(endpoint -> {
      MqttWill will = endpoint.will();
      context.assertEquals("the-message", will.getWillMessage());
      context.assertEquals(2, will.getWillQos());
      context.assertEquals("the-message", new String(will.getWillMessageBytes()));
      endpoint.accept(false);
    });
    server.listen(context.asyncAssertSuccess(v -> {
      client = MqttClient.create(vertx, new MqttClientOptions()
        .setWillFlag(true)
        .setWillQoS(2)
        .setWillMessage("the-message")
      );
      client.connect(MQTT_SERVER_PORT, MQTT_SERVER_HOST, context.asyncAssertSuccess(ack -> {
      }));
    }));
  }
}
