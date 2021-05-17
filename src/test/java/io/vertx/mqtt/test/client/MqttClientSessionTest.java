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
import static org.junit.Assert.assertEquals;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
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
import io.vertx.mqtt.messages.MqttPublishMessage;

@RunWith(VertxUnitRunner.class)
public class MqttClientSessionTest {
  private static final Logger log = LoggerFactory.getLogger(MqttClientSessionTest.class);
  private static final int MQTT_SERVER_TLS_PORT = 1883;
  private static final String MQTT_SERVER_HOST = "localhost";

  Vertx vertx = Vertx.vertx();
  MqttServer server;
  TestContext ctx;
  List<MqttPublishMessage> serverMessages = new LinkedList<>();

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
    server
      .endpointHandler(server -> MqttClientSessionTest.serverLogic(vertx, server, this.serverMessages::add))
      .listen(result);

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

  private static void serverLogic(Vertx vertx, MqttEndpoint endpoint, Handler<MqttPublishMessage> fromClient) {
    log.info("[SERVER] Client connected");

    Map<String, Long> publishTimers = new HashMap<>();

    endpoint.subscribeHandler(subscribe -> {
      String names = subscribe.topicSubscriptions().stream().map(MqttTopicSubscription::topicName).collect(Collectors.joining(", "));
      log.info("[SERVER] Received SUBSCRIBE with message id = " + subscribe.messageId() + " topics = " + names);

      List<MqttQoS> grantedQosLevels = new ArrayList<>();
      for (MqttTopicSubscription s : subscribe.topicSubscriptions()) {
        final String topicName = s.topicName();
        final MqttQoS qos;
        switch (topicName) {
          case "foo":
          case "bar":
          case "baz/#":
          case "qos0":
            qos = MqttQoS.valueOf(Math.min(s.qualityOfService().value(), 0));
            break;
          case "qos1":
            qos = MqttQoS.valueOf(Math.min(s.qualityOfService().value(), 1));
            break;
          default:
            qos = MqttQoS.FAILURE;
        }

        grantedQosLevels.add(qos);

        if (qos != MqttQoS.FAILURE) {
          AtomicLong l = new AtomicLong();
          long t = vertx.setPeriodic(1_000, x -> {
            long value = l.getAndIncrement();
            endpoint.publish(topicName, Buffer.buffer("payload" + value), qos, false, false);
          });
          Long old = publishTimers.put(topicName, t);
          if (old != null) {
            vertx.cancelTimer(old);
          }
        }

      }

      // ack the subscriptions request
      endpoint.subscribeAcknowledge(subscribe.messageId(), grantedQosLevels);

    });

    endpoint.unsubscribeHandler(u -> {
      log.info("[SERVER] Received UNSUBSCRIBE with message id = " + u.messageId());
      for ( String topic : u.topics() ) {
        long t = publishTimers.remove(topic);
        vertx.cancelTimer(t);
      }
      endpoint.unsubscribeAcknowledge(u.messageId());
    });

    endpoint.publishHandler(p -> {
      log.info("[SERVER] Received PUBLISH with message id = " + p.messageId());
      fromClient.handle(p);
      switch (p.qosLevel()) {
        case AT_MOST_ONCE:
          break;
        case AT_LEAST_ONCE:
          endpoint.publishAcknowledge(p.messageId());
          break;
      }
    });
    endpoint.disconnectHandler(d -> {
      publishTimers.values().forEach(vertx::cancelTimer);
      log.info("[SERVER] Client disconnected");
    });

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

  private Object[] testSubscribe(Duration timeout, Consumer<MqttClientSession> customizer, BiFunction<MqttClientSession, List<String[]>, Boolean> completion) {

    MqttClientSessionOptions options = new MqttClientSessionOptions()
      .setPort(MQTT_SERVER_TLS_PORT)
      .setHostname(MQTT_SERVER_HOST);
    MqttClientSession client = MqttClientSession
      .create(vertx, options);

    customizer.accept(client);

    Async async = ctx.async();

    LinkedList<MqttClientSession.SubscriptionEvent> events = new LinkedList<>();
    client.subscriptionStateHandler(events::add);

    List<String[]> payloads = new LinkedList<>();
    Async msgAsync = ctx.async();
    client.messageHandler(msg -> {
      payloads.add(new String[]{msg.topicName(), msg.payload().toString(StandardCharsets.UTF_8)});
      if (completion.apply(client, payloads)) {
        msgAsync.complete();
      }
    });

    msgAsync.handler(x -> {

      client.sessionStateHandler(event -> {

        switch (event.getSessionState()) {
          case DISCONNECTED:
            async.complete();
            break;
        }
      });

      client.stop();
    });

    client.start();

    async.await(timeout.toMillis());

    return new Object[]{events, payloads};
  }

  /**
   * Test subscribing.
   */
  @Test
  public void testSubscribeDefault() {

    startServer();

    Object[] tuple = testSubscribe(Duration.ofSeconds(5), session -> {
        session.subscribe("qos0", MqttClientSession.RequestedQoS.QOS_1);
      },
      (session, payloads) -> payloads.size() == 2
    );

    // assert

    LinkedList<MqttClientSession.SubscriptionEvent> events = (LinkedList<MqttClientSession.SubscriptionEvent>) tuple[0];
    List<String[]> payloads = (List<String[]>) tuple[1];

    assertArrayEquals(new Object[]{
      new MqttClientSession.SubscriptionEvent("qos0", MqttClientSession.SubscriptionState.SUBSCRIBING, null),
      new MqttClientSession.SubscriptionEvent("qos0", MqttClientSession.SubscriptionState.SUBSCRIBED, 0),
      new MqttClientSession.SubscriptionEvent("qos0", MqttClientSession.SubscriptionState.UNSUBSCRIBED, null),
    }, events.toArray());

    assertArrayEquals(new Object[]{
      new String[]{"qos0", "payload0"},
      new String[]{"qos0", "payload1"}
    }, payloads.toArray());

  }

  /**
   * Test subscribing.
   */
  @Test
  public void testSubscribeLate() {

    vertx.setTimer(2_000, x -> startServerAsync());

    Object[] tuple = testSubscribe(Duration.ofSeconds(15), session -> {
        session.subscribe("qos0", MqttClientSession.RequestedQoS.QOS_1);
      },
      (session, payloads) -> payloads.size() == 2
    );

    // assert

    LinkedList<MqttClientSession.SubscriptionEvent> events = (LinkedList<MqttClientSession.SubscriptionEvent>) tuple[0];
    List<String[]> payloads = (List<String[]>) tuple[1];

    assertArrayEquals(new Object[]{
      new MqttClientSession.SubscriptionEvent("qos0", MqttClientSession.SubscriptionState.UNSUBSCRIBED, null),
      new MqttClientSession.SubscriptionEvent("qos0", MqttClientSession.SubscriptionState.SUBSCRIBING, null),
      new MqttClientSession.SubscriptionEvent("qos0", MqttClientSession.SubscriptionState.SUBSCRIBED, 0),
      new MqttClientSession.SubscriptionEvent("qos0", MqttClientSession.SubscriptionState.UNSUBSCRIBED, null),
    }, events.toArray());

    assertArrayEquals(new Object[]{
      new String[]{"qos0", "payload0"},
      new String[]{"qos0", "payload1"}
    }, payloads.toArray());

  }

  /**
   * Test subscribing and unsubscribing.
   */
  @Test
  public void testUnsubscribeDefault() {

    startServer();

    Object[] tuple = testSubscribe(Duration.ofSeconds(25),
      session -> {
        session.subscribe("qos0", MqttClientSession.RequestedQoS.QOS_1);
      },
      (session, payloads) -> {
        if (payloads.size() == 2) {
          // stop
          session.unsubscribe(Collections.singleton("qos0"));
          vertx.setTimer(5_000, x -> {
            session.subscribe("qos1", MqttClientSession.RequestedQoS.QOS_1);
          });
        }
        return payloads.size() == 4;
      }
    );

    // assert

    LinkedList<MqttClientSession.SubscriptionEvent> events = (LinkedList<MqttClientSession.SubscriptionEvent>) tuple[0];
    List<String[]> payloads = (List<String[]>) tuple[1];

    assertArrayEquals(new Object[]{
      new MqttClientSession.SubscriptionEvent("qos0", MqttClientSession.SubscriptionState.SUBSCRIBING, null),
      new MqttClientSession.SubscriptionEvent("qos0", MqttClientSession.SubscriptionState.SUBSCRIBED, 0),
      new MqttClientSession.SubscriptionEvent("qos0", MqttClientSession.SubscriptionState.UNSUBSCRIBED, null),
      new MqttClientSession.SubscriptionEvent("qos1", MqttClientSession.SubscriptionState.SUBSCRIBING, null),
      new MqttClientSession.SubscriptionEvent("qos1", MqttClientSession.SubscriptionState.SUBSCRIBED, 1),
      new MqttClientSession.SubscriptionEvent("qos1", MqttClientSession.SubscriptionState.UNSUBSCRIBED, null),
    }, events.toArray());

    assertArrayEquals(new Object[]{
      new String[]{"qos0", "payload0"},
      new String[]{"qos0", "payload1"},
      new String[]{"qos1", "payload0"},
      new String[]{"qos1", "payload1"}
    }, payloads.toArray());

  }

  /**
   * Test publishing, when it should fail
   */
  @Test
  public void testPublishWhenDisconnected() {

    MqttClientSessionOptions options = new MqttClientSessionOptions()
      .setPort(MQTT_SERVER_TLS_PORT)
      .setHostname(MQTT_SERVER_HOST);
    MqttClientSession client = MqttClientSession
      .create(vertx, options);

    client.publish("foo", Buffer.buffer(), MqttQoS.AT_MOST_ONCE)
      // this must fail as we are not connected
      .onComplete(ctx.asyncAssertFailure());

  }

  /**
   * Test publishing, when it should fail
   */
  @Test
  public void testPublish() {

    startServer();

    MqttClientSessionOptions options = new MqttClientSessionOptions()
      .setPort(MQTT_SERVER_TLS_PORT)
      .setHostname(MQTT_SERVER_HOST);
    MqttClientSession client = MqttClientSession
      .create(vertx, options);

    Async async = ctx.async();

    client.sessionStateHandler(event -> {

      switch (event.getSessionState()) {
        case CONNECTED:
          client.publishHandler(x -> client.stop());
          client.publish("foo", Buffer.buffer("bar"), MqttQoS.AT_LEAST_ONCE);
          break;
        case DISCONNECTED:
          async.complete();
          break;
      }
    });

    client.start();

    async.await(5_000);

    List<MqttPublishMessage> expectedMessages = new LinkedList<>();
    expectedMessages.add(MqttPublishMessage.create(
      0,
      MqttQoS.AT_LEAST_ONCE,
      false,
      false,
      "foo",
      Buffer.buffer("bar").getByteBuf()
    ));

    assertEquals(expectedMessages.size(), serverMessages.size());
    for (int i = 0; i < expectedMessages.size(); i++) {
      MqttPublishMessage expected = expectedMessages.get(i);
      MqttPublishMessage actual = serverMessages.get(i);

      assertEquals(expected.topicName(), actual.topicName());
      assertEquals(expected.payload(), actual.payload());
      assertEquals(expected.qosLevel(), actual.qosLevel());
    }

  }

}
