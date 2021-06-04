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

import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.MqttServerOptions;
import io.vertx.mqtt.messages.codes.MqttDisconnectReasonCode;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.packet.MqttReturnCode;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * MQTT server testing about the maximum message size
 */
@RunWith(VertxUnitRunner.class)
public class Mqtt5ServerMaxMessageSizeTest extends MqttServerBaseTest {

  private static final Logger log = LoggerFactory.getLogger(Mqtt5ServerMaxMessageSizeTest.class);

  private static final String MQTT_TOPIC = "/my_topic";
  private static final int MQTT_MAX_MESSAGE_SIZE = 50;
  private static final int MQTT_BIG_MESSAGE_SIZE = MQTT_MAX_MESSAGE_SIZE + 1;

  @Before
  public void before(TestContext context) {

    MqttServerOptions options = new MqttServerOptions();
    options.setMaxMessageSize(MQTT_MAX_MESSAGE_SIZE);

    this.setUp(context, options);
  }

  //Temporarily disabled until https://github.com/vert-x3/vertx-mqtt/issues/202 gets resolved
  @Ignore
  @Test
  public void publishBigMessage(TestContext context) {
    Mqtt5ProbeCallback probeCallback = new Mqtt5ProbeCallback(context);
    try {
      MemoryPersistence persistence = new MemoryPersistence();
      MqttClient client = new MqttClient(String.format("tcp://%s:%d", MQTT_SERVER_HOST, MQTT_SERVER_PORT), "12345", persistence);
      client.setCallback(probeCallback);
      client.connect();

      byte[] message = new byte[MQTT_BIG_MESSAGE_SIZE];

      // The client seems to fail when sending IO and block forever (see MqttOutputStream)
      // that makes the test hang forever
      client.setTimeToWait(1000);
      client.publish(MQTT_TOPIC, message, 0, false);
    } catch (MqttException e) {
      log.info("MQTT client failure", e);
    }
    context.assertEquals((int) MqttReturnCode.RETURN_CODE_PACKET_TOO_LARGE, probeCallback.getDisconnectResponse().getReturnCode());
  }

  @After
  public void after(TestContext context) {

    this.tearDown(context);
  }

  @Override
  protected void endpointHandler(MqttEndpoint endpoint, TestContext context) {

    endpoint.exceptionHandler(t -> {
      if (t instanceof TooLongFrameException) {
        endpoint.disconnect(MqttDisconnectReasonCode.PACKET_TOO_LARGE, MqttProperties.NO_PROPERTIES);
      }
    });

    endpoint.accept(false);
  }
}
