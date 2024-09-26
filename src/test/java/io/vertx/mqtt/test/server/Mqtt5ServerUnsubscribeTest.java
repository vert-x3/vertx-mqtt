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
import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.messages.codes.MqttSubAckReasonCode;
import io.vertx.mqtt.messages.codes.MqttUnsubAckReasonCode;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.packet.MqttReturnCode;
import org.eclipse.paho.mqttv5.common.packet.UserProperty;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * MQTT server testing about clients unsubscription
 */
@RunWith(VertxUnitRunner.class)
public class Mqtt5ServerUnsubscribeTest extends MqttServerBaseTest {

  private static final Logger log = LoggerFactory.getLogger(Mqtt5ServerUnsubscribeTest.class);

  private List<MqttUnsubAckReasonCode> expectedReasonCodes;

  private static final String MQTT_TOPIC = "/my_topic";

  private static final String MQTT_REASON_STRING = "because I've said so";

  private static final String USER_PROPERTY_KEY = "key";
  private static final String USER_PROPERTY_VALUE = "value";

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

    try {
      MemoryPersistence persistence = new MemoryPersistence();
      MqttAsyncClient client = new MqttAsyncClient(String.format("tcp://%s:%d", MQTT_SERVER_HOST, MQTT_SERVER_PORT), "12345", persistence);
      IMqttToken token = client.connect();
      token.waitForCompletion();

      expectedReasonCodes = Collections.singletonList(MqttUnsubAckReasonCode.SUCCESS);
      String[] topics = new String[]{MQTT_TOPIC};
      int[] qos = new int[]{0};
      token = client.subscribe(topics, qos);
      token.waitForCompletion();

      org.eclipse.paho.mqttv5.common.packet.MqttProperties mqttProperties = new org.eclipse.paho.mqttv5.common.packet.MqttProperties();
      List<UserProperty> userProperties = new ArrayList<>();
      userProperties.add(new UserProperty(USER_PROPERTY_KEY, USER_PROPERTY_VALUE));
      mqttProperties.setUserProperties(userProperties);
      token = client.unsubscribe(topics, null, null, mqttProperties);
      token.waitForCompletion();

      context.assertTrue(true);

    } catch (MqttException e) {

      context.fail(e);
    }
  }

  @Test
  public void unsubscribeFail(TestContext context) {

    try {
      MemoryPersistence persistence = new MemoryPersistence();
      MqttClient client = new MqttClient(String.format("tcp://%s:%d", MQTT_SERVER_HOST, MQTT_SERVER_PORT), "12345", persistence);
      client.connect();

      expectedReasonCodes = Collections.singletonList(MqttUnsubAckReasonCode.IMPLEMENTATION_SPECIFIC_ERROR);
      String[] topics = new String[]{MQTT_TOPIC};
      int[] qos = new int[]{0};
      client.subscribe(topics, qos);

      client.unsubscribe(topics);

    } catch (MqttException e) {
      context.assertEquals(MqttReturnCode.RETURN_CODE_IMPLEMENTATION_SPECIFIC_ERROR, e.getReasonCode());
      context.assertEquals(MQTT_REASON_STRING, e.getMessage());
    }
  }


  @Override
  protected void endpointHandler(MqttEndpoint endpoint, TestContext context) {

    endpoint.subscribeHandler(subscribe -> {

      List<MqttQoS> qos = new ArrayList<>();
      qos.add(subscribe.topicSubscriptions().get(0).qualityOfService());
      endpoint.subscribeAcknowledge(subscribe.messageId(), qos);

    }).unsubscribeHandler(unsubscribe -> {

      if(expectedReasonCodes.get(0) == MqttUnsubAckReasonCode.SUCCESS) {
        MqttProperties.UserProperties userProperties = (MqttProperties.UserProperties) unsubscribe.properties().getProperty(MqttProperties.MqttPropertyType.USER_PROPERTY.value());
        context.assertEquals(userProperties.value().get(0).key, USER_PROPERTY_KEY);
        context.assertEquals(userProperties.value().get(0).value, USER_PROPERTY_VALUE);
      }

      MqttProperties props = new MqttProperties();
      if(expectedReasonCodes.get(0) != MqttUnsubAckReasonCode.SUCCESS) {
        props.add(new MqttProperties.StringProperty(MqttProperties.MqttPropertyType.REASON_STRING.value(), MQTT_REASON_STRING));
      }
      endpoint.unsubscribeAcknowledge(unsubscribe.messageId(), expectedReasonCodes, props);

    });

    endpoint.accept(false);
  }
}
