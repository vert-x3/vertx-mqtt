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
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.mqtt.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertTrue;

/**
 * MQTT client testing about recovering of persistence and recovery for "in flight" messages queues
 */
@RunWith(VertxUnitRunner.class)
public class MqttClientRecoveryTest {
  private static final Logger log = LoggerFactory.getLogger(MqttClientRecoveryTest.class);

  private static final String MQTT_TOPIC = "/my_topic";
  private static final String MQTT_MESSAGE = "Hello Vert.x MQTT Client";
  private static final String MQTT_HOST = "localhost";
  private static final int MQTT_PORT = 1884;

  Vertx vertx = Vertx.vertx();
  MqttServer server;
  TestContext context;

  @Test
  public void qos1recovery(TestContext context) {
    this.context = context;
    Async phase1 = context.async(2);
    AtomicInteger sentPacketId = new AtomicInteger();

    MqttClientOptions options = new MqttClientOptions();

    // by setting this flag to fase we are forcing the test server to store the session state
    options.setCleanSession(false);

    MqttClient client = MqttClient.create(vertx, options);
    client.exceptionHandler(Throwable::printStackTrace);
    client.connect(MQTT_PORT, MQTT_HOST, c -> {
        log.info("[CLIENT] Connected to the test server");

        // publishing the message which should not be acknowledged by the test server and be resenred on reconnection
        client.publish(
          MQTT_TOPIC,
          Buffer.buffer(MQTT_MESSAGE.getBytes()),
          MqttQoS.AT_LEAST_ONCE,
          false,
          false,
          h -> {
            log.info("[CLIENT] publishing message id = " + h.result());
            sentPacketId.set(h.result());
          });

        // after publishing server should instantly close the connection
        phase1.countDown();
      }
    );


    client.closeHandler(ch -> {
      log.info("[CLIENT] connection with the test server is closed");
      phase1.countDown();
    });

    // waiting for :
    //            1. client connecting
    //            2. sending PUBLISH packet
    //            3. disconnection without responding on PUBLISH
    phase1.await();


    Async phase2 = context.async();
    client.publishCompletionHandler(packetId -> {
      log.info("[CLIENT] just received PUBACK");
      context.assertTrue(packetId == sentPacketId.get());
      phase2.countDown();
    });

    // and now when reconnecting we will resend unacknowledged PUBLISH packet
    client.connect(MQTT_PORT, MQTT_HOST, c -> {
      log.info("[CLIENT] reconnectring... ");
    });

    // waiting for redelivering of PUBLISH packet
    phase2.await();
  }

  @Before
  public void before() {
    server = MqttServer.create(vertx,
      new MqttServerOptions()
        .setPort(MQTT_PORT)
    );
    server.endpointHandler(this::serverLogic).listen(ar -> {
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

  // this is for remembering of received packetId
  final AtomicInteger receivedPacketId = new AtomicInteger();
  // for counting of how many times the client was connected
  final AtomicInteger connectionCounter = new AtomicInteger(0);

  private void serverLogic(MqttEndpoint mqttEndpoint) {
    log.info("[SERVER] Client connected");
    connectionCounter.incrementAndGet();
    mqttEndpoint.publishHandler(h -> {
      // when connecting the first time
      if (connectionCounter.get() == 1) {
        log.info("[SERVER] Just received publish message from the client");

        // remember packetId of sent message
        receivedPacketId.set(h.messageId());

        log.info("[SERVER] disconnection of the client");
        mqttEndpoint.close();
      }
      // when sending PUBLISH after reconnection
      else if (connectionCounter.get() == 2) {
        log.info("[SERVER] received PUBLISH message with packetId " + h.messageId());

        context.assertTrue(receivedPacketId.get() == h.messageId());

        mqttEndpoint.publishAcknowledge(h.messageId());
      }
      // such situation should not appear
      else {
        log.error("[SERVER] something wrong happened.... ");
        context.assertTrue(false);
      }
    });

    mqttEndpoint.disconnectHandler(d -> log.info("[SERVER] Client disconnected"));
    mqttEndpoint.accept(false);
  }

}
