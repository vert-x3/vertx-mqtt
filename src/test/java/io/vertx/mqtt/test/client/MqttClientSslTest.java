/*
 * Copyright 2017 Red Hat Inc.
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
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.JksOptions;
import io.vertx.core.net.PemKeyCertOptions;
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

import java.net.URL;

/**
 * MQTT client testing about using SSL/TLS
 */
@RunWith(VertxUnitRunner.class)
public class MqttClientSslTest {

  private static final Logger log = LoggerFactory.getLogger(MqttClientSslTest.class);
  private static final int MQTT_SERVER_TLS_PORT = 8883;
  private static final String MQTT_SERVER_HOST = "localhost";

  Vertx vertx = Vertx.vertx();
  MqttServer server;
  TestContext context;

  @Test
  public void clientSslTrustAllTest(TestContext context) {
    MqttClientOptions clientOptions = new MqttClientOptions()
      .setPort(MQTT_SERVER_TLS_PORT)
      .setHost(MQTT_SERVER_HOST)
      .setSsl(true)
      .setTrustAll(true);

    MqttClient client = MqttClient.create(vertx, clientOptions);
    client.exceptionHandler(t -> context.assertTrue(false));

    this.context = context;
    Async async = context.async();
    client.connect(s -> client.disconnect(d -> async.countDown()));
    async.await();
  }

  @Test
  public void clientSslClientTruststoreTest(TestContext context) {

    this.context = context;
    URL trustStore = this.getClass().getResource("/tls/client-truststore.jks");
    JksOptions jksOptions = new JksOptions().setPath(trustStore.getPath());

    MqttClientOptions clientOptions = new MqttClientOptions()
      .setPort(MQTT_SERVER_TLS_PORT)
      .setHost(MQTT_SERVER_HOST)
      .setSsl(true)
      .setTrustStoreOptions(jksOptions);

    MqttClient client = MqttClient.create(vertx, clientOptions);
    client.exceptionHandler(t -> context.assertTrue(false));

    Async async = context.async();
    client.connect(s -> client.disconnect(d -> async.countDown()));
    async.await();
  }

  @Before
  public void before() {
    PemKeyCertOptions pemKeyCertOptions = new PemKeyCertOptions()
      .setKeyPath("tls/server-key.pem")
      .setCertPath("tls/server-cert.pem");

    MqttServerOptions serverOptions = new MqttServerOptions()
      .setPort(MQTT_SERVER_TLS_PORT)
      .setHost(MQTT_SERVER_HOST)
      .setKeyCertOptions(pemKeyCertOptions)
      .setSsl(true);

    server = MqttServer.create(vertx, serverOptions);

    server.endpointHandler(e -> {
      log.info("Client connected");
      e.disconnectHandler(d -> log.info("Client disconnected"));
      e.accept(false);
    }).listen(ar -> {
      if (ar.succeeded()) {
        log.info("MQTT server listening on port " + ar.result().actualPort());
      } else {
        log.error("Error starting MQTT server", ar.cause());
        System.exit(1);
      }
    });

    server.exceptionHandler(t -> context.assertTrue(false));
  }

  @After
  public void after() {
    this.server.close();
    this.vertx.close();
  }
}
