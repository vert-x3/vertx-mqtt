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

import java.nio.charset.Charset;

import static org.junit.Assert.assertTrue;

/**
 * MQTT client testing on subscribing topics
 */
@RunWith(VertxUnitRunner.class)
public class MqttClientSubscribeIT {

  private static final Logger log = LoggerFactory.getLogger(MqttClientSubscribeIT.class);

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

  @Test
  public void unsubscribedNoMessageReceived(TestContext context) throws InterruptedException {

    Async publish = context.async(2);
    Async async = context.async();

    MqttClient subscriber1 = MqttClient.create(Vertx.vertx());
    MqttClient subscriber2 = MqttClient.create(Vertx.vertx());
    MqttClient publisher = MqttClient.create(Vertx.vertx());

    // subscriber1 connects, subscribe and then un-unsubscribe, it won't get the published message
    subscriber1.connect(TestUtil.BROKER_PORT, TestUtil.BROKER_ADDRESS, ar -> {

      assertTrue(ar.succeeded());

      subscriber1.publishHandler(message -> {
        log.error("Subscriber " + subscriber1.clientId() + " received message " + new String(message.payload().getBytes()));
        context.fail();
      });

      subscriber1.subscribe(MQTT_TOPIC, MqttQoS.AT_MOST_ONCE.value(), ar1 -> {

        assertTrue(ar1.succeeded());
        log.info("Subscriber " + subscriber1.clientId() + " subscribed to " + MQTT_TOPIC);

        subscriber1.unsubscribe(MQTT_TOPIC, ar2 -> {

          assertTrue(ar2.succeeded());
          log.info("Subscriber " + subscriber1.clientId() + " un-subscribed from " + MQTT_TOPIC);
          publish.countDown();
        });

      });

    });

    // subscriber2 connects and subscribe, it will get the published message
    subscriber2.connect(TestUtil.BROKER_PORT, TestUtil.BROKER_ADDRESS, ar -> {

      assertTrue(ar.succeeded());

      subscriber2.publishHandler(message -> {
        log.error("Subscriber " + subscriber2.clientId() + " received message " + new String(message.payload().getBytes()));
        async.complete();
      });

      subscriber2.subscribe(MQTT_TOPIC, MqttQoS.AT_MOST_ONCE.value(), ar1 -> {

        assertTrue(ar1.succeeded());
        log.info("Subscriber " + subscriber2.clientId() + " subscribed to " + MQTT_TOPIC);
        publish.countDown();
      });

    });

    // waiting for subscribers to subscribe and then the first client to un-subscribe, before publishing a message
    publish.await();

    publisher.connect(TestUtil.BROKER_PORT, TestUtil.BROKER_ADDRESS, ar -> {

      publisher.publish(
        MQTT_TOPIC,
        Buffer.buffer(MQTT_MESSAGE.getBytes()),
        MqttQoS.AT_MOST_ONCE,
        false,
        false,
        ar1 -> {
          assertTrue(ar.succeeded());
          messageId = ar1.result();
          log.info("Publishing message id = " + messageId);
        }
      );

    });

    async.await();
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

    client.connect(TestUtil.BROKER_PORT, TestUtil.BROKER_ADDRESS, ar -> {
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

    client.connect(TestUtil.BROKER_PORT, TestUtil.BROKER_ADDRESS, ar -> {
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
