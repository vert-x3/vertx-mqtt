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

import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.MqttServerOptions;
import io.vertx.test.tls.Cert;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.charset.Charset;

/**
 * MQTT server testing about using SSL/TLS
 */
@RunWith(VertxUnitRunner.class)
public class MqttServerSslTest extends MqttServerBaseTest {

  private static final Logger log = LoggerFactory.getLogger(MqttServerSslTest.class);

  private Async async;

  private static final String MQTT_TOPIC = "/my_topic";
  private static final String MQTT_MESSAGE = "Hello Vert.x MQTT Server";

  @Before
  public void before(TestContext context) {

    MqttServerOptions options = new MqttServerOptions()
      .setPort(MQTT_SERVER_TLS_PORT)
      .setKeyCertOptions(Cert.SERVER_PEM_ROOT_CA.get())
      .setSsl(true);

    // just useful for enabling decryption using Wireshark (which doesn't support default Diffie-Hellmann for key exchange)
    // options.addEnabledCipherSuite("TLS_RSA_WITH_AES_256_CBC_SHA256");

    this.setUp(context, options);
  }

  @Test
  public void connection(TestContext context) {

    this.async = context.async();

    try {

      MemoryPersistence persistence = new MemoryPersistence();
      MqttClient client = new MqttClient(String.format("ssl://%s:%d", MQTT_SERVER_HOST, MQTT_SERVER_TLS_PORT), "12345", persistence);

      MqttConnectOptions options = new MqttConnectOptions();
      options.setSocketFactory(this.getSocketFactory("/tls/client-truststore-root-ca.jks", null));
      client.connect(options);

      client.publish(MQTT_TOPIC, MQTT_MESSAGE.getBytes(), 0, false);

      this.async.await();

      client.disconnect();

      context.assertTrue(true);

    } catch (MqttException e) {
      e.printStackTrace();
      context.assertTrue(false);
    } catch (Exception e1) {
      e1.printStackTrace();
      context.assertTrue(false);
    }
  }

  @After
  public void after(TestContext context) {

    this.tearDown(context);
  }

  @Override
  protected void endpointHandler(MqttEndpoint endpoint, TestContext context) {

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
