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

import io.netty.handler.codec.mqtt.MqttProperties;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.MqttTopicSubscription;
import org.eclipse.paho.mqttv5.client.IMqttMessageListener;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.MqttSubscription;
import org.eclipse.paho.mqttv5.common.packet.UserProperty;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * MQTT server testing about server publish
 */
public class Mqtt5ServerPublishTest extends MqttServerBaseTest {

  private static final Logger log = LoggerFactory.getLogger(Mqtt5ServerPublishTest.class);

  private Async async;

  private static final String MQTT_TOPIC = "/my_topic";
  private static final String MQTT_MESSAGE = "Hello Vert.x MQTT Server";

  private String topic;
  private String message;
  private MqttProperties properties;

  private AtomicInteger nextMessageId = new AtomicInteger(0);

  private AtomicReference<org.eclipse.paho.mqttv5.common.packet.MqttProperties> lastMessageProperties = new AtomicReference<>(null);

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
    MqttProperties props = new MqttProperties();
    String expectedContentType = "plain/text";
    props.add(new MqttProperties.StringProperty(MqttProperties.MqttPropertyType.CONTENT_TYPE.value(), expectedContentType));

    this.publish(context, MQTT_TOPIC, MQTT_MESSAGE, 0, props);

    context.assertNotNull(lastMessageProperties.get());
    context.assertEquals(expectedContentType, lastMessageProperties.get().getContentType());
  }

  @Test
  public void publishQos1(TestContext context) {
    MqttProperties props = new MqttProperties();
    props.add(new MqttProperties.UserProperty("priority", "fast"));

    this.publish(context, MQTT_TOPIC, MQTT_MESSAGE, 1, props);

    context.assertNotNull(lastMessageProperties.get());
    UserProperty userProp = lastMessageProperties.get().getUserProperties().get(0);
    context.assertNotNull(userProp);
    context.assertEquals(userProp.getKey(), "priority");
    context.assertEquals(userProp.getValue(), "fast");
  }

  @Test
  public void publishQos2(TestContext context) {
    MqttProperties props = new MqttProperties();
    int expectedMessageExpiry = 234;
    props.add(new MqttProperties.IntegerProperty(MqttProperties.MqttPropertyType.PUBLICATION_EXPIRY_INTERVAL.value(), expectedMessageExpiry));

    this.publish(context, MQTT_TOPIC, MQTT_MESSAGE, 2, props);

    context.assertNotNull(lastMessageProperties.get());
    context.assertEquals((long)expectedMessageExpiry, lastMessageProperties.get().getMessageExpiryInterval());
  }

  private void publish(TestContext context, String topic, String message, int qos, MqttProperties properties) {

    this.topic = topic;
    this.message = message;
    this.properties = properties;

    this.async = context.async(2);

    try {
      MemoryPersistence persistence = new MemoryPersistence();
      MqttClient client = new MqttClient(String.format("tcp://%s:%d", MQTT_SERVER_HOST, MQTT_SERVER_PORT), "12345", persistence);
      client.connect();

      MqttSubscription[] subscriptions = new MqttSubscription[]{new MqttSubscription(topic, qos)};
      client.subscribe(subscriptions, new IMqttMessageListener[] {new IMqttMessageListener() {

        @Override
        public void messageArrived(String topic, MqttMessage mqttMessage) throws Exception {

          log.info("Just received message [" + mqttMessage.toString() + "] on topic [" + topic + "] with QoS [" + mqttMessage.getQos() + "] and properties [" + mqttMessage.getProperties() + "]");

          lastMessageProperties.set(mqttMessage.getProperties());
          async.countDown();
          if (mqttMessage.getQos() == 0)
            async.complete();

        }
      }});

      this.async.await();

    } catch (MqttException e) {
      context.fail(e);
    }
  }

  @Override
  protected void endpointHandler(MqttEndpoint endpoint, TestContext context) {

    endpoint.subscribeHandler(subscribe -> {

      endpoint.subscribeAcknowledge(subscribe.messageId(),
        subscribe.topicSubscriptions()
          .stream()
          .map(MqttTopicSubscription::qualityOfService)
          .collect(Collectors.toList()));

      endpoint.publish(this.topic,
        Buffer.buffer(this.message),
        subscribe.topicSubscriptions().get(0).qualityOfService(),
        false,
        false,
        nextMessageId.getAndIncrement(),
        properties,
        publishSent -> {
        context.assertTrue(publishSent.succeeded());
      });
    }).publishAcknowledgeHandler(messageId -> {

      log.info("Message [" + messageId + "] acknowledged");
      async.countDown();
    }).publishReceivedHandler(messageId -> {

      endpoint.publishRelease(messageId);
    }).publishCompletionHandler(messageId -> {

      log.info("Message [" + messageId + "] completed");
      async.countDown();
    });

    endpoint.accept(false);
  }
}
