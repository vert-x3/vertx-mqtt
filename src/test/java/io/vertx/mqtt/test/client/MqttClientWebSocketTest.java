/*
 * Copyright (c) 2026 IoT Invent GmbH
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
 *
 * AI Disclosure: This file was largely AI-generated. The AI-generated
 * portions are made available under CC0-1.0 and not subject to the
 * project's licence. The human contributor has reviewed and verified
 * that the code is correct.
 *
 * SPDX-License-Identifier: Apache-2.0 AND CC0-1.0
 * Assisted-by: Anthropic Claude Opus 5.5 (claude-opus-5-5)
 */

package io.vertx.mqtt.test.client;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.WebSocketFrame;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.Timeout;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.MqttServer;
import io.vertx.mqtt.MqttServerOptions;
import io.vertx.test.tls.Cert;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MQTT client testing about using MQTT over WebSocket
 */
@RunWith(VertxUnitRunner.class)
public class MqttClientWebSocketTest {

  private static final String MQTT_SERVER_HOST = "localhost";
  private static final String MQTT_TOPIC = "/my_topic";

  @Rule
  public Timeout timeout = Timeout.seconds(20);

  private Vertx vertx;
  private MqttServer server;
  private MqttClient client;

  @Before
  public void before() {
    vertx = Vertx.vertx();
  }

  @After
  public void after() {
    vertx.close().await();
  }

  @Test
  public void publishSubscribeQos0(TestContext context) {
    publishSubscribe(context, new MqttClientOptions(), MqttQoS.AT_MOST_ONCE);
  }

  @Test
  public void publishSubscribeQos1(TestContext context) {
    publishSubscribe(context, new MqttClientOptions(), MqttQoS.AT_LEAST_ONCE);
  }

  @Test
  public void publishSubscribeQos2(TestContext context) {
    publishSubscribe(context, new MqttClientOptions(), MqttQoS.EXACTLY_ONCE);
  }

  @Test
  public void publishSubscribeMqtt5(TestContext context) {
    publishSubscribe(context, mqttVersion(5), MqttQoS.AT_LEAST_ONCE);
  }

  @Test
  public void publishSubscribeOverTls(TestContext context) {
    int port = startServer(new MqttServerOptions()
      .setUseWebSocket(true)
      .setSsl(true)
      .setKeyCertOptions(Cert.SERVER_PEM.get()), this::echo);
    publishSubscribe(context, port, new MqttClientOptions()
      .setSsl(true)
      .setHostnameVerificationAlgorithm("")
      .setTrustAll(true), MqttQoS.AT_LEAST_ONCE, Buffer.buffer("Hello Vert.x MQTT over wss"));
  }

  @Test
  public void largeMessage(TestContext context) {
    int size = 256 * 1024;
    MqttServerOptions serverOptions = new MqttServerOptions()
      .setUseWebSocket(true)
      .setMaxMessageSize(size + 1024);
    serverOptions.setWebSocketMaxFrameSize(size + 1024);
    int port = startServer(serverOptions, this::echo);
    publishSubscribe(context, port, new MqttClientOptions()
      .setMaxMessageSize(size + 1024)
      .setWebSocketMaxFrameSize(size + 1024), MqttQoS.AT_LEAST_ONCE, Buffer.buffer(new byte[size]));
  }

  @Test
  public void perMessageCompression(TestContext context) {
    compression(context, new MqttClientOptions().setTryUsePerMessageWebSocketCompression(true), "permessage-deflate");
  }

  @Test
  public void perFrameCompression(TestContext context) {
    compression(context, new MqttClientOptions().setTryUsePerFrameWebSocketCompression(true), "deflate-frame");
  }

  @Test
  public void compressionOfferedButNotSupportedByServer(TestContext context) {
    int port = startServer(new MqttServerOptions()
      .setUseWebSocket(true)
      .setPerMessageWebSocketCompressionSupported(false)
      .setPerFrameWebSocketCompressionSupported(false), this::echo);
    publishSubscribe(context, port, new MqttClientOptions()
      .setTryUsePerMessageWebSocketCompression(true)
      .setTryUsePerFrameWebSocketCompression(true), MqttQoS.AT_LEAST_ONCE, Buffer.buffer("uncompressed"));
  }

