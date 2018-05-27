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
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.MqttServerOptions;
import io.vertx.test.core.tls.Cert;
import io.vertx.test.core.tls.Trust;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.net.ssl.SSLPeerUnverifiedException;
import java.nio.charset.Charset;
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
    MqttServerOptions options = new MqttServerOptions()
      .setPort(MQTT_SERVER_TLS_PORT)
      .setKeyCertOptions(Cert.SERVER_PEM_ROOT_CA.get())
      .setTrustOptions(Trust.SERVER_PEM_ROOT_CA.get())
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
      options.setSocketFactory(this.getSocketFactory("/tls/client-truststore-root-ca.jks", "/tls/client-keystore-root-ca.jks"));
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

      context.assertEquals(clientCert.getSubjectX500Principal().getName(), "CN=client");
      context.assertEquals(clientCaCert.getSubjectX500Principal().getName(), "CN=Root CA");
    } catch (MqttException e) {
      e.printStackTrace();
      context.assertTrue(false);
    } catch (Exception e) {
      e.printStackTrace();
      context.assertTrue(false);
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
      options.setSocketFactory(this.getSocketFactory("/tls/client-truststore-root-ca.jks", "/tls/client-keystore-other-ca.jks"));
      client.connect(options);

      client.publish(MQTT_TOPIC, MQTT_MESSAGE.getBytes(), 0, false);

      this.async.await();

      client.disconnect();

      context.assertTrue(this.clientConnectedWithSsl);
      context.assertFalse(this.receivedClientCertificateValidated);
      context.assertNull(receivedClientPeerCertificates);

    } catch (MqttException e) {
      e.printStackTrace();
      context.assertTrue(false);
    } catch (Exception e) {
      e.printStackTrace();
      context.assertTrue(false);
    }
  }

  @After
  public void after(TestContext context) {

    this.tearDown(context);
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
