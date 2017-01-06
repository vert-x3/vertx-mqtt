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

package io.vertx.mqtt.test;

import io.vertx.core.net.PemKeyCertOptions;
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

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.security.KeyStore;

/**
 * MQTT server testing about using SSL/TLS
 */
@RunWith(VertxUnitRunner.class)
public class MqttSslTest extends MqttBaseTest {

  private Async async;

  private static final String MQTT_TOPIC = "/my_topic";
  private static final String MQTT_MESSAGE = "Hello Vert.x MQTT Server";

  @Before
  public void before(TestContext context) {

    PemKeyCertOptions pemKeyCertOptions = new PemKeyCertOptions()
      .setKeyPath("tls/server-key.pem")
      .setCertPath("tls/server-cert.pem");

    MqttServerOptions options = new MqttServerOptions()
      .setPort(MQTT_SERVER_TLS_PORT)
      .setKeyCertOptions(pemKeyCertOptions)
      .setSsl(true);

    this.setUp(context, options);
  }

  @Test
  public void connection(TestContext context) {

    this.async = context.async();

    try {

      MemoryPersistence persistence = new MemoryPersistence();
      MqttClient client = new MqttClient(String.format("ssl://%s:%d", MQTT_SERVER_HOST, MQTT_SERVER_TLS_PORT), "12345", persistence);


      URL trustStore =  this.getClass().getResource("/tls/client-truststore.jks");
      System.setProperty("javax.net.ssl.trustStore", trustStore.getPath());
      System.setProperty("javax.net.ssl.trustStorePassword", "wibble");

      client.connect();

      /*
      MqttConnectOptions options = new MqttConnectOptions();
      options.setSocketFactory(this.getSocketFactory());
      client.connect(options);
      */

      client.publish(MQTT_TOPIC, MQTT_MESSAGE.getBytes(), 0, false);

      this.async.await();

      client.disconnect();

      context.assertTrue(true);

    } catch (MqttException e) {
      context.assertTrue(false);
      e.printStackTrace();
    } catch (Exception e1) {

    }
  }

  @After
  public void after(TestContext context) {

    this.tearDown(context);
  }

  private SSLSocketFactory getSocketFactory() throws Exception {

    URL trustStore =  this.getClass().getResource("/tls/client-truststore.jks");

    FileInputStream input = new FileInputStream(trustStore.getPath());

    KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
    ks.load(input, "wibble".toCharArray());

    TrustManagerFactory tmf = TrustManagerFactory.getInstance("X509");
    tmf.init(ks);

    SSLContext context = SSLContext.getInstance("TLSv1.2");
    context.init(null, tmf.getTrustManagers(), null);

    return context.getSocketFactory();

  }

  protected void endpointHandler(MqttEndpoint endpoint) {

    endpoint.publishHandler(message -> {

      System.out.println("Just received message on [" + message.topicName() + "] payload [" + message.payload().toString(Charset.defaultCharset()) + "] with QoS [" + message.qosLevel() + "]");

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