  @Test
  public void noCompressionOnBothSides(TestContext context) {
    int port = startServer(new MqttServerOptions()
      .setUseWebSocket(true)
      .setPerMessageWebSocketCompressionSupported(false)
      .setPerFrameWebSocketCompressionSupported(false), this::echo);
    publishSubscribe(context, port, new MqttClientOptions(), MqttQoS.AT_LEAST_ONCE, Buffer.buffer("uncompressed"));
  }

  @Test
  public void pathQueryStringAndHeaders(TestContext context) {
    Async async = context.async(2);
    int port = startServer(new MqttServerOptions().setUseWebSocket(true), endpoint -> {
      context.assertEquals("/mqtt?token=abc", endpoint.httpRequestURI());
      context.assertEquals("Bearer xyz", endpoint.httpHeaders().get("Authorization"));
      context.assertEquals("mqtt", endpoint.httpHeaders().get("Sec-WebSocket-Protocol"));
      endpoint.accept(false);
      async.countDown();
    });
    client = MqttClient.create(vertx, new MqttClientOptions()
      .setUseWebSocket(true)
      .setWebSocketPath("/mqtt?token=abc")
      .addWebSocketHeader("Authorization", "Bearer xyz"));
    client.connect(port, MQTT_SERVER_HOST)
      .compose(ack -> client.disconnect())
      .onComplete(context.asyncAssertSuccess(v -> async.countDown()));
  }

  @Test
  public void customSubProtocol(TestContext context) {
    Async async = context.async();
    HttpServer httpServer = vertx.createHttpServer(new HttpServerOptions().setWebSocketSubProtocols(Collections.singletonList("mqttv3.1.1")))
      .webSocketHandler(ws -> {
        context.assertEquals("mqttv3.1.1", ws.subProtocol());
        async.complete();
      });
    int port = httpServer.listen(0, MQTT_SERVER_HOST).await().actualPort();
    client = MqttClient.create(vertx, new MqttClientOptions().setUseWebSocket(true).addWebSocketSubProtocol("mqttv3.1.1"));
    client.connect(port, MQTT_SERVER_HOST);
  }

  @Test
  public void wrongPathFailsConnect(TestContext context) {
    int port = startServer(new MqttServerOptions().setUseWebSocket(true), endpoint -> context.fail("Unexpected endpoint"));
    client = MqttClient.create(vertx, new MqttClientOptions().setUseWebSocket(true).setWebSocketPath("/other"));
    client.connect(port, MQTT_SERVER_HOST).onComplete(context.asyncAssertFailure(err -> {
      context.assertTrue(err.getMessage().contains("404"), err.getMessage());
      context.assertFalse(client.isConnected());
    }));
  }

  @Test
  public void handshakeTimeoutFailsConnect(TestContext context) {
    // a server accepting the TCP connection but never answering the handshake
    int port = vertx.createNetServer().connectHandler(so -> {}).listen(0, MQTT_SERVER_HOST).await().actualPort();
    client = MqttClient.create(vertx, new MqttClientOptions().setUseWebSocket(true).setWebSocketHandshakeTimeout(500));
    long start = System.currentTimeMillis();
    client.connect(port, MQTT_SERVER_HOST).onComplete(context.asyncAssertFailure(err -> {
      context.assertTrue(err.getMessage().contains("timed out"), err.getMessage());
      context.assertTrue(System.currentTimeMillis() - start < 5000);
    }));
  }

  @Test
  public void connectAgainAfterFailedHandshake(TestContext context) {
    int port = startServer(new MqttServerOptions().setUseWebSocket(true), endpoint -> context.fail("Unexpected endpoint"));
    client = MqttClient.create(vertx, new MqttClientOptions().setUseWebSocket(true).setWebSocketPath("/other"));
    client.connect(port, MQTT_SERVER_HOST)
      .recover(err -> client.connect(port, MQTT_SERVER_HOST))
      .onComplete(context.asyncAssertFailure(err -> context.assertTrue(err.getMessage().contains("404"), err.getMessage())));
  }

