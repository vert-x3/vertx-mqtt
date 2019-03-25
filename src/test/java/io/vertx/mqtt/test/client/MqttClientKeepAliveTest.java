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

import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;
import io.vertx.mqtt.MqttServer;
import io.vertx.mqtt.impl.MqttServerImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

/**
 * MQTT client keep alive tests using a Vert.x MQTT server to accomodate testing.
 */
@RunWith(VertxUnitRunner.class)
public class MqttClientKeepAliveTest {

  private Vertx vertx;
  private MqttServer server;

  private void startServer(TestContext ctx) {
    Async async = ctx.async();
    server.listen(1884, "localhost", ctx.asyncAssertSuccess(server -> async.complete()));
    async.awaitSuccess(10000);
  }

  @Before
  public void before(TestContext ctx) {
    vertx = Vertx.vertx();
    server = MqttServer.create(vertx);
  }

  @After
  public void after(TestContext ctx) {
    vertx.close(ctx.asyncAssertSuccess());
  }

  @Test
  public void expireClientPingOnMissingPingResponse(TestContext ctx) {
    AtomicInteger pings = new AtomicInteger();
    ((MqttServerImpl)server).keepAliveCheck(false); // Tell the server to not evict us
    server.endpointHandler(endpoint -> {
      endpoint.autoKeepAlive(false);
      endpoint.accept(false);
      endpoint.pingHandler(v -> pings.incrementAndGet());
    });
    startServer(ctx);
    Async async = ctx.async();
    MqttClientOptions options = new MqttClientOptions();
    options.setAutoKeepAlive(true);
    options.setKeepAliveTimeSeconds(1);
    options.setKeepAliveTimeout(4);
    MqttClient client = MqttClient.create(vertx, options);
    client.connect(1884, "localhost", ctx.asyncAssertSuccess(ack -> {
      long now = System.currentTimeMillis();
      client.closeHandler(v -> {
        long delta = System.currentTimeMillis() - now;
        long val = Math.round(delta / 1000D);
        assertEquals(5, val);
        assertEquals(1, pings.get());
        async.complete();
      });
    }));
    async.await();
  }

  @Test
  public void serverWillDisconnectWhenKeepAliveSet(TestContext ctx) {
    Async async = ctx.async();
    server.endpointHandler(endpoint -> {
      endpoint.accept(false);
      endpoint.pingHandler(v -> ctx.fail());
    });
    startServer(ctx);
    MqttClientOptions options = new MqttClientOptions();
    options.setAutoKeepAlive(false);    // The client will manage pings
    options.setKeepAliveTimeSeconds(2); // Tell the server to disconnects the client after 3 seconds of inactivity
    MqttClient client = MqttClient.create(vertx, options);
    client.connect(1884, "localhost", ctx.asyncAssertSuccess(ack -> {
      long now = System.currentTimeMillis();
      client.closeHandler(v -> {
        long delta = System.currentTimeMillis() - now;
        long val = Math.round(delta / 1000D);
        // MQTT-3.1.2-24 - 3 = 2 + 1
        ctx.assertEquals(3L, val);
        async.complete();
      });
    }));
    async.await();
  }
}
