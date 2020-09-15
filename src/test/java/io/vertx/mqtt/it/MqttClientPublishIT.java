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

package io.vertx.mqtt.it;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.mqtt.MqttClient;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertTrue;

/**
 * MQTT client testing on publishing messages
 */
@RunWith(VertxUnitRunner.class)
public class MqttClientPublishIT {

  private static final Logger log = LoggerFactory.getLogger(MqttClientPublishIT.class);

  private static final String MQTT_TOPIC = "/my_topic";
  private static final String MQTT_MESSAGE = "Hello Vert.x MQTT Client";

  private int messageId = 0;

  @Test
  public void publishQoS2(TestContext context) throws InterruptedException {
    this.publish(context, MqttQoS.EXACTLY_ONCE);
  }

  @Test
  public void publishQoS1(TestContext context) throws InterruptedException {
    this.publish(context, MqttQoS.AT_LEAST_ONCE);
  }

  private void publish(TestContext context, MqttQoS qos) {

    this.messageId = 0;

    Async async = context.async();
    MqttClient client = MqttClient.create(Vertx.vertx());

    client.publishCompletionHandler(pubid -> {
      assertTrue(pubid == messageId);
      log.info("publishing complete for message id = " + pubid);
      client.disconnect();
      async.countDown();
    });

    client.connect(TestUtil.BROKER_PORT, TestUtil.BROKER_ADDRESS, ar -> {

      assertTrue(ar.succeeded());

      client.publish(
        MQTT_TOPIC,
        Buffer.buffer(MQTT_MESSAGE.getBytes()),
        qos,
        false,
        false,
        ar1 -> {
          assertTrue(ar.succeeded());
          messageId = ar1.result();
          log.info("publishing message id = " + messageId);
        }
      );
    });

    async.await();
  }
}
