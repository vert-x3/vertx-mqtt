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

import io.netty.handler.codec.mqtt.MqttQoS;
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

import java.util.ArrayList;
import java.util.List;

/**
 * MQTT server testing about clients subscription
 */
@RunWith(VertxUnitRunner.class)
public class MqttServerSubscribeTest extends MqttServerBaseTest {

  private static final Logger log = LoggerFactory.getLogger(MqttServerSubscribeTest.class);

  private Async async;

  private static final String MQTT_TOPIC = "/my_topic";
  private static final String MQTT_TOPIC_FAILURE = "/my_topic/failure";

  @Before
  public void before(TestContext context) {

    this.setUp(context);
  }

  @After
  public void after(TestContext context) {

    this.tearDown(context);
  }

  @Test
  public void subscribeQos0(TestContext context) {

    this.subscribe(context, MQTT_TOPIC, 0);
  }

  @Test
  public void subscribeQos1(TestContext context) {

    this.subscribe(context, MQTT_TOPIC, 1);
  }

  @Test
  public void subscribeQos2(TestContext context) {

    this.subscribe(context, MQTT_TOPIC, 2);
  }

  @Test
  public void subscribeFailure(TestContext context) {

    this.subscribe(context, MQTT_TOPIC_FAILURE, 0);
  }

  private void subscribe(TestContext context, String topic, int expectedQos) {

    this.async = context.async();

    try {
      MemoryPersistence persistence = new MemoryPersistence();
      MqttClient client = new MqttClient(String.format("tcp://%s:%d", MQTT_SERVER_HOST, MQTT_SERVER_PORT), "12345", persistence);
      client.connect();

      String[] topics = new String[]{topic};
      int[] qos = new int[]{expectedQos};
      // after calling subscribe, the qos is replaced with granted QoS that should be the same
      client.subscribe(topics, qos);

      this.async.await();

      context.assertTrue(qos[0] == expectedQos);

    } catch (MqttException e) {

      context.assertTrue(!topic.equals(MQTT_TOPIC_FAILURE) ? false : true);
      e.printStackTrace();
    }
  }

  @Override
  protected void endpointHandler(MqttEndpoint endpoint) {

    endpoint.subscribeHandler(subscribe -> {

      List<MqttQoS> qos = new ArrayList<>();

      MqttQoS grantedQos =
        subscribe.topicSubscriptions().get(0).topicName().equals(MQTT_TOPIC_FAILURE) ?
          MqttQoS.FAILURE :
          subscribe.topicSubscriptions().get(0).qualityOfService();

      qos.add(grantedQos);
      endpoint.subscribeAcknowledge(subscribe.messageId(), qos);

      this.async.complete();
    });

    endpoint.accept(false);
  }
}
