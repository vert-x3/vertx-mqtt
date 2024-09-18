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
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.net.JksOptions;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.core.net.SelfSignedCertificate;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;
import io.vertx.mqtt.MqttServer;
import io.vertx.mqtt.MqttServerOptions;
import io.vertx.test.tls.Cert;
import io.vertx.test.tls.Trust;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * MQTT client testing about using SSL/TLS
 */
@RunWith(VertxUnitRunner.class)
public class MqttClientSslTest {

  private static final Logger log = LoggerFactory.getLogger(MqttClientSslTest.class);
  private static final int MQTT_SERVER_TLS_PORT = 8883;
  private static final String MQTT_SERVER_HOST = "localhost";

  Vertx vertx;
  MqttServer server;
  MqttClient client;

  @Test
  public void clientSslTrustAllTest() {
    MqttClientOptions clientOptions = new MqttClientOptions()
      .setSsl(true)
      .setHostnameVerificationAlgorithm("")
      .setTrustAll(true);

    client = MqttClient.create(vertx, clientOptions);

    client.connect(MQTT_SERVER_TLS_PORT, MQTT_SERVER_HOST)
      .compose(msg -> client.disconnect())
      .await();
  }

  @Test
  public void clientSslClientTruststoreTest(TestContext context) {

    JksOptions jksOptions = Trust.SERVER_JKS.get();

    MqttClientOptions clientOptions = new MqttClientOptions()
      .setSsl(true)
      .setHostnameVerificationAlgorithm("")
      .setTrustOptions(jksOptions);

    client = MqttClient.create(vertx, clientOptions);
    client.exceptionHandler(t -> context.assertTrue(false));

    client.connect(MQTT_SERVER_TLS_PORT, MQTT_SERVER_HOST)
      .compose(msg -> client.disconnect())
      .onComplete(context.asyncAssertSuccess());
  }

  @Before
  public void before() {
    this.vertx = Vertx.vertx();

    PemKeyCertOptions pemKeyCertOptions = Cert.SERVER_PEM.get();

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
    }).listen().await();
  }

  @After
  public void after() {
    this.server.close();
    this.vertx.close();
  }
}
