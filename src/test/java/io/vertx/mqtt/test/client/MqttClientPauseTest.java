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

package io.vertx.mqtt.test.client;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;
import io.vertx.mqtt.MqttServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * MQTT client pause tests.
 */
@RunWith(VertxUnitRunner.class)
public class MqttClientPauseTest {

  private Vertx vertx;
  private MqttServer server;

  @Before
  public void before(TestContext ctx) {
    vertx = Vertx.vertx();
    server = MqttServer.create(vertx);
    server.endpointHandler(endpoint -> {
      endpoint.accept(false);
      endpoint.publishHandler(msg -> {
        // Echo back
        endpoint.publish(msg.topicName(), msg.payload(), MqttQoS.AT_MOST_ONCE, false, false);
      });
    });
    Async async = ctx.async();
    server.listen(ctx.asyncAssertSuccess(server -> async.complete()));
    async.awaitSuccess(10000);
  }

  @After
  public void after(TestContext ctx) {
    server.close(ctx.asyncAssertSuccess(v -> {
      vertx.close(ctx.asyncAssertSuccess());
    }));
  }

  @Test
  public void testPauseResume(TestContext ctx) {
    Async async = ctx.async();
    AtomicInteger received = new AtomicInteger();
    MqttClient client = MqttClient.create(vertx);

    client.connect(MqttClientOptions.DEFAULT_PORT, "localhost", ctx.asyncAssertSuccess(ack -> {

      client.publishHandler(msg -> {
        received.incrementAndGet();
      });

      // Pause the client immediately
      client.pause();

      // Publish a message from client to trigger server echo
      // Since client is paused for READING, it should still be able to WRITE.
      client.publish("test/topic", Buffer.buffer("hello"), MqttQoS.AT_MOST_ONCE, false, false);

      // Wait a bit to ensure nothing is received, check that nothing is receives, and resume reading
      vertx.setTimer(1_000, id -> {

        ctx.assertEquals(0, received.get(), "Should not receive message while paused");

        // Wait for message
        long timerId = vertx.setTimer(2_000, id2 -> {
          ctx.fail("Did not receive message after resume");
        });

        client.publishHandler(msg -> {
          vertx.cancelTimer(timerId);
          received.incrementAndGet();
          async.complete();
        });

        client.resume();
      });
    }));

    async.await();
    client.disconnect();
  }
}
