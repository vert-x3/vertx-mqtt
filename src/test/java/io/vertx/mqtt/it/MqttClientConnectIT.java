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

package io.vertx.mqtt.it;

import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;
import io.vertx.mqtt.MqttConnectionException;
import io.vertx.mqtt.MqttServer;
import io.vertx.mqtt.MqttServerOptions;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * MQTT client testing about connection
 */
@RunWith(VertxUnitRunner.class)
public class MqttClientConnectIT {

  @Test
  public void connectDisconnect(TestContext context) throws InterruptedException {
    Async async = context.async();
    MqttClient client = MqttClient.create(Vertx.vertx());

    client.connect(TestUtil.BROKER_PORT, TestUtil.BROKER_ADDRESS, c -> {

      assertTrue(c.succeeded());

      client
        .disconnect(ar -> {
          assertTrue(ar.succeeded());
          async.countDown();
        });
    });

    async.await();
  }

  @Test
  public void connectDisconnectWithIdleOption(TestContext context) {
    Async async = context.async();
    MqttClientOptions options = new MqttClientOptions();
    options.setKeepAliveTimeSeconds(100);
    MqttClient client = MqttClient.create(Vertx.vertx(),options);

    client.connect(TestUtil.BROKER_PORT, TestUtil.BROKER_ADDRESS, c -> {

      assertTrue(c.succeeded());

      client
        .disconnect(ar -> {
          assertTrue(ar.succeeded());
          async.countDown();
        });
    });

    async.await();
  }

  @Test
  public void closeHandler(TestContext context) throws InterruptedException {
    Async async = context.async();
    MqttClient client = MqttClient.create(Vertx.vertx(),
      new MqttClientOptions()
        .setKeepAliveTimeSeconds(5)
        .setAutoKeepAlive(false)
    );

    client.closeHandler((v) -> {
      async.countDown();
    });

    client.connect(TestUtil.BROKER_PORT, TestUtil.BROKER_ADDRESS, c -> {
      assertTrue(c.succeeded());
    });

    async.await();
  }

  @Test
  public void tcpConnectionFails(TestContext context) {
    Async async = context.async();
    MqttClient client = MqttClient.create(Vertx.vertx());

    client.closeHandler(v -> {
      // when TCP connection fails, this handler should not be called, connection not established
      context.fail();
    });

    client.connect(MqttClientOptions.DEFAULT_PORT, MqttClientOptions.DEFAULT_HOST, c -> {
      // connection
      assertTrue(c.failed());
      assertFalse(client.isConnected());
      async.complete();
    });

    async.await();
  }

  @Test
  public void connackNotOk(TestContext context) {
    Async async = context.async();
    Async asyncServer = context.async();
    Vertx vertx = Vertx.vertx();

    MqttServer server = MqttServer.create(vertx);
    server.endpointHandler(endpoint -> {
      endpoint.reject(MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE);
    });
    server.listen(MqttServerOptions.DEFAULT_PORT, context.asyncAssertSuccess(v -> asyncServer.complete()));
    asyncServer.await();

    MqttClient client = MqttClient.create(vertx);
    client.closeHandler(v -> {
      // when server replies with "negative" CONNACK, this handler should not be called
      // the failure is just part of the connectHandler
      context.fail();
    });

    client.connect(MqttClientOptions.DEFAULT_PORT, MqttClientOptions.DEFAULT_HOST, c -> {
      assertTrue(c.failed());
      assertTrue(c.cause() instanceof MqttConnectionException);
      MqttConnectionException connEx = (MqttConnectionException) c.cause();
      assertEquals(connEx.code(), MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE);
      assertFalse(client.isConnected());
      async.complete();
    });

    async.await();
  }
}
