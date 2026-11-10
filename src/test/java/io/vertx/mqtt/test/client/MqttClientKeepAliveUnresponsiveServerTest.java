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
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;
import io.vertx.mqtt.MqttServer;
import io.vertx.mqtt.MqttServerOptions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A client that keeps writing still notices a server that stopped responding: when nothing was received for one and a
 * half times the keep alive, it sends a PINGREQ and closes the connection without PINGRESP.
 */
@RunWith(VertxUnitRunner.class)
public class MqttClientKeepAliveUnresponsiveServerTest {

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

  /**
   * The server accepts the publishes, but sends nothing back and does not answer PINGREQ.
   */
  @Test
  public void unresponsiveServerIsDetectedWhileTheClientKeepsPublishing(TestContext ctx) {
    server.endpointHandler(endpoint -> {
      endpoint.autoKeepAlive(false);
      endpoint.publishHandler(msg -> { });
      endpoint.accept(false);
    });
    int port = server.listen().await().actualPort();

    Async closed = ctx.async();
    MqttClient client = MqttClient.create(vertx, new MqttClientOptions().setKeepAliveInterval(1));
    client.connect(port, "localhost").onComplete(ctx.asyncAssertSuccess(ack -> {
      long publisher = vertx.setPeriodic(300, id ->
        client.publish("keepalive/unresponsive", Buffer.buffer("x"), MqttQoS.AT_MOST_ONCE, false, false));
      // nothing received for 1.5 s, then 1.5 s without PINGRESP
      long timeout = vertx.setTimer(6000, id -> ctx.fail("Unresponsive server not detected"));
      client.closeHandler(v -> {
        vertx.cancelTimer(publisher);
        vertx.cancelTimer(timeout);
        closed.complete();
      });
    }));
    closed.awaitSuccess(10000);
  }

  /**
   * A responsive server answers these PINGREQs, the connection stays open.
   */
  @Test
  public void responsiveServerStaysConnectedWhileTheClientKeepsPublishing(TestContext ctx) {
    server.endpointHandler(endpoint -> {
      endpoint.publishHandler(msg -> { });
      endpoint.accept(false);
    });
    int port = server.listen().await().actualPort();

    Async done = ctx.async();
    AtomicInteger pongs = new AtomicInteger();
    MqttClient client = MqttClient.create(vertx, new MqttClientOptions().setKeepAliveInterval(1));
    client.pingResponseHandler(v -> pongs.incrementAndGet());
    client.closeHandler(v -> ctx.fail("Connection to a responsive server closed"));
    client.connect(port, "localhost").onComplete(ctx.asyncAssertSuccess(ack -> {
      long publisher = vertx.setPeriodic(300, id ->
        client.publish("keepalive/responsive", Buffer.buffer("x"), MqttQoS.AT_MOST_ONCE, false, false));
      vertx.setTimer(5000, id -> {
        vertx.cancelTimer(publisher);
        ctx.assertTrue(client.isConnected());
        ctx.assertTrue(pongs.get() > 0, "the client never checked the server while publishing");
        client.closeHandler(null);
        client.disconnect().onComplete(ctx.asyncAssertSuccess(v -> done.complete()));
      });
    }));
    done.awaitSuccess(10000);
  }
}
