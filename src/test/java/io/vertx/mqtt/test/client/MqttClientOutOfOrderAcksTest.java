/*
 * Copyright 2017 Red Hat Inc.
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

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.MqttServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.LinkedList;
import java.util.Queue;


/**
 * MQTT client testing on messages acknowledged out of order
 */
@RunWith(VertxUnitRunner.class)
public class MqttClientOutOfOrderAcksTest {
  private static final Logger log = LoggerFactory.getLogger(MqttClientOutOfOrderAcksTest.class);

  private static final String MQTT_TOPIC = "/my_topic";
  private static final String MQTT_MESSAGE = "Hello Vert.x MQTT Client";

  Vertx vertx = Vertx.vertx();
  MqttServer server;
  TestContext context;

  @Test
  public void publishQoS1OutOfOrderAcks(TestContext context) throws InterruptedException {
    clientSendThreePublishMessages(MqttQoS.AT_LEAST_ONCE, context);
  }

  @Test
  public void publishQoS2OutOfOrderAcks(TestContext context) throws InterruptedException {
    clientSendThreePublishMessages(MqttQoS.EXACTLY_ONCE, context);
  }

  private void clientSendThreePublishMessages(MqttQoS mqttQoS, TestContext context) {
    Async async = context.async(3);
    MqttClient client = MqttClient.create(vertx);

    Queue<Integer> expectOrder = new LinkedList<>();
    // order we expect to receive acknowledgment for published message
    expectOrder.add(2);
    expectOrder.add(1);
    expectOrder.add(3);


    client.publishCompletionHandler(h -> {
      context.assertTrue(h.intValue() == expectOrder.poll());
      log.info("[CLIENT] Publish completed for message with id: " + h);
      async.countDown();
    });

    client.connect(MqttClientOptions.DEFAULT_PORT, MqttClientOptions.DEFAULT_HOST, c -> {

      // publish QoS = 1 message three times
      for (int i = 0; i < 3; i++)
        client.publish(MQTT_TOPIC,
          Buffer.buffer(MQTT_MESSAGE.getBytes()),
          mqttQoS,
          false,
          false, h -> log.info("[CLIENT] publishing message id = " + h.result()));
    });

    async.await();
    client.disconnect();
  }


  @Before
  public void before() {
    server = MqttServer.create(vertx);
    server.endpointHandler(MqttClientOutOfOrderAcksTest::serverLogic).listen(ar -> {
      if (ar.succeeded()) {
        log.info("[SERVER] MQTT server listening on port " + ar.result().actualPort());
      } else {
        log.error("[SERVER] Error starting MQTT server", ar.cause());
        System.exit(1);
      }
    });

    server.exceptionHandler(t -> context.assertTrue(false));
  }

  @After
  public void after() {
    this.server.close();
    this.vertx.close();
  }

  private static void serverLogic(MqttEndpoint mqttEndpoint) {
    log.info("[SERVER] Client connected");

    mqttEndpoint.publishHandler(p -> {
      log.info("[SERVER] Received PUBLISH with message id = " + p.messageId());

      if (p.qosLevel().equals(MqttQoS.EXACTLY_ONCE))
        // when received last PUBLISH message to acknowledge
        if (p.messageId() == 3) {
          // send PUBREC in order 3 --> 2 --> 1 -->
          // the order here is different from QoS 1 because we want to add more shuffling
          mqttEndpoint.publishReceived(3);
          mqttEndpoint.publishReceived(2);
          mqttEndpoint.publishReceived(1);
        }

      if (p.qosLevel().equals(MqttQoS.AT_LEAST_ONCE))
        // when received last PUBLISH message to acknowledge
        if (p.messageId() == 3) {
          //puback in order 2 --> 1 --> 3 -->
          mqttEndpoint.publishAcknowledge(2);
          mqttEndpoint.publishAcknowledge(1);
          mqttEndpoint.publishAcknowledge(3);
        }
    });

    mqttEndpoint.publishReleaseHandler(pr -> {
      log.info("[SERVER] Receive PUBREL with message id = " + pr);
      // we use 1 here because the order of responded by client messages is  3->2->1
      // so we are waiting here for receiving the last message with id=1 PUBREL message from the client
      if (pr == 1) {
        // send PUBREL in order 2 --> 1 --> 3 -->
        mqttEndpoint.publishComplete(2);
        mqttEndpoint.publishComplete(1);
        mqttEndpoint.publishComplete(3);
      }
    });

    mqttEndpoint.disconnectHandler(d -> log.info("[SERVER] Client disconnected"));
    mqttEndpoint.accept(false);
  }

}
