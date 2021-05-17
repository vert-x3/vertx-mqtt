/*
 * Copyright 2021 Red Hat Inc.
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

import static org.junit.Assert.assertArrayEquals;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.mqtt.MqttClientSession;
import io.vertx.mqtt.MqttClientSessionOptions;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.MqttServer;
import io.vertx.mqtt.MqttTopicSubscription;

@RunWith(VertxUnitRunner.class)
public class MqttClientSessionTest {
  private static final Logger log = LoggerFactory.getLogger(MqttClientSslTest.class);
  private static final int MQTT_SERVER_TLS_PORT = 1883;
  private static final String MQTT_SERVER_HOST = "localhost";

  Vertx vertx = Vertx.vertx();
  MqttServer server;
  TestContext ctx;

  @Before
  public void setup(TestContext ctx) {
    this.ctx = ctx;
  }

  @After
  public void after() {
    stopServer();
  }

  void startServer() {
    Async async = ctx.async();
    startServerAsync()
      .onFailure(ctx::fail)
      .onSuccess(x -> async.complete());

    async.await();
  }

  Future<?> startServerAsync() {
    server = MqttServer.create(vertx);
    server.exceptionHandler(t -> ctx.fail());

    Promise<MqttServer> result = Promise.promise();
    server.endpointHandler(MqttClientSessionTest::serverLogic).listen(result);

    return result.future();
  }

  void stopServer() {
    if (this.server != null) {
      this.server.close(ctx.asyncAssertSuccess(v -> {
        this.vertx.close(ctx.asyncAssertSuccess());
      }));
      this.server = null;
    }
  }

  private static void serverLogic(MqttEndpoint endpoint) {
    log.info("[SERVER] Client connected");

    endpoint.subscribeHandler(subscribe -> {
      log.info("[SERVER] Received SUBSCRIBE with message id = " + subscribe.messageId());

      List<MqttQoS> grantedQosLevels = new ArrayList<>();
      for (MqttTopicSubscription s : subscribe.topicSubscriptions()) {
        switch (s.topicName()) {
          case "foo":
          case "bar":
          case "baz/#":
          case "qos0":
            grantedQosLevels.add(MqttQoS.valueOf(Math.min(s.qualityOfService().value(), 0)));
            break;
          case "qos1":
            grantedQosLevels.add(MqttQoS.valueOf(Math.min(s.qualityOfService().value(), 1)));
            break;
          default:
            grantedQosLevels.add(MqttQoS.FAILURE);
        }
      }

      // ack the subscriptions request
      endpoint.subscribeAcknowledge(subscribe.messageId(), grantedQosLevels);

    });
    endpoint.publishHandler(p -> {
      log.info("[SERVER] Received PUBLISH with message id = " + p.messageId());
    });
    endpoint.disconnectHandler(d -> log.info("[SERVER] Client disconnected"));

    // accept session

    endpoint.accept(false);
  }

  /**
   * Test starting a server first, and then connecting.
   */
  @Test
  public void testConnect() {

    startServer();

    MqttClientSessionOptions options = new MqttClientSessionOptions()
      .setPort(MQTT_SERVER_TLS_PORT)
      .setHostname(MQTT_SERVER_HOST);
    MqttClientSession client = MqttClientSession
      .create(vertx, options);

    LinkedList<MqttClientSession.SessionState> sessionStates = new LinkedList<>();

    Async async = ctx.async();

    client.sessionStateHandler(event -> {
      sessionStates.add(event.getSessionState());

      switch (event.getSessionState()) {
        case CONNECTED:
          client.stop();
          break;
        case DISCONNECTED:
          async.complete();
          break;
      }
    });

    client.start();

    async.await();

    assertArrayEquals(new Object[]{
      MqttClientSession.SessionState.CONNECTING,
      MqttClientSession.SessionState.CONNECTED,
      MqttClientSession.SessionState.DISCONNECTING,
      MqttClientSession.SessionState.DISCONNECTED
    }, sessionStates.toArray());
  }

  /**
   * Test starting a connection before the server is running. The session must connect late.
   */
  @Test
  public void testConnectLate() {

    MqttClientSessionOptions options = new MqttClientSessionOptions()
      .setPort(MQTT_SERVER_TLS_PORT)
      .setHostname(MQTT_SERVER_HOST);
    MqttClientSession client = MqttClientSession
      .create(vertx, options);

    LinkedList<MqttClientSession.SessionState> sessionStates = new LinkedList<>();

    Async async = ctx.async(2);

    client.sessionStateHandler(event -> {
      sessionStates.add(event.getSessionState());

      switch (event.getSessionState()) {
        case CONNECTED:
          client.stop();
          break;
        case DISCONNECTED:
          async.countDown();
          break;
      }
    });

    client.start();

    vertx.setTimer(2_000, x -> {
      // start server after 2 seconds
      startServerAsync();
    });

    async.await(15_000);

    assertArrayEquals(new Object[]{
      MqttClientSession.SessionState.CONNECTING,
      MqttClientSession.SessionState.DISCONNECTED,

      MqttClientSession.SessionState.CONNECTING,
      MqttClientSession.SessionState.CONNECTED,
      MqttClientSession.SessionState.DISCONNECTING,
      MqttClientSession.SessionState.DISCONNECTED
    }, sessionStates.toArray());
  }
}
