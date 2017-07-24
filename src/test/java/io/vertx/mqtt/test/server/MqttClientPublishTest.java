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

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.mqtt.MqttEndpoint;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.charset.Charset;

/**
 * MQTT server testing about clients publish
 */
@RunWith(VertxUnitRunner.class)
public class MqttClientPublishTest extends MqttBaseTest {

  private static final Logger log = LoggerFactory.getLogger(MqttClientPublishTest.class);

  private Async async;

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

    this.async = context.async();

    try {
      MemoryPersistence persistence = new MemoryPersistence();
      MqttClient client = new MqttClient(String.format("tcp://%s:%d", MQTT_SERVER_HOST, MQTT_SERVER_PORT), "12345", persistence);
      client.connect();

      client.publish(topic, message.getBytes(), qos, false);

      this.async.await();

      context.assertTrue(true);

    } catch (MqttException e) {

      context.assertTrue(false);
      e.printStackTrace();
    }
  }

  @Override
  protected void endpointHandler(MqttEndpoint endpoint) {

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
