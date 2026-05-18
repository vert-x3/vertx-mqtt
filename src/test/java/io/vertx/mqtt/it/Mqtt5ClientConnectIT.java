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

package io.vertx.mqtt.it;

import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.TestContext;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;
import io.vertx.mqtt.messages.MqttConnAckMessage;
import org.junit.After;
import org.junit.Test;

/**
 * Integration tests for MQTT 5.0 CONNECT / CONNACK against a real Mosquitto 2.x broker.
 */
public class Mqtt5ClientConnectIT extends Mqtt5ClientBaseIT {

  private Vertx vertx;

  @After
  public void tearDown(TestContext ctx) {
    if (vertx != null) {
      vertx.close().onComplete(ctx.asyncAssertSuccess());
    }
  }

  /**
   * Connect with MQTT 5.0 protocol version: broker must accept the connection and
   * return CONNECTION_ACCEPTED.
   */
  @Test
  public void connectWithMqtt5Version(TestContext ctx) {
    vertx = Vertx.vertx();
    MqttClient client = MqttClient.create(vertx, v5Options());

    client.connect(port, host)
      .onComplete(ctx.asyncAssertSuccess(ack -> {
        ctx.assertEquals(MqttConnectReturnCode.CONNECTION_ACCEPTED, ack.code());
        client.disconnect().onComplete(ctx.asyncAssertSuccess());
      }));
  }

  /**
   * Connect with MQTT 5.0 and SESSION_EXPIRY_INTERVAL: broker must accept and
   * return a valid CONNACK (Mosquitto echoes or ignores this property, but does not reject it).
   */
  @Test
  public void connectWithSessionExpiryInterval(TestContext ctx) {
    vertx = Vertx.vertx();
    MqttClientOptions opts = v5Options();
    opts.setSessionExpireInterval(60L);

    MqttClient client = MqttClient.create(vertx, opts);
    client.connect(port, host)
      .onComplete(ctx.asyncAssertSuccess(ack -> {
        ctx.assertEquals(MqttConnectReturnCode.CONNECTION_ACCEPTED, ack.code());
        client.disconnect().onComplete(ctx.asyncAssertSuccess());
      }));
  }

  /**
   * Connect and verify that Mosquitto 2.x provides server-specific CONNACK properties.
   * At minimum, Mosquitto 2.x sends RECEIVE_MAXIMUM and optionally TOPIC_ALIAS_MAXIMUM.
   */
  @Test
  public void connAckContainsServerProperties(TestContext ctx) {
    vertx = Vertx.vertx();
    MqttClient client = MqttClient.create(vertx, v5Options());

    client.connect(port, host)
      .onComplete(ctx.asyncAssertSuccess(ack -> {
        // Mosquitto 2.x always includes RECEIVE_MAXIMUM in v5 CONNACK
        ctx.assertNotNull(ack.receiveMaximum(), "CONNACK must contain RECEIVE_MAXIMUM");
        client.disconnect().onComplete(ctx.asyncAssertSuccess());
      }));
  }

  /**
   * Connect and disconnect with MQTT 5.0 DISCONNECT reason code NORMAL.
   */
  @Test
  public void connectAndDisconnectWithReasonCode(TestContext ctx) {
    vertx = Vertx.vertx();
    MqttClient client = MqttClient.create(vertx, v5Options());

    client.connect(port, host)
      .onComplete(ctx.asyncAssertSuccess(ack -> {
        ctx.assertEquals(MqttConnectReturnCode.CONNECTION_ACCEPTED, ack.code());
        client.disconnect(
          io.vertx.mqtt.messages.codes.MqttDisconnectReasonCode.NORMAL,
          io.netty.handler.codec.mqtt.MqttProperties.NO_PROPERTIES)
          .onComplete(ctx.asyncAssertSuccess());
      }));
  }
}