  @Test
  public void fragmentedFramesFromServer(TestContext context) {
    // CONNACK (0x20 0x02 0x00 0x00) split across a binary frame and a continuation frame
    HttpServer httpServer = vertx.createHttpServer(new HttpServerOptions().setWebSocketSubProtocols(Collections.singletonList("mqtt")))
      .webSocketHandler(ws -> ws.binaryMessageHandler(connect -> {
        ws.writeFrame(WebSocketFrame.binaryFrame(Buffer.buffer(new byte[]{0x20, 0x02}), false));
        ws.writeFrame(WebSocketFrame.continuationFrame(Buffer.buffer(new byte[]{0x00, 0x00}), true));
      }));
    int port = httpServer.listen(0, MQTT_SERVER_HOST).await().actualPort();
    client = MqttClient.create(vertx, new MqttClientOptions().setUseWebSocket(true));
    client.connect(port, MQTT_SERVER_HOST).onComplete(context.asyncAssertSuccess(ack -> context.assertTrue(client.isConnected())));
  }

  @Test
  public void keepAlive(TestContext context) {
    Async pings = context.async(2);
    int port = startServer(new MqttServerOptions().setUseWebSocket(true), endpoint -> {
      endpoint.pingHandler(v -> pings.countDown());
      endpoint.accept(false);
    });
    client = MqttClient.create(vertx, new MqttClientOptions().setUseWebSocket(true).setKeepAliveInterval(1));
    client.connect(port, MQTT_SERVER_HOST).onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void clientDisconnect(TestContext context) {
    Async disconnected = context.async();
    int port = startServer(new MqttServerOptions().setUseWebSocket(true), endpoint -> {
      endpoint.disconnectHandler(v -> disconnected.complete());
      endpoint.accept(false);
    });
    client = MqttClient.create(vertx, new MqttClientOptions().setUseWebSocket(true));
    client.connect(port, MQTT_SERVER_HOST)
      .compose(ack -> client.disconnect())
      .onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void serverClose(TestContext context) {
    Async closed = context.async();
    int port = startServer(new MqttServerOptions().setUseWebSocket(true), endpoint -> {
      endpoint.accept(false);
      vertx.setTimer(100, id -> endpoint.close());
    });
    client = MqttClient.create(vertx, new MqttClientOptions().setUseWebSocket(true));
    client.closeHandler(v -> closed.complete());
    client.connect(port, MQTT_SERVER_HOST).onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void optionsJsonAndCopy(TestContext context) {
    MqttClientOptions options = new MqttClientOptions()
      .setUseWebSocket(true)
      .setWebSocketPath("/ws?x=1")
      .addWebSocketSubProtocol("mqtt")
      .addWebSocketHeader("Authorization", "Bearer xyz")
      .setWebSocketMaxFrameSize(1234)
      .setWebSocketHandshakeTimeout(4321)
      .setTryUsePerFrameWebSocketCompression(true)
      .setTryUsePerMessageWebSocketCompression(true)
      .setWebSocketCompressionLevel(3)
      .setWebSocketCompressionAllowClientNoContext(true)
      .setWebSocketCompressionRequestServerNoContext(true);

    for (MqttClientOptions copy : Arrays.asList(new MqttClientOptions(options), new MqttClientOptions(new JsonObject(options.toJson().encode())))) {
      context.assertTrue(copy.isUseWebSocket());
      context.assertEquals("/ws?x=1", copy.getWebSocketPath());
      context.assertEquals(Collections.singletonList("mqtt"), copy.getWebSocketSubProtocols());
      context.assertEquals(Collections.singletonMap("Authorization", "Bearer xyz"), copy.getWebSocketHeaders());
      context.assertEquals(1234, copy.getWebSocketMaxFrameSize());
      context.assertEquals(4321L, copy.getWebSocketHandshakeTimeout());
      context.assertTrue(copy.isTryUsePerFrameWebSocketCompression());
      context.assertTrue(copy.isTryUsePerMessageWebSocketCompression());
      context.assertEquals(3, copy.getWebSocketCompressionLevel());
      context.assertTrue(copy.isWebSocketCompressionAllowClientNoContext());
      context.assertTrue(copy.isWebSocketCompressionRequestServerNoContext());
    }

    MqttClientOptions defaults = new MqttClientOptions();
    context.assertFalse(defaults.isUseWebSocket());
    context.assertEquals(MqttClientOptions.DEFAULT_WEB_SOCKET_PATH, defaults.getWebSocketPath());
    context.assertNull(defaults.getWebSocketSubProtocols());
    context.assertFalse(defaults.isTryUsePerMessageWebSocketCompression());
  }

  @Test(expected = IllegalArgumentException.class)
  public void invalidPath() {
    new MqttClientOptions().setWebSocketPath("mqtt");
  }

  private static MqttClientOptions mqttVersion(int version) {
    MqttClientOptions options = new MqttClientOptions();
    options.setVersion(version);
    return options;
  }

  private void compression(TestContext context, MqttClientOptions clientOptions, String extension) {
    Async offered = context.async();
    int port = startServer(new MqttServerOptions().setUseWebSocket(true).setMaxMessageSize(128 * 1024), endpoint -> {
      String extensions = endpoint.httpHeaders().get("Sec-WebSocket-Extensions");
      context.assertNotNull(extensions);
      context.assertTrue(extensions.contains(extension), extensions);
      offered.complete();
      echo(endpoint);
    });
    byte[] compressible = new byte[64 * 1024];
    Arrays.fill(compressible, (byte) 'a');
    publishSubscribe(context, port, clientOptions.setMaxMessageSize(128 * 1024), MqttQoS.AT_LEAST_ONCE, Buffer.buffer(compressible));
  }

  private void publishSubscribe(TestContext context, MqttClientOptions clientOptions, MqttQoS qos) {
    int port = startServer(new MqttServerOptions().setUseWebSocket(true), this::echo);
    publishSubscribe(context, port, clientOptions, qos, Buffer.buffer("Hello Vert.x MQTT over WebSocket"));
  }

  private void publishSubscribe(TestContext context, int port, MqttClientOptions clientOptions, MqttQoS qos, Buffer payload) {
    Async received = context.async();
    client = MqttClient.create(vertx, clientOptions.setUseWebSocket(true));
    client.publishHandler(msg -> {
      context.assertEquals(MQTT_TOPIC, msg.topicName());
      context.assertEquals(payload, msg.payload());
      context.assertEquals(qos, msg.qosLevel());
      client.disconnect().onComplete(context.asyncAssertSuccess(v -> received.complete()));
    });
    client.connect(port, MQTT_SERVER_HOST)
      .compose(ack -> client.subscribe(MQTT_TOPIC, qos.value()))
      .compose(id -> client.publish(MQTT_TOPIC, payload, qos, false, false))
      .onComplete(context.asyncAssertSuccess());
  }

  private void echo(MqttEndpoint endpoint) {
    AtomicInteger subscriptions = new AtomicInteger();
    endpoint.publishAutoAck(true);
    endpoint.subscribeHandler(subscribe -> {
      subscriptions.incrementAndGet();
      endpoint.subscribeAcknowledge(subscribe.messageId(),
        Collections.singletonList(subscribe.topicSubscriptions().get(0).qualityOfService()));
    });
    endpoint.publishHandler(msg -> {
      if (subscriptions.get() > 0) {
        endpoint.publish(msg.topicName(), msg.payload(), msg.qosLevel(), false, false);
      }
    });
    endpoint.accept(false);
  }

  private int startServer(MqttServerOptions options, Handler<MqttEndpoint> endpointHandler) {
    server = MqttServer.create(vertx, options.setPort(0).setHost(MQTT_SERVER_HOST));
    return server.endpointHandler(endpointHandler).listen().await().actualPort();
  }
}
