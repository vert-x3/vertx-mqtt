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

import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttVersion;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.MqttServer;
import io.vertx.mqtt.MqttServerOptions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The client uses the Server Keep Alive a MQTT 5 server assigns in the CONNACK (MQTT 5.0 §3.2.2.3.14).
 */
@RunWith(VertxUnitRunner.class)
public class Mqtt5ClientServerKeepAliveTest {

  private Vertx vertx;
  private MqttServer server;

  @Before
  public void before() {
    vertx = Vertx.vertx();
    server = MqttServer.create(vertx, new MqttServerOptions().setPort(0));
  }

  @After
  public void after(TestContext ctx) {
    server.close().onComplete(ctx.asyncAssertSuccess(v -> vertx.close().onComplete(ctx.asyncAssertSuccess())));
  }

  private static void acceptWithServerKeepAlive(MqttEndpoint endpoint, int serverKeepAlive) {
    MqttProperties props = new MqttProperties();
    props.add(new MqttProperties.IntegerProperty(MqttProperties.MqttPropertyType.SERVER_KEEP_ALIVE.value(), serverKeepAlive));
    endpoint.accept(false, props);
  }

  private static MqttClientOptions v5Options(int keepAliveInterval) {
    MqttClientOptions options = new MqttClientOptions().setKeepAliveInterval(keepAliveInterval);
    options.setVersion(MqttVersion.MQTT_5.protocolLevel());
    return options;
  }

  private int listen() {
    return server.listen().await().actualPort();
  }

  /**
   * Server Keep Alive 0 switches keep alive off: the connect succeeds and the client sends no PINGREQ.
   */
  @Test
  public void serverKeepAliveZeroDisablesKeepAlive(TestContext ctx) {
    AtomicInteger pings = new AtomicInteger();
    server.endpointHandler(endpoint -> {
      endpoint.pingHandler(v -> pings.incrementAndGet());
      acceptWithServerKeepAlive(endpoint, 0);
    });
    int port = listen();

    Async done = ctx.async();
    MqttClient client = MqttClient.create(vertx, v5Options(2));
    client.connect(port, "localhost").onComplete(ctx.asyncAssertSuccess(ack ->
      // past the configured keep alive, but before the 3 s after which the Vert.x server closes the connection,
      // as it watches the keep alive of the CONNECT regardless of the Server Keep Alive it sent
      vertx.setTimer(2500, id -> {
        ctx.assertTrue(client.isConnected());
        ctx.assertEquals(0, pings.get());
        client.disconnect().onComplete(ctx.asyncAssertSuccess(v -> done.complete()));
      })));
  }

  /**
   * A missing PINGRESP is detected after 1.5 times the assigned keep alive, not the configured one.
   */
  @Test
  public void missingPingResponseIsDetectedAfterServerKeepAlive(TestContext ctx) {
    server.endpointHandler(endpoint -> {
      endpoint.autoKeepAlive(false); // never answers PINGREQ
      acceptWithServerKeepAlive(endpoint, 1);
    });
    int port = listen();

    Async closed = ctx.async();
    MqttClient client = MqttClient.create(vertx, v5Options(30));
    client.connect(port, "localhost").onComplete(ctx.asyncAssertSuccess(ack -> {
      // PINGREQ after 1 s, closed 1.5 s later; with the timeout of the configured 30 s it would take 46 s
      long timeout = vertx.setTimer(6000, id -> ctx.fail("Client not closed after the assigned keep alive"));
      client.closeHandler(v -> {
        vertx.cancelTimer(timeout);
        closed.complete();
      });
    }));
  }

  /**
   * The assigned keep alive applies to that connection only, the next CONNECT sends the configured one again.
   */
  @Test
  public void serverKeepAliveDoesNotCarryOverToTheNextConnect(TestContext ctx) {
    List<Integer> requested = new ArrayList<>();
    server.endpointHandler(endpoint -> {
      requested.add(endpoint.keepAliveTimeSeconds());
      acceptWithServerKeepAlive(endpoint, 5);
    });
    int port = listen();

    Async done = ctx.async();
    MqttClient client = MqttClient.create(vertx, v5Options(30));
    client.connect(port, "localhost")
      .compose(ack -> client.disconnect())
      .compose(v -> client.connect(port, "localhost"))
      .compose(ack -> client.disconnect())
      .onComplete(ctx.asyncAssertSuccess(v -> {
        ctx.assertEquals(List.of(30, 30), requested);
        done.complete();
      }));
  }
}
