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

import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.net.NetClient;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.mqtt.MqttServer;
import io.vertx.mqtt.MqttServerOptions;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * MQTT server testing
 */
@RunWith(VertxUnitRunner.class)
public class MqttServerWebSocketTest {

  private static final Logger log = LoggerFactory.getLogger(MqttServerWebSocketTest.class);

  protected static final String MQTT_SERVER_HOST = "localhost";
  protected static final int MQTT_SERVER_PORT = 1883;

  private Vertx vertx;

  @Before
  public void before() {

    this.vertx = Vertx.vertx();
  }

  @After
  public void after(TestContext context) {

    this.vertx.close().onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void sharedServersRoundRobin(TestContext context) {

    int numServers = VertxOptions.DEFAULT_EVENT_LOOP_POOL_SIZE / 2- 1;
    int numConnections = numServers * 20;

    List<MqttServer> servers = new ArrayList<>();
    Set<MqttServer> connectedServers = ConcurrentHashMap.newKeySet();

    CountDownLatch latchListen = new CountDownLatch(numServers);
    CountDownLatch latchConns = new CountDownLatch(numConnections);
    Map<MqttServer, Integer> connectCount = new ConcurrentHashMap<>();

    try {

      for (int i = 0; i < numServers; i++) {

        MqttServer server = MqttServer.create(this.vertx, new MqttServerOptions().setHost(MQTT_SERVER_HOST).setPort(MQTT_SERVER_PORT).setUseWebSocket(true));
        servers.add(server);

        server.endpointHandler(endpoint -> {

          connectedServers.add(server);

          Integer cnt = connectCount.get(server);
          int icnt = cnt == null ? 0 : cnt;
          icnt++;
          connectCount.put(server, icnt);

          endpoint.accept(false);
          latchConns.countDown();

        }).listen().onComplete(ar -> {

          if (ar.succeeded()) {
            log.info("MQTT server listening on port " + ar.result().actualPort());
            latchListen.countDown();
          } else {
            log.error("Error starting MQTT server", ar.cause());
          }
        });
      }

      context.assertTrue(latchListen.await(10, TimeUnit.SECONDS));

      // starting Eclipse Paho clients is synchronous
      for (int i = 0; i < numConnections; i++) {

        String clientId = String.format("client-%d", i);
        try {

          MemoryPersistence persistence = new MemoryPersistence();
          MqttClient client = new MqttClient(String.format("ws://%s:%d", MQTT_SERVER_HOST, MQTT_SERVER_PORT), clientId, persistence);
          client.connect();
          log.info("Client connected " + clientId);

        } catch (MqttException e) {

          log.error("Error on connecting client " + clientId, e);
          context.assertTrue(false);
        }
      }
      context.assertTrue(latchConns.await(10, TimeUnit.SECONDS));

      context.assertEquals(numServers, connectedServers.size());
      for (MqttServer server : servers) {
        context.assertTrue(connectedServers.contains(server));
      }
      context.assertEquals(numServers, connectCount.size());
      for (int cnt : connectCount.values()) {
        context.assertEquals(numConnections / numServers, cnt);
      }

      CountDownLatch closeLatch = new CountDownLatch(numServers);

      for (MqttServer server : servers) {
        server.close().onComplete(context.asyncAssertSuccess(v -> {
          closeLatch.countDown();
        }));
      }

      context.assertTrue(closeLatch.await(10, TimeUnit.SECONDS));

    } catch (InterruptedException e) {

    }
  }

  @Test
  public void testHttpHeaders(TestContext context) {
    MqttServer server = MqttServer.create(this.vertx, new MqttServerOptions().setHost(MQTT_SERVER_HOST).setPort(MQTT_SERVER_PORT).setUseWebSocket(true));
    Async done = context.async();
    server.endpointHandler(endpoint -> {
      MultiMap headers = endpoint.httpHeaders();
      context.assertNotNull(headers);
      context.assertEquals("Upgrade", headers.get("Connection"));
      context.assertEquals("/mqtt", endpoint.httpRequestURI());
      done.complete();
      endpoint.accept(false);
    });
    Async listen = context.async();
    server.listen().onComplete(context.asyncAssertSuccess(s -> listen.complete()));
    listen.awaitSuccess(15_000);
    MemoryPersistence persistence = new MemoryPersistence();
    try (MqttClient client = new MqttClient(String.format("ws://%s:%d", MQTT_SERVER_HOST, MQTT_SERVER_PORT), "12345", persistence)) {
      client.connect();
      client.disconnect();
    } catch (MqttException e) {
      context.fail(e);
    }



  }

  @Test
  public void testWebSocketConnectWithCompressionDisabled(TestContext context) {
    MqttServerOptions options = new MqttServerOptions()
      .setHost(MQTT_SERVER_HOST)
      .setPort(MQTT_SERVER_PORT)
      .setUseWebSocket(true)
      .setPerFrameWebSocketCompressionSupported(false)
      .setPerMessageWebSocketCompressionSupported(false);
    MqttServer server = MqttServer.create(this.vertx, options);
    Async done = context.async();
    server.endpointHandler(endpoint -> {
      endpoint.accept(false);
      done.complete();
    });
    Async listen = context.async();
    server.listen().onComplete(context.asyncAssertSuccess(s -> listen.complete()));
    listen.awaitSuccess(15_000);
    MemoryPersistence persistence = new MemoryPersistence();
    try (MqttClient client = new MqttClient(String.format("ws://%s:%d", MQTT_SERVER_HOST, MQTT_SERVER_PORT), "compression-disabled", persistence)) {
      client.connect();
      client.disconnect();
    } catch (MqttException e) {
      context.fail(e);
    }
  }

  @Test
  public void testWebSocketPathWithQueryString(TestContext context) {
    MqttServer server = MqttServer.create(this.vertx, new MqttServerOptions()
      .setHost(MQTT_SERVER_HOST)
      .setPort(0)
      .setUseWebSocket(true));
    Async done = context.async();
    server.endpointHandler(endpoint -> {
      context.assertEquals("/mqtt?auth=token", endpoint.httpRequestURI());
      done.complete();
      endpoint.accept(false);
    });
    Async listen = context.async();
    server.listen().onComplete(context.asyncAssertSuccess(s -> listen.complete()));
    listen.awaitSuccess(15_000);
    MemoryPersistence persistence = new MemoryPersistence();
    String serverUri = String.format("ws://%s:%d/mqtt?auth=token", MQTT_SERVER_HOST, server.actualPort());
    try (MqttClient client = new MqttClient(serverUri, "query-string", persistence)) {
      client.connect();
      client.disconnect();
    } catch (MqttException e) {
      context.fail(e);
    }
    done.awaitSuccess(15_000);
  }

  @Test
  public void testInvalidWebSocketPathRejected(TestContext context) {
    MqttServer server = MqttServer.create(this.vertx, new MqttServerOptions()
      .setHost(MQTT_SERVER_HOST)
      .setPort(0)
      .setUseWebSocket(true));
    server.endpointHandler(endpoint ->
      context.fail("Endpoint handler should not be called for invalid WebSocket path"));
    Async listen = context.async();
    server.listen().onComplete(context.asyncAssertSuccess(s -> listen.complete()));
    listen.awaitSuccess(15_000);

    NetClient client = this.vertx.createNetClient();
    try {
      assertInvalidWebSocketPathRejected(context, client, server.actualPort(), "/mqttfoo?auth=token");
      assertInvalidWebSocketPathRejected(context, client, server.actualPort(), "/mqtt/foo?auth=token");
    } finally {
      client.close();
    }
  }

  private void assertInvalidWebSocketPathRejected(TestContext context, NetClient client, int port, String requestUri) {
    Async done = context.async();
    client.connect(port, MQTT_SERVER_HOST).onComplete(ar -> {
      if (ar.failed()) {
        context.fail(ar.cause());
        done.complete();
        return;
      }
      Buffer response = Buffer.buffer();
      ar.result().handler(buffer -> {
        response.appendBuffer(buffer);
        String responseText = response.toString();
        if (responseText.contains("\r\n\r\n")) {
          ar.result().handler(null);
          try {
            context.assertTrue(responseText.startsWith("HTTP/1.1 404"));
          } finally {
            ar.result().close();
            done.complete();
          }
        }
      });
      ar.result().closeHandler(v -> {
        String responseText = response.toString();
        if (!responseText.contains("\r\n\r\n")) {
          try {
            context.assertTrue(responseText.startsWith("HTTP/1.1 404"));
          } finally {
            done.complete();
          }
        }
      });
      ar.result().write("GET " + requestUri + " HTTP/1.1\r\n" +
        "Host: " + MQTT_SERVER_HOST + ":" + port + "\r\n" +
        "Connection: Upgrade\r\n" +
        "Upgrade: websocket\r\n" +
        "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n" +
        "Sec-WebSocket-Version: 13\r\n" +
        "Sec-WebSocket-Protocol: mqtt\r\n\r\n");
    });
    done.awaitSuccess(15_000);
  }
}
