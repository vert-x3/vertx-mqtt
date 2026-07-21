/*
 * Copyright 2026 Red Hat Inc.
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
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;
import io.vertx.mqtt.MqttException;
import io.vertx.mqtt.MqttServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tests for client-side QoS flow-control behaviour.
 */
@RunWith(VertxUnitRunner.class)
public class MqttClientQoSTest {

  private Vertx vertx;
  private MqttServer server;

  @Before
  public void before(TestContext ctx) {
    vertx = Vertx.vertx();
  }

  @After
  public void after(TestContext ctx) {
    if (server != null) {
      server.close(ctx.asyncAssertSuccess(v -> vertx.close(ctx.asyncAssertSuccess())));
    } else {
      vertx.close(ctx.asyncAssertSuccess());
    }
  }

  /**
   * A rogue broker sends 3 QoS 2 PUBLISH messages while withholding PUBREL indefinitely.
   * With maxInflightQueue=2 the client must close the connection on the 3rd message and
   * deliver MQTT_INFLIGHT_QUEUE_FULL to the exceptionHandler.
   */
  @Test
  public void testInboundQos2LimitMqtt311ClosesConnection(TestContext ctx) {
    server = MqttServer.create(vertx);
    Async async = ctx.async();

    server.endpointHandler(endpoint -> {
      endpoint.accept(false);
      // Never send PUBREL — keep slots occupied permanently.
      endpoint.publishReceivedHandler(messageId -> {});
      // Flood the client with 3 QoS 2 PUBLISH messages.
      for (int i = 0; i < 3; i++) {
        endpoint.publish("test/topic", Buffer.buffer("payload"), MqttQoS.EXACTLY_ONCE, false, false);
      }
    });

    Async serverReady = ctx.async();
    server.listen(ctx.asyncAssertSuccess(s -> serverReady.complete()));
    serverReady.awaitSuccess(5000);

    MqttClientOptions options = new MqttClientOptions().setMaxInflightQueue(2);
    MqttClient client = MqttClient.create(vertx, options);

    client.exceptionHandler(err -> {
      ctx.assertTrue(err instanceof MqttException, "Expected MqttException, got " + err.getClass().getName());
      ctx.assertEquals(MqttException.MQTT_INFLIGHT_QUEUE_FULL, ((MqttException) err).code());
      async.complete();
    });

    client.connect(MqttClientOptions.DEFAULT_PORT, "localhost", ctx.asyncAssertSuccess(ack -> {}));

    async.awaitSuccess(10000);
  }

  /**
   * After the broker releases one slot via PUBREL→PUBCOMP, the client must accept a
   * subsequent QoS 2 PUBLISH without closing the connection.
   *
   * <p>Sequence:
   * <ol>
   *   <li>Server sends msg1 and msg2 (fills queue of 2).</li>
   *   <li>Client sends PUBREC for both; server releases msg1 via PUBREL.</li>
   *   <li>Client processes PUBREL(msg1): removes slot, sends PUBCOMP.</li>
   *   <li>Server receives PUBCOMP(msg1) and sends msg3.</li>
   *   <li>Client must accept msg3 (queue size now 1 < 2); server receives PUBREC(msg3) → test passes.</li>
   * </ol>
   */
  @Test
  public void testInboundQos2SlotFreedAfterPubrel(TestContext ctx) {
    server = MqttServer.create(vertx);
    Async async = ctx.async();

    AtomicInteger firstReceivedId = new AtomicInteger(-1);
    AtomicInteger pubrecCount = new AtomicInteger();

    server.endpointHandler(endpoint -> {
      endpoint.accept(false);

      endpoint.publishReceivedHandler(messageId -> {
        int count = pubrecCount.incrementAndGet();
        if (count == 1) {
          firstReceivedId.set(messageId);
        } else if (count == 2) {
          // Both slots occupied; free the first one.
          endpoint.publishRelease(firstReceivedId.get());
        } else if (count == 3) {
          // msg3 was accepted by the client — slot was correctly freed.
          async.complete();
        }
      });

      endpoint.publishCompletionHandler(messageId -> {
        // Slot for msg1 is now free; publish a third message.
        endpoint.publish("test/topic", Buffer.buffer("third"), MqttQoS.EXACTLY_ONCE, false, false);
      });

      // Publish two messages to fill the client's inbound QoS 2 queue.
      endpoint.publish("test/topic", Buffer.buffer("first"), MqttQoS.EXACTLY_ONCE, false, false);
      endpoint.publish("test/topic", Buffer.buffer("second"), MqttQoS.EXACTLY_ONCE, false, false);
    });

    Async serverReady = ctx.async();
    server.listen(ctx.asyncAssertSuccess(s -> serverReady.complete()));
    serverReady.awaitSuccess(5000);

    MqttClientOptions options = new MqttClientOptions().setMaxInflightQueue(2);
    MqttClient client = MqttClient.create(vertx, options);

    client.exceptionHandler(err -> ctx.fail("Unexpected exception: " + err.getMessage()));

    client.connect(MqttClientOptions.DEFAULT_PORT, "localhost", ctx.asyncAssertSuccess(ack -> {}));

    async.awaitSuccess(10000);
  }
}
