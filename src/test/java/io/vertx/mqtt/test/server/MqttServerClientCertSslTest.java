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

import io.vertx.core.http.ClientAuth;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.core.net.PemTrustOptions;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.MqttServerOptions;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

/**
 * MQTT server testing using SSL/TLS with client certificates
 */
@RunWith(VertxUnitRunner.class)
public class MqttServerClientCertSslTest extends MqttServerBaseTest {

  private static final Logger log = LoggerFactory.getLogger(MqttServerClientCertSslTest.class);

  private Async async;

  private static final String MQTT_TOPIC = "/my_topic";
  private static final String MQTT_MESSAGE = "Hello Vert.x MQTT Server";

  private Certificate[] receivedClientPeerCertificates = null;
  private boolean receivedClientCertificateValidated = false;
  private boolean disconnectUntrustedClient = false;
  private boolean clientConnectedWithSsl = false;

  @Before
  public void before(TestContext context) {

    PemKeyCertOptions pemKeyCertOptions = new PemKeyCertOptions()
      .setKeyPath("tls/server-key.pem")
      .setCertPath("tls/server-cert.pem");

    PemTrustOptions pemTrustOptions = new PemTrustOptions()
      .addCertPath("tls/client-trusted-root-ca/ca-cert.pem");

    MqttServerOptions options = new MqttServerOptions()
      .setPort(MQTT_SERVER_TLS_PORT)
      .setKeyCertOptions(pemKeyCertOptions)
      .setTrustOptions(pemTrustOptions)
      .setSsl(true)
      .setClientAuth(ClientAuth.REQUEST);

    this.setUp(context, options);
  }

  private void resetClientInfo() {
    this.clientConnectedWithSsl = false;
    this.receivedClientCertificateValidated = false;
    this.receivedClientPeerCertificates = null;
  }

  @Test
  public void trustedClient(TestContext context) {

    resetClientInfo();

    this.async = context.async();

    try {

      MemoryPersistence persistence = new MemoryPersistence();
      MqttClient client = new MqttClient(String.format("ssl://%s:%d", MQTT_SERVER_HOST, MQTT_SERVER_TLS_PORT), "12345", persistence);

      MqttConnectOptions options = new MqttConnectOptions();
      options.setSocketFactory(this.getSocketFactory("/tls/client-truststore.jks", "/tls/client-trusted-store.jks"));
      client.connect(options);

      client.publish(MQTT_TOPIC, MQTT_MESSAGE.getBytes(), 0, false);

      this.async.await();

      client.disconnect();

      context.assertTrue(this.clientConnectedWithSsl);
      context.assertTrue(this.receivedClientCertificateValidated);
      context.assertNotNull(this.receivedClientPeerCertificates);

      // Our client certificate chain has no intermediate CA's -- only root CA and client certificate
      context.assertEquals(this.receivedClientPeerCertificates.length,2);
      context.assertTrue(this.receivedClientPeerCertificates[0] instanceof X509Certificate);
      context.assertTrue(this.receivedClientPeerCertificates[1] instanceof X509Certificate);

      X509Certificate clientCert = (X509Certificate) this.receivedClientPeerCertificates[0];
      X509Certificate clientCaCert = (X509Certificate) this.receivedClientPeerCertificates[1];

      context.assertEquals(clientCert.getSubjectX500Principal().getName(), "CN=Client Trusted Device");
      context.assertEquals(clientCaCert.getSubjectX500Principal().getName(), "O=Client Trusted Root CA");
    } catch (MqttException e) {
      context.assertTrue(false);
      e.printStackTrace();
    } catch (Exception e) {
      context.assertTrue(false);
      e.printStackTrace();
    }
  }

  @Test
  public void untrustedClient(TestContext context) {

    resetClientInfo();

    this.async = context.async();

    try {

      MemoryPersistence persistence = new MemoryPersistence();
      MqttClient client = new MqttClient(String.format("ssl://%s:%d", MQTT_SERVER_HOST, MQTT_SERVER_TLS_PORT), "12345", persistence);

      MqttConnectOptions options = new MqttConnectOptions();
      options.setSocketFactory(this.getSocketFactory("/tls/client-truststore.jks", "/tls/client-untrusted-store.jks"));
      client.connect(options);

      client.publish(MQTT_TOPIC, MQTT_MESSAGE.getBytes(), 0, false);

      this.async.await();

      client.disconnect();

      context.assertTrue(this.clientConnectedWithSsl);
      context.assertFalse(this.receivedClientCertificateValidated);
      context.assertNull(receivedClientPeerCertificates);

    } catch (MqttException e) {
      context.assertTrue(false);
      e.printStackTrace();
    } catch (Exception e) {
      context.assertTrue(false);
      e.printStackTrace();
    }
  }

  @After
  public void after(TestContext context) {

    this.tearDown(context);
  }

  private SSLSocketFactory getSocketFactory(String trustStoreName, String keyStoreName) throws Exception {

    URL clientTrustStore = this.getClass().getResource(trustStoreName);

    FileInputStream clientTrustStoreInput = new FileInputStream(clientTrustStore.getPath());

    KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
    trustStore.load(clientTrustStoreInput, "wibble".toCharArray());

    TrustManagerFactory tmf = TrustManagerFactory.getInstance("X509");
    tmf.init(trustStore);

    KeyManager[] keyManagers = null;

    if (keyStoreName != null) {
      URL clientKeyStore = this.getClass().getResource(keyStoreName);

      FileInputStream clientKeyStoreInput = new FileInputStream(clientKeyStore.getPath());

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

  @Override
  protected void endpointHandler(MqttEndpoint endpoint, TestContext context) {

    this.clientConnectedWithSsl = endpoint.isSsl();

    try {
      receivedClientPeerCertificates = endpoint.sslSession().getPeerCertificates();
      receivedClientCertificateValidated = true;
      log.info("Trusted client connected");
    } catch (SSLPeerUnverifiedException e) {
      if (disconnectUntrustedClient) {
        log.info("Disconnecting untrusted client");
        endpoint.close();
        this.async.complete();
        return;
      }
      log.info("Untrusted client connected");
    }

    endpoint.publishHandler(message -> {

      log.info("Just received message on [" + message.topicName() + "] payload [" + message.payload().toString(Charset.defaultCharset()) + "] with QoS [" + message.qosLevel() + "]");

      switch (message.qosLevel()) {

        case AT_LEAST_ONCE:

          endpoint.publishAcknowledge(message.messageId());
          this.async.complete();
          break;

        case EXACTLY_ONCE:

          endpoint.publishReceived(message.messageId());
          break;

        case AT_MOST_ONCE:

          this.async.complete();
          break;
      }

    }).publishReleaseHandler(messageId -> {

      endpoint.publishComplete(messageId);
      this.async.complete();
    });

    endpoint.accept(false);
  }

}
