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

package io.vertx.mqtt.test.server;

import io.vertx.core.Vertx;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.MqttServer;
import io.vertx.mqtt.MqttServerOptions;
import io.vertx.test.tls.Cert;
import io.vertx.test.tls.Trust;
import org.junit.runner.RunWith;

import javax.net.ssl.*;
import java.io.InputStream;
import java.security.KeyStore;

/**
 * Base class for MQTT server unit tests
 */
@RunWith(VertxUnitRunner.class)
public abstract class MqttServerBaseTest {

  private static final Logger log = LoggerFactory.getLogger(MqttServerBaseTest.class);

  protected static final String MQTT_SERVER_HOST = "localhost";
  protected static final int MQTT_SERVER_PORT = 1883;
  protected static final int MQTT_SERVER_TLS_PORT = 8883;

  protected Vertx vertx;
  protected MqttServer mqttServer;
  protected Throwable rejection;

  /**
   * Setup the needs for starting the MQTT server
   *
   * @param context TestContext instance
   * @param options MQTT server options
   */
  protected void setUp(TestContext context, MqttServerOptions options) {

    this.vertx = Vertx.vertx();
    if (options == null) {
      this.mqttServer = MqttServer.create(this.vertx);
    } else {
      this.mqttServer = MqttServer.create(this.vertx, options);
    }

    this.mqttServer.exceptionHandler(err -> {
      rejection = err;
    });

    Async async = context.async();
    this.mqttServer.endpointHandler(endpoint -> endpointHandler(endpoint, context))
      .listen()
      .onComplete(context.asyncAssertSuccess(res -> {
      log.info("MQTT server listening on port " + res.actualPort());
      async.complete();
    }));
    // Synchronous start since the proxy might be triggered in method overrides
    async.awaitSuccess(15000);
  }

  /**
   * Setup the needs for starting the MQTT server
   *
   * @param context TestContext instance
   */
  protected void setUp(TestContext context) {
    this.setUp(context, null);
  }

  /**
   * Teardown the stuff used for testing (i.e. MQTT server)
   *
   * @param context TestContext instance
   */
  protected void tearDown(TestContext context) {

    this.mqttServer.close().onComplete(context.asyncAssertSuccess());
    this.vertx.close().onComplete(context.asyncAssertSuccess());
  }

  protected void endpointHandler(MqttEndpoint endpoint, TestContext context) {

    endpoint.accept(false);
  }

  /**
   * Socket factory for Paho MQTT client so that used trust store and keystore can be configured from vertx-core test resources.
   *
   * @param trustStoreName Trust storename in classpath format
   * @param keyStoreName Key store name in classpath format
   * @return SSLSocketFactory instance with requested stores
   * @throws Exception
   */
  protected SSLSocketFactory getSocketFactory(String trustStoreName, String keyStoreName) throws Exception {

    InputStream clientTrustStoreInput = Trust.class.getResourceAsStream(trustStoreName);

    KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
    trustStore.load(clientTrustStoreInput, "wibble".toCharArray());

    TrustManagerFactory tmf = TrustManagerFactory.getInstance("X509");
    tmf.init(trustStore);

    KeyManager[] keyManagers = null;

    if (keyStoreName != null) {
      InputStream clientKeyStoreInput = Cert.class.getResourceAsStream(keyStoreName);

      KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
      keyStore.load(clientKeyStoreInput, "wibble".toCharArray());

      KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      kmf.init(keyStore, "wibble".toCharArray());

      keyManagers = kmf.getKeyManagers();
    }

    SSLContext context = SSLContext.getInstance("TLSv1.2");
    context.init(keyManagers, tmf.getTrustManagers(), null);

    return context.getSocketFactory();
  }
}
