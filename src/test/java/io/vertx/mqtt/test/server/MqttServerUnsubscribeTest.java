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
 * MQTT server testing about clients unsubscription
 */
@RunWith(VertxUnitRunner.class)
public class MqttServerUnsubscribeTest extends MqttServerBaseTest {

  private static final Logger log = LoggerFactory.getLogger(MqttServerUnsubscribeTest.class);

  private Async subscribeAsync;
  private Async unsubscribeAsync;

  private static final String MQTT_TOPIC = "/my_topic";

  @Before
  public void before(TestContext context) {

    this.setUp(context);
  }

  @After
  public void after(TestContext context) {

    this.tearDown(context);
  }

  @Test
  public void unsubscribe(TestContext context) {

    this.subscribeAsync = context.async();
    this.unsubscribeAsync = context.async();

    try {
      MemoryPersistence persistence = new MemoryPersistence();
      MqttClient client = new MqttClient(String.format("tcp://%s:%d", MQTT_SERVER_HOST, MQTT_SERVER_PORT), "12345", persistence);
      client.connect();

      String[] topics = new String[]{MQTT_TOPIC};
      int[] qos = new int[]{0};
      client.subscribe(topics, qos);

      this.subscribeAsync.await();

      client.unsubscribe(topics);

      this.unsubscribeAsync.await();

      context.assertTrue(true);

    } catch (MqttException e) {

      context.assertTrue(false);
      e.printStackTrace();
    }
  }

  @Override
  protected void endpointHandler(MqttEndpoint endpoint, TestContext context) {

    endpoint.subscribeHandler(subscribe -> {

      List<MqttQoS> qos = new ArrayList<>();
      qos.add(subscribe.topicSubscriptions().get(0).qualityOfService());
      endpoint.subscribeAcknowledge(subscribe.messageId(), qos);

      this.subscribeAsync.complete();

    }).unsubscribeHandler(unsubscribe -> {

      endpoint.unsubscribeAcknowledge(unsubscribe.messageId());

      this.unsubscribeAsync.complete();
    });

    endpoint.accept(false);
  }
}
