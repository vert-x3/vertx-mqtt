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

import io.vertx.core.buffer.Buffer;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.MqttTopicSubscription;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.stream.Collectors;

/**
 * MQTT server testing about server publish
 */
public class MqttServerPublishTest extends MqttBaseTest {

  private static final Logger log = LoggerFactory.getLogger(MqttServerPublishTest.class);

  private Async async;

  private static final String MQTT_TOPIC = "/my_topic";
  private static final String MQTT_MESSAGE = "Hello Vert.x MQTT Server";

  private String topic;
  private String message;

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

    this.publish(context, MQTT_TOPIC, MQTT_MESSAGE, 0);
  }

  @Test
  public void publishQos1(TestContext context) {

    this.publish(context, MQTT_TOPIC, MQTT_MESSAGE, 1);
  }

  @Test
  public void publishQos2(TestContext context) {

    this.publish(context, MQTT_TOPIC, MQTT_MESSAGE, 2);
  }

  private void publish(TestContext context, String topic, String message, int qos) {

    this.topic = topic;
    this.message = message;

    this.async = context.async();

    try {
      MemoryPersistence persistence = new MemoryPersistence();
      MqttClient client = new MqttClient(String.format("tcp://%s:%d", MQTT_SERVER_HOST, MQTT_SERVER_PORT), "12345", persistence);
      client.connect();

      client.subscribe(topic, qos, new IMqttMessageListener() {

        @Override
        public void messageArrived(String topic, MqttMessage mqttMessage) throws Exception {

          log.info("Just received message [" + mqttMessage.toString() + "] on topic [" + topic + "] with QoS [" + mqttMessage.getQos() + "]");

          if (mqttMessage.getQos() == 0)
            async.complete();
        }
      });

      this.async.await();

      context.assertTrue(true);

    } catch (MqttException e) {

      context.assertTrue(false);
      e.printStackTrace();
    }
  }

  protected void endpointHandler(MqttEndpoint endpoint) {

    endpoint.subscribeHandler(subscribe -> {

      endpoint.subscribeAcknowledge(subscribe.messageId(),
        subscribe.topicSubscriptions()
          .stream()
          .map(MqttTopicSubscription::qualityOfService)
          .collect(Collectors.toList()));

      endpoint.publish(this.topic, Buffer.buffer(this.message), subscribe.topicSubscriptions().get(0).qualityOfService(), false, false);
    }).publishAcknowledgeHandler(messageId -> {

      log.info("Message [" + messageId + "] acknowledged");
      this.async.complete();
    }).publishReceivedHandler(messageId -> {

      endpoint.publishRelease(messageId);
    }).publishCompleteHandler(messageId -> {

      log.info("Message [" + messageId + "] acknowledged");
      this.async.complete();
    });

    endpoint.accept(false);
  }
}
