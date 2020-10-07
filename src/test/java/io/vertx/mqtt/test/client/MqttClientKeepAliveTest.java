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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeoutException;
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
  public void clientWillDisconnectOnMissingPingResponse(TestContext ctx) {
    AtomicInteger pings = new AtomicInteger();
    server.endpointHandler(endpoint -> {
      endpoint.autoKeepAlive(false); // Tell the server not to respond to PINGREQ
      endpoint.accept(false);
      endpoint.pingHandler(v -> pings.incrementAndGet());
    });
    startServer(ctx);
    Async async = ctx.async();
    MqttClientOptions options = new MqttClientOptions();
    options.setKeepAliveTimeSeconds(2);
    options.setKeepAliveTimeout(1);
    MqttClient client = MqttClient.create(vertx, options);
    client.connect(1884, "localhost", ctx.asyncAssertSuccess(ack -> {
      client.closeHandler(v -> {
        assertEquals(1, pings.get());
        async.complete();
      });
    }));
    async.await();
  }

}
