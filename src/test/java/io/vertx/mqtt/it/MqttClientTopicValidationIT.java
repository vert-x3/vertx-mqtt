/*
 * Copyright 2017 Red Hat Inc.
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
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.mqtt.MqttClient;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

@RunWith(VertxUnitRunner.class)
public class MqttClientTopicValidationIT {

  private static final Logger log = LoggerFactory.getLogger(MqttClientTopicValidationIT.class);
  private static final String MQTT_MESSAGE = "Hello Vert.x MQTT Client";
  private static final int MAX_TOPIC_LEN = 65535;

  private static final String goodTopic;
  private static final String badTopic;

  static {
    char[] topic = new char[MAX_TOPIC_LEN + 1];
    Arrays.fill(topic, 'h');
    badTopic = new String(topic);
    goodTopic = new String(topic, 0, MAX_TOPIC_LEN);
  }


  @Test
  public void topicNameValidation(TestContext context) {
    testPublish("/", true, context);
    testPublish("/hello", true, context);
    testPublish("sport/tennis/player1", true, context);
    testPublish("sport/tennis/player1#", false, context);
    testPublish("sport/tennis/+/player1#", false, context);
    testPublish("#", false, context);
    testPublish("+", false, context);
    testPublish("", false, context);
    testPublish(goodTopic, true, context);
    testPublish(badTopic, false, context);
  }

  @Test
  public void topicFilterValidation(TestContext context) {
    testSubscribe("#", true, context);
    testSubscribe("+", true, context);
    testSubscribe("+/tennis/#", true, context);
    testSubscribe("sport/+/player1", true, context);
    testSubscribe("+/+", true, context);
    testSubscribe("sport+", false, context);
    testSubscribe("sp#ort", false, context);
    testSubscribe("+/tennis#", false, context);
    testSubscribe(goodTopic, true, context);
    testSubscribe(badTopic, false, context);
  }

  /**
   * Execute a test of topic validation on public
   *
   * @param topicName topic name
   * @param mustBeValid if it should be valid or not
   * @param context
   */
  public void testPublish(String topicName, boolean mustBeValid, TestContext context) {

    log.info(String.format("test publishing in \"%s\" topic", topicName));
    Async async = context.async(2);
    MqttClient client = MqttClient.create(Vertx.vertx());

    client.connect(TestUtil.BROKER_PORT, TestUtil.BROKER_ADDRESS, c -> {
      Assert.assertTrue(c.succeeded());

      client.publish(
        topicName,
        Buffer.buffer(MQTT_MESSAGE.getBytes()),
        MqttQoS.AT_MOST_ONCE,
        false,
        false,
        ar1 -> {
          assertThat(ar1.succeeded(), is(mustBeValid));
          log.info("publishing message id = " + ar1.result());
          async.countDown();
          client
            .disconnect(ar -> {
              Assert.assertTrue(ar.succeeded());
              async.countDown();
            });
        });
    });

    async.await();
  }

  public void testSubscribe(String topicFilter, boolean mustBeValid, TestContext context) {

    log.info(String.format("test subscribing for \"%s\" topic", topicFilter));

    Async async = context.async(2);
    MqttClient client = MqttClient.create(Vertx.vertx());

    client.connect(TestUtil.BROKER_PORT, TestUtil.BROKER_ADDRESS, c -> {
      Assert.assertTrue(c.succeeded());

      client.subscribe(
        topicFilter,
        0,
        ar -> {
          assertThat(ar.succeeded(), is(mustBeValid));
          log.info("subscribe message id = " + ar.result());
          async.countDown();
          client
            .disconnect(ar1 -> {
              Assert.assertTrue(ar1.succeeded());
              async.countDown();
            });
        });
    });

    async.await();
  }
}
