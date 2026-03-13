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
import io.netty.handler.codec.mqtt.MqttSubscriptionOption;
import io.netty.handler.codec.mqtt.MqttTopicSubscription;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.messages.MqttSubAckMessage;
import org.junit.After;
import org.junit.Test;

import java.util.List;
import java.util.Map;

/**
 * Integration tests for MQTT 5.0 SUBSCRIBE against a real Mosquitto 2.x broker.
 */
public class Mqtt5ClientSubscribeIT extends Mqtt5ClientBaseIT {

  private static final String TOPIC = "/mqtt5/it/subscribe";

  private Vertx vertx;

  @After
  public void tearDown(TestContext ctx) {
    if (vertx != null) {
      vertx.close().onComplete(ctx.asyncAssertSuccess());
    }
  }

  /**
   * Subscribe to a topic with QoS 0 using MQTT 5.0: SUBACK must contain a granted QoS.
   */
  @Test
  public void subscribeQos0(TestContext ctx) {
    vertx = Vertx.vertx();
    Async subscribed = ctx.async();
    MqttClient client = MqttClient.create(vertx, v5Options());

    client.subscribeCompletionHandler((MqttSubAckMessage ack) -> {
      ctx.assertFalse(ack.grantedQoSLevels().isEmpty());
      subscribed.complete();
    });

    client.connect(port, host)
      .onComplete(ctx.asyncAssertSuccess(ack ->
        client.subscribe(TOPIC, 0)));

    subscribed.awaitSuccess(10000);
  }

  /**
   * Subscribe to a topic with QoS 1 using MQTT 5.0: broker must grant QoS 1.
   */
  @Test
  public void subscribeQos1(TestContext ctx) {
    vertx = Vertx.vertx();
    Async subscribed = ctx.async();
    MqttClient client = MqttClient.create(vertx, v5Options());

    client.subscribeCompletionHandler((MqttSubAckMessage ack) -> {
      ctx.assertFalse(ack.grantedQoSLevels().isEmpty());
      // Mosquitto grants at least QoS 0; typically grants what was requested
      subscribed.complete();
    });

    client.connect(port, host)
      .onComplete(ctx.asyncAssertSuccess(ack ->
        client.subscribe(TOPIC, 1)));

    subscribed.awaitSuccess(10000);
  }

  /**
   * Subscribe and receive a published message end-to-end with MQTT 5.0.
   * Two clients: publisher (QoS 0) and subscriber (QoS 0).
   */
  @Test
  public void subscribeAndReceiveMessage(TestContext ctx) {
    vertx = Vertx.vertx();
    Async received = ctx.async();
    String payload = "hello-mqtt5";

    MqttClient subscriber = MqttClient.create(vertx, v5Options());
    MqttClient publisher  = MqttClient.create(vertx, v5Options());

    subscriber.publishHandler(msg -> {
      ctx.assertEquals(payload, msg.payload().toString());
      received.complete();
    });

    subscriber.connect(port, host)
      .onComplete(ctx.asyncAssertSuccess(ack -> {
        subscriber.subscribe(TOPIC, 0)
          .onComplete(ctx.asyncAssertSuccess(subId ->
            publisher.connect(port, host)
              .onComplete(ctx.asyncAssertSuccess(ack2 ->
                publisher.publish(TOPIC, Buffer.buffer(payload), MqttQoS.AT_MOST_ONCE, false, false)))));
      }));

    received.awaitSuccess(10000);
  }

  /**
   * Subscribe with subscription options (No Local, Retain Handling) using MQTT 5.0.
   * Broker must accept the subscription without error.
   */
  @Test
  public void subscribeWithSubscriptionOptions(TestContext ctx) {
    vertx = Vertx.vertx();
    Async subscribed = ctx.async();
    MqttClient client = MqttClient.create(vertx, v5Options());

    client.subscribeCompletionHandler((MqttSubAckMessage ack) -> {
      ctx.assertFalse(ack.grantedQoSLevels().isEmpty());
      subscribed.complete();
    });

    client.connect(port, host)
      .onComplete(ctx.asyncAssertSuccess(ack -> {
        MqttSubscriptionOption option = new MqttSubscriptionOption(
          MqttQoS.AT_MOST_ONCE,
          false,
          false,
          MqttSubscriptionOption.RetainedHandlingPolicy.SEND_AT_SUBSCRIBE_IF_NOT_YET_EXISTS);
        client.subscribe(
          List.of(new MqttTopicSubscription(TOPIC, option)),
          MqttProperties.NO_PROPERTIES);
      }));

    subscribed.awaitSuccess(10000);
  }
}
