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

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetSocket;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;
import io.vertx.mqtt.MqttServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Client connect tests
 */
@RunWith(VertxUnitRunner.class)
public class MqttConnectTest {

  private Vertx vertx;
  private MqttServer server;
  private NetServer proxyServer;

  @Before
  public void before() {
    vertx = Vertx.vertx();
    server = MqttServer.create(vertx);
    proxyServer = vertx.createNetServer();
  }

  @After
  public void after(TestContext ctx) {
    proxyServer.close(ctx.asyncAssertSuccess(v1 -> {
      server.close(ctx.asyncAssertSuccess(v2 -> {
        vertx.close(ctx.asyncAssertSuccess());
      }));
    }));
  }

  @Test
  public void concurrentConnect(TestContext ctx) {
    server.endpointHandler(endpoint -> {
      endpoint.accept(false);
      endpoint.publish("test", Buffer.buffer(), MqttQoS.AT_LEAST_ONCE, false, false);
    });
    Async serverLatch = ctx.async();
    server.listen(MqttClientOptions.DEFAULT_PORT, ctx.asyncAssertSuccess(v -> serverLatch.complete()));
    serverLatch.awaitSuccess(10000);
    MqttClient client = MqttClient.create(vertx);
    Async msglatch = ctx.async();
    client.connect(MqttClientOptions.DEFAULT_PORT, "localhost", ctx.asyncAssertSuccess(ack -> {
      client.publishHandler(msg -> {
        msglatch.complete();
      });
    }));
    client.connect(MqttClientOptions.DEFAULT_PORT, "localhost", ctx.asyncAssertFailure(err -> {
      ctx.assertEquals(IllegalStateException.class, err.getClass());
    }));
  }

  @Test
  public void reconnectInCloseHandler(TestContext ctx) {
    server.endpointHandler(endpoint -> {
      endpoint.accept(false);
    });
    Async serverLatch = ctx.async();
    server.listen(0, ctx.asyncAssertSuccess(v -> serverLatch.complete()));
    serverLatch.awaitSuccess(10000);
    int port = server.actualPort();
    MqttClient client = MqttClient.create(vertx);
    NetClient proxyClient = vertx.createNetClient();
    proxyServer.connectHandler(so1 -> {
      so1.pause();
      proxyClient.connect(port, "localhost", ar -> {
        if (ar.succeeded()) {
          NetSocket so2 = ar.result();
          vertx.setTimer(1000, id -> {
            so1.close();
          });
          so1.handler(so2::write);
          so2.handler(so1::write);
          so1.closeHandler(v -> {
            so2.close();
          });
          so2.closeHandler(v -> {
            so1.close();
          });
        } else {
          so1.close();
        }
        so1.resume();
      });
    });
    Async proxyLatch = ctx.async();
    proxyServer.listen(MqttClientOptions.DEFAULT_PORT, MqttClientOptions.DEFAULT_HOST, ctx.asyncAssertSuccess(v -> proxyLatch.complete()));
    proxyLatch.awaitSuccess(20_000);
    Async async = ctx.async();
    client.connect(MqttClientOptions.DEFAULT_PORT, MqttClientOptions.DEFAULT_HOST, ctx.asyncAssertSuccess(ack1 -> {
      client.closeHandler(v1 -> {
        client.connect(MqttClientOptions.DEFAULT_PORT, MqttClientOptions.DEFAULT_HOST, ctx.asyncAssertSuccess(ack2 -> {
          client.closeHandler(v2 -> {
            async.complete();
          });
        }));
      });
    }));
  }
}
