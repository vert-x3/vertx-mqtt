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

import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.mqtt.MqttClient;
import org.junit.After;
import org.junit.Test;

/**
 * Integration tests for MQTT 5.0 PUBLISH against a real Mosquitto 2.x broker.
 */
public class Mqtt5ClientPublishIT extends Mqtt5ClientBaseIT {

  private static final String TOPIC = "/mqtt5/it/publish";

  private Vertx vertx;

  @After
  public void tearDown(TestContext ctx) {
    if (vertx != null) {
      vertx.close().onComplete(ctx.asyncAssertSuccess());
    }
  }

  /**
   * Publish QoS 0 with MQTT 5.0: fire and forget, future completes after send.
   */
  @Test
  public void publishQos0(TestContext ctx) {
    vertx = Vertx.vertx();
    MqttClient client = MqttClient.create(vertx, v5Options());

    client.connect(port, host)
      .onComplete(ctx.asyncAssertSuccess(ack ->
        client.publish(TOPIC, Buffer.buffer("qos0-payload"), MqttQoS.AT_MOST_ONCE, false, false)
          .onComplete(ctx.asyncAssertSuccess(id ->
            client.disconnect().onComplete(ctx.asyncAssertSuccess())))));
  }

  /**
   * Publish QoS 1 with MQTT 5.0: broker sends PUBACK, future completes after PUBACK.
   */
  @Test
  public void publishQos1(TestContext ctx) {
    vertx = Vertx.vertx();
    Async published = ctx.async();
    MqttClient client = MqttClient.create(vertx, v5Options());

    client.publishCompletionHandler(id -> published.complete());

    client.connect(port, host)
      .onComplete(ctx.asyncAssertSuccess(ack ->
        client.publish(TOPIC, Buffer.buffer("qos1-payload"), MqttQoS.AT_LEAST_ONCE, false, false)));

    published.awaitSuccess(10000);
  }

  /**
   * Publish QoS 2 with MQTT 5.0: full PUBREC/PUBREL/PUBCOMP handshake with broker.
   */
  @Test
  public void publishQos2(TestContext ctx) {
    vertx = Vertx.vertx();
    Async published = ctx.async();
    MqttClient client = MqttClient.create(vertx, v5Options());

    client.publishCompletionHandler(id -> published.complete());

    client.connect(port, host)
      .onComplete(ctx.asyncAssertSuccess(ack ->
        client.publish(TOPIC, Buffer.buffer("qos2-payload"), MqttQoS.EXACTLY_ONCE, false, false)));

    published.awaitSuccess(10000);
  }

  /**
   * Publish QoS 1 with MQTT 5.0 user properties: broker accepts the message.
   */
  @Test
  public void publishWithUserProperties(TestContext ctx) {
    vertx = Vertx.vertx();
    Async published = ctx.async();
    MqttClient client = MqttClient.create(vertx, v5Options());

    client.publishCompletionHandler(id -> published.complete());

    client.connect(port, host)
      .onComplete(ctx.asyncAssertSuccess(ack -> {
        MqttProperties props = new MqttProperties();
        props.add(new MqttProperties.UserProperties(
          java.util.List.of(new MqttProperties.StringPair("source", "vertx-mqtt-test"))));
        client.publish(TOPIC, Buffer.buffer("with-user-props"), MqttQoS.AT_LEAST_ONCE, false, false, props);
      }));

    published.awaitSuccess(10000);
  }

  /**
   * Publish QoS 0 with CONTENT_TYPE property: broker accepts the message.
   */
  @Test
  public void publishWithContentType(TestContext ctx) {
    vertx = Vertx.vertx();
    MqttClient client = MqttClient.create(vertx, v5Options());

    client.connect(port, host)
      .onComplete(ctx.asyncAssertSuccess(ack -> {
        MqttProperties props = new MqttProperties();
        props.add(new MqttProperties.StringProperty(MqttProperties.CONTENT_TYPE, "application/json"));
        client.publish(TOPIC, Buffer.buffer("{\"test\":true}"), MqttQoS.AT_MOST_ONCE, false, false, props)
          .onComplete(ctx.asyncAssertSuccess(id ->
            client.disconnect().onComplete(ctx.asyncAssertSuccess())));
      }));
  }
}
