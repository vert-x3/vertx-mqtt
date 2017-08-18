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

package io.vertx.mqtt.test.client;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.charset.Charset;

import static org.junit.Assert.assertTrue;

/**
 * MQTT client testing on subscribing topics
 */
@RunWith(VertxUnitRunner.class)
public class MqttClientSubscribeTest {

  private static final Logger log = LoggerFactory.getLogger(MqttClientSubscribeTest.class);

  private static final String MQTT_TOPIC = "/my_topic";
  private static final String MQTT_MESSAGE = "Hello Vert.x MQTT Client";

  private int messageId = 0;

  @Test
  public void subscribeQos2AndReceive(TestContext context) throws InterruptedException {
    this.subscribeAndReceive(context, MqttQoS.EXACTLY_ONCE);
  }

  @Test
  public void subscribeQos1AndReceive(TestContext context) throws InterruptedException {
    this.subscribeAndReceive(context, MqttQoS.AT_LEAST_ONCE);
  }

  @Test
  public void subscribeQoS0AndReceive(TestContext context) throws InterruptedException {
    this.subscribeAndReceive(context, MqttQoS.AT_MOST_ONCE);
  }

  @Test
  public void subscribeQoS0(TestContext context) throws InterruptedException {
    this.subscribe(context, MqttQoS.AT_MOST_ONCE);
  }

  @Test
  public void subscribeQoS1(TestContext context) throws InterruptedException {
    this.subscribe(context, MqttQoS.AT_LEAST_ONCE);
  }

  @Test
  public void subscribeQoS2(TestContext context) throws InterruptedException {
    this.subscribe(context, MqttQoS.EXACTLY_ONCE);
  }

  private void subscribeAndReceive(TestContext context, MqttQoS qos) {

    Async async = context.async();
    MqttClient client = MqttClient.create(Vertx.vertx());

    client.publishHandler(publish -> {
        assertTrue(publish.qosLevel() == qos);
        log.info("Just received message on [" + publish.topicName() + "] payload [" + publish.payload().toString(Charset.defaultCharset()) + "] with QoS [" + publish.qosLevel() + "]");
        client.disconnect();
        async.countDown();
      });

    client.connect(MqttClientOptions.DEFAULT_PORT, TestUtil.BROKER_ADDRESS, ar -> {
      assertTrue(ar.succeeded());
      client.subscribe(MQTT_TOPIC, qos.value());
      client.publish(
        MQTT_TOPIC,
        Buffer.buffer(MQTT_MESSAGE.getBytes()),
        qos,
        false,
        false
      );

    });

    async.await();
  }

  private void subscribe(TestContext context, MqttQoS qos) {

    this.messageId = 0;

    Async async = context.async();
    MqttClient client = MqttClient.create(Vertx.vertx());

    client.subscribeCompletionHandler(suback -> {
      assertTrue(suback.messageId() == messageId);
      assertTrue(suback.grantedQoSLevels().contains(qos.value()));
      log.info("subscribing complete for message id = " + suback.messageId() + " with QoS " + suback.grantedQoSLevels());
      client.disconnect();
      async.countDown();
    });

    client.connect(MqttClientOptions.DEFAULT_PORT, TestUtil.BROKER_ADDRESS, ar -> {
      assertTrue(ar.succeeded());

      client.subscribe(MQTT_TOPIC, qos.value(), done -> {
        assertTrue(done.succeeded());
        messageId = done.result();
        log.info("subscribing on [" + MQTT_TOPIC + "] with QoS [" + qos.value() + "] message id = " + messageId);
      });
    });

    async.await();
  }
}
