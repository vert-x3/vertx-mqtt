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
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.eclipse.paho.mqttv5.common.packet.UserProperty;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

/**
 * MQTT server testing about clients publish
 */
@RunWith(VertxUnitRunner.class)
public class Mqtt5ServerClientPublishTest extends MqttServerBaseTest {

  private static final Logger log = LoggerFactory.getLogger(Mqtt5ServerClientPublishTest.class);

  private Async async;

  private String expectedMessage;
  private String expectedLabel;

  private static final String MQTT_TOPIC = "/my_topic";
  private static final String MQTT_MESSAGE = "Hello Vert.x MQTT Server";

  @Before
  public void before(TestContext context) {

    this.setUp(context);
  }

  @After
  public void after(TestContext context) {

    this.tearDown(context);
  }

  @Test
  public void publishQos0(TestContext context) {

    this.publish(context, MQTT_TOPIC, MQTT_MESSAGE, 0, "red");
  }

  @Test
  public void publishQos1(TestContext context) {

    this.publish(context, MQTT_TOPIC, MQTT_MESSAGE, 1, "green");
  }

  @Test
  public void publishQos2(TestContext context) {

    this.publish(context, MQTT_TOPIC, MQTT_MESSAGE, 2, "blue");
  }

  private void publish(TestContext context, String topic, String message, int qos, String label) {

    this.async = context.async();

    try {
      MemoryPersistence persistence = new MemoryPersistence();
      MqttClient client = new MqttClient(String.format("tcp://%s:%d", MQTT_SERVER_HOST, MQTT_SERVER_PORT), "12345", persistence);
      client.connect();

      expectedMessage = message;
      expectedLabel = label;


      MqttProperties mqttProperties = new MqttProperties();
      mqttProperties.setUserProperties(Collections.singletonList(new UserProperty("label", label)));
      MqttMessage mqttMessage = new MqttMessage(message.getBytes(StandardCharsets.UTF_8), qos, false, mqttProperties);

      client.publish(topic, mqttMessage);

      this.async.await();

      context.assertTrue(true);

    } catch (MqttException e) {
      e.printStackTrace();
      context.assertTrue(false);
    }
  }

  @Override
  protected void endpointHandler(MqttEndpoint endpoint, TestContext context) {

    endpoint.publishHandler(message -> {

      String messageText = message.payload().toString(StandardCharsets.UTF_8);
      log.info("Just received message on [" + message.topicName() + "] payload [" + messageText + "] with QoS [" + message.qosLevel() + "]");

      context.assertEquals(expectedMessage, messageText);
      io.netty.handler.codec.mqtt.MqttProperties.UserProperties userProps =
        (io.netty.handler.codec.mqtt.MqttProperties.UserProperties)message.properties().getProperty(io.netty.handler.codec.mqtt.MqttProperties.MqttPropertyType.USER_PROPERTY.value());
      context.assertEquals(1, userProps.value().size());
      context.assertEquals("label", userProps.value().get(0).key);
      context.assertEquals(expectedLabel, userProps.value().get(0).value);

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
