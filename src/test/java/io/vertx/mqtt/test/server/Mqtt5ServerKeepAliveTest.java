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

package io.vertx.mqtt.test.server;

import io.netty.handler.codec.mqtt.MqttProperties;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetSocket;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.mqtt.MqttServer;
import io.vertx.mqtt.MqttServerOptions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * After accepting with a Server Keep Alive (MQTT 5.0 §3.2.2.3.14) the server watches that keep alive instead of the
 * one the client requested. The client is a plain socket sending exactly the packets a test needs.
 */
@RunWith(VertxUnitRunner.class)
public class Mqtt5ServerKeepAliveTest {

  private Vertx vertx;
  private MqttServer server;
  // referenced for the whole test, Vert.x closes a client that is no longer referenced
  private NetClient client;

  @Before
  public void before() {
    vertx = Vertx.vertx();
    server = MqttServer.create(vertx, new MqttServerOptions().setPort(0));
    client = vertx.createNetClient();
  }

  @After
  public void after(TestContext ctx) {
    server.close().onComplete(ctx.asyncAssertSuccess(v -> vertx.close().onComplete(ctx.asyncAssertSuccess())));
  }

  private int listenAcceptingWithServerKeepAlive(int serverKeepAlive) {
    server.endpointHandler(endpoint -> {
      MqttProperties props = new MqttProperties();
      props.add(new MqttProperties.IntegerProperty(MqttProperties.MqttPropertyType.SERVER_KEEP_ALIVE.value(), serverKeepAlive));
      endpoint.accept(false, props);
    });
    return server.listen().await().actualPort();
  }

  /**
   * @return a socket that sent an MQTT 5 CONNECT requesting the keep alive, completed with the CONNACK
   */
  private Future<NetSocket> connect(int port, int keepAlive) {
    Buffer connect = Buffer.buffer()
      .appendBytes(new byte[]{0x10, 15})                           // CONNECT, remaining length
      .appendBytes(new byte[]{0x00, 0x04, 'M', 'Q', 'T', 'T', 5})  // protocol name and level
      .appendByte((byte) 0x02)                                     // clean start
      .appendUnsignedShort(keepAlive)
      .appendByte((byte) 0x00)                                     // no properties
      .appendBytes(new byte[]{0x00, 0x02, 'k', 'a'});              // client identifier
    return client.connect(port, "localhost").compose(so -> {
      Promise<NetSocket> connack = Promise.promise();
      so.handler(buf -> connack.tryComplete(so));
      return so.write(connect).compose(v -> connack.future());
    });
  }

  /**
   * Server Keep Alive 0 switches keep alive off: a client that sends nothing is not closed.
   */
  @Test
  public void serverKeepAliveZeroKeepsSilentClientConnected(TestContext ctx) {
    int port = listenAcceptingWithServerKeepAlive(0);

    Async done = ctx.async();
    connect(port, 1).onComplete(ctx.asyncAssertSuccess(so -> {
      so.closeHandler(v -> ctx.fail("Client closed although the Server Keep Alive is 0"));
      // past the 2 s after which the requested keep alive of 1 s would close the connection
      vertx.setTimer(3000, id -> {
        so.closeHandler(null);
        done.complete();
      });
    }));
  }

  /**
   * A client that sends nothing is closed after 1.5 times the assigned keep alive, not the requested one.
   */
  @Test
  public void silentClientIsClosedAfterServerKeepAlive(TestContext ctx) {
    int port = listenAcceptingWithServerKeepAlive(1);

    Async closed = ctx.async();
    connect(port, 30).onComplete(ctx.asyncAssertSuccess(so -> {
      // closed after 2 s, with the requested 30 s it would take 45 s
      long timeout = vertx.setTimer(5000, id -> ctx.fail("Client not closed after the assigned keep alive"));
      so.closeHandler(v -> {
        vertx.cancelTimer(timeout);
        closed.complete();
      });
    }));
  }

  /**
   * A client pinging within the assigned keep alive stays connected, although it pings less often than it requested.
   */
  @Test
  public void clientPingingWithinServerKeepAliveStaysConnected(TestContext ctx) {
    int port = listenAcceptingWithServerKeepAlive(3);

    Async done = ctx.async();
    connect(port, 1).onComplete(ctx.asyncAssertSuccess(so -> {
      so.closeHandler(v -> ctx.fail("Client closed although it pinged within the Server Keep Alive"));
      // every 2.5 s: within the 4.5 s of the assigned keep alive, past the 2 s of the requested one
      long pinger = vertx.setPeriodic(2500, id -> so.write(Buffer.buffer(new byte[]{(byte) 0xC0, 0x00})));
      vertx.setTimer(6000, id -> {
        vertx.cancelTimer(pinger);
        so.closeHandler(null);
        done.complete();
      });
    }));
  }
}
