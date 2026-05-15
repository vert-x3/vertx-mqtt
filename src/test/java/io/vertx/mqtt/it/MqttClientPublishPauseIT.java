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
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.mqtt.MqttClient;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * MQTT client testing on publishing messages
 */
@RunWith(VertxUnitRunner.class)
public class MqttClientPublishPauseIT extends MqttClientBaseIT {

  private static final Logger log = LoggerFactory.getLogger(MqttClientPublishPauseIT.class);

  private static final String MQTT_TOPIC = "/my_topic";
  private static final String MQTT_MESSAGE = "Hello Vert.x MQTT Client";

  private int messageCount = 0;

  @Test
  public void testPauseResume(TestContext context) throws InterruptedException {

    Async async = context.async();
    Vertx vertx = Vertx.vertx();
    MqttClient client = MqttClient.create(vertx);

    client.publishHandler(mess -> {
      log.info("Received message id = " + mess.messageId());
      messageCount++;
    });

    client.connect(port, host).onComplete(context.asyncAssertSuccess(v -> {

      client.subscribe(MQTT_TOPIC, 0);

      log.info("Pause reading");
      client.pause();

      client.publish(MQTT_TOPIC,
          Buffer.buffer(MQTT_MESSAGE.getBytes()),
          MqttQoS.AT_LEAST_ONCE, false, false);

      vertx.setTimer(2_000, event -> {
        log.info("Resume reading");
        client.resume();
      });

    }));

    // wait for 5 seconds to receive the message after resume
    vertx.setTimer(1_000, event -> {
      log.info("Asserting not message received");
      // should receive no messages during the pause
      context.assertTrue(messageCount == 0);
    });

    // wait for 15 seconds to receive the message after resume
    vertx.setTimer(3_000, event -> {
      log.info("Asserting receiving message received during the pause");
      // should receive only one message after resume
      context.assertTrue(messageCount == 1);
      client.disconnect();
      async.complete();
    });

    async.await();

  }

}
