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
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
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
    MqttServer server = this.server;
    if (server != null) {
      Async async = context.async();
      this.server = null;
      server.close(context.asyncAssertSuccess(v -> async.countDown()));
      async.await(20_000);
    }
    MqttClient client = this.client;
    if (client != null) {
      Async async = context.async();
      this.client = null;
      client.disconnect(context.asyncAssertSuccess(v -> async.countDown()));
      async.await(20_000);
    }
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
      context.assertEquals(Buffer.buffer("the-message"), will.getWillMessage());
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

  @Test
  public void testToJson(TestContext context) {
    MqttProperties props1 = new MqttProperties();
    props1.add(new MqttProperties.StringProperty(MqttProperties.MqttPropertyType.CONTENT_TYPE.value(), "text/plain"));
    MqttWill will1 = new MqttWill(true, "/sample/topic", Buffer.buffer("sample message"), 2, false, props1);

    JsonObject willJson = will1.toJson();

    MqttWill will2 = new MqttWill(willJson);

    context.assertEquals(will1.getWillMessage(), will2.getWillMessage());
    context.assertEquals(will1.getWillQos(), will2.getWillQos());
    context.assertEquals(will1.getWillTopic(), will2.getWillTopic());

    MqttProperties props2 = will2.getWillProperties();
    context.assertEquals(props1.listAll().size(), props2.listAll().size());
    for(MqttProperties.MqttProperty<?> prop1: props1.listAll()) {
      MqttProperties.MqttProperty<?> prop2 = props2.getProperty(prop1.propertyId());
      context.assertNotNull(prop2);
      context.assertEquals(prop1, prop2);
    }
  }
}
