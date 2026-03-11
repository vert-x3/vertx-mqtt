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

import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttVersion;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;
import io.vertx.mqtt.MqttServer;
import io.vertx.mqtt.messages.codes.MqttDisconnectReasonCode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for the MQTT v5 DISCONNECT packet sent by the client with reason codes.
 */
@RunWith(VertxUnitRunner.class)
public class Mqtt5ClientDisconnectTest {

  private Vertx vertx;
  private MqttServer server;

  @Before
  public void before() {
    vertx = Vertx.vertx();
    server = MqttServer.create(vertx);
  }

  @After
  public void after(TestContext ctx) {
    server.close().onComplete(ctx.asyncAssertSuccess(v ->
      vertx.close().onComplete(ctx.asyncAssertSuccess())));
  }

  // -----------------------------------------------------------------------

  /**
   * Plain disconnect (no reason code) should trigger the server's disconnectHandler.
   */
  @Test
  public void disconnectNormal(TestContext ctx) {
    Async disconnected = ctx.async();

    server.endpointHandler(endpoint -> {
      endpoint.disconnectHandler(v -> disconnected.complete());
      endpoint.accept(false);
    });

    startServer(ctx, () -> {
      MqttClientOptions options = new MqttClientOptions();
      options.setVersion(MqttVersion.MQTT_5.protocolLevel());

      MqttClient client = MqttClient.create(vertx, options);
      client.connect(server.actualPort(), "localhost")
        .onComplete(ctx.asyncAssertSuccess(ack ->
          client.disconnect(MqttDisconnectReasonCode.NORMAL, MqttProperties.NO_PROPERTIES)
            .onComplete(ctx.asyncAssertSuccess())));
    });

    disconnected.awaitSuccess(5000);
  }

  /**
   * Disconnect with a specific reason code — the client must be able to send it
   * without error (the server-side handler fires which proves the packet reached it).
   */
  @Test
  public void disconnectWithReasonCode(TestContext ctx) {
    Async disconnected = ctx.async();

    server.endpointHandler(endpoint -> {
      endpoint.disconnectHandler(v -> disconnected.complete());
      endpoint.accept(false);
    });

    startServer(ctx, () -> {
      MqttClientOptions options = new MqttClientOptions();
      options.setVersion(MqttVersion.MQTT_5.protocolLevel());

      MqttClient client = MqttClient.create(vertx, options);
      client.connect(server.actualPort(), "localhost")
        .onComplete(ctx.asyncAssertSuccess(ack ->
          client.disconnect(MqttDisconnectReasonCode.SESSION_TAKEN_OVER, MqttProperties.NO_PROPERTIES)
            .onComplete(ctx.asyncAssertSuccess())));
    });

    disconnected.awaitSuccess(5000);
  }

  /**
   * After a v5 disconnect the client must be in a disconnected state.
   */
  @Test
  public void disconnectAndReconnect(TestContext ctx) {
    Async reconnected = ctx.async();

    server.endpointHandler(endpoint -> endpoint.accept(false));

    startServer(ctx, () -> {
      MqttClientOptions options = new MqttClientOptions();
      options.setVersion(MqttVersion.MQTT_5.protocolLevel());

      MqttClient client = MqttClient.create(vertx, options);
      client.connect(server.actualPort(), "localhost")
        .onComplete(ctx.asyncAssertSuccess(ack1 ->
          client.disconnect(MqttDisconnectReasonCode.NORMAL, MqttProperties.NO_PROPERTIES)
            .onComplete(ctx.asyncAssertSuccess(v -> {
              ctx.assertFalse(client.isConnected());
              client.connect(server.actualPort(), "localhost")
                .onComplete(ctx.asyncAssertSuccess(ack2 -> {
                  ctx.assertTrue(client.isConnected());
                  reconnected.complete();
                }));
            }))));
    });

    reconnected.awaitSuccess(8000);
  }

  // -----------------------------------------------------------------------

  private void startServer(TestContext ctx, Runnable afterStart) {
    Async latch = ctx.async();
    server.listen(0).onComplete(ctx.asyncAssertSuccess(v -> {
      latch.complete();
      afterStart.run();
    }));
    latch.awaitSuccess(5000);
  }
}
