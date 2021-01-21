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

import io.netty.handler.codec.DecoderException;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.Repeat;
import io.vertx.ext.unit.junit.RepeatRule;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.MqttServerOptions;
import io.vertx.mqtt.it.CustomPahoLogger;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MQTT server testing about the maximum message size
 */
@RunWith(VertxUnitRunner.class)
public class MqttServerWebSocketMaxMessageSizeTest extends MqttServerBaseTest {

  private static final Logger log = LoggerFactory.getLogger(MqttServerWebSocketMaxMessageSizeTest.class);

  private Async async;

  private static final String MQTT_TOPIC = "/my_topic";
  private static final int MQTT_MAX_MESSAGE_SIZE = 50;
  private static final int MQTT_BIG_MESSAGE_SIZE = 50 + 1;

  @Before
  public void before(TestContext context) {

    MqttServerOptions options = new MqttServerOptions();
    options.setMaxMessageSize(MQTT_MAX_MESSAGE_SIZE);
    options.setUseWebSocket(true);

    this.setUp(context, options);
  }

  @Rule
  public RepeatRule rule = new RepeatRule();

  @Repeat(100)
  @Test
  public void publishBigMessage(TestContext context) {

    this.async = context.async();

    org.eclipse.paho.client.mqttv3.logging.LoggerFactory.setLogger(CustomPahoLogger.class.getName());

    AtomicBoolean done = new AtomicBoolean();
    try {

      MemoryPersistence persistence = new MemoryPersistence();
      MqttClient client = new MqttClient(String.format("ws://%s:%d", MQTT_SERVER_HOST, MQTT_SERVER_PORT), "12345", persistence);
      client.connect();

      byte[] message = new byte[MQTT_BIG_MESSAGE_SIZE];

      System.out.println("PUBLISHING");
      new Thread(() -> {
        if (!done.get()) {
          // Print dump
          Set<Thread> threads = Thread.getAllStackTraces().keySet();
          threads.forEach(thread -> {
            Exception e = new Exception();
            e.setStackTrace(thread.getStackTrace());
            System.out.println("<DUMP thread='" + thread.getName() +  "'>");
            e.printStackTrace(System.out);
            System.out.println("</DUMP>");
          });
        }
      }).start();
      client.setTimeToWait(30_000);
      client.publish(MQTT_TOPIC, message, 0, false);
      System.out.println("PUBLISHED");
    } catch (MqttException e) {
      context.fail(e);
    } finally {
      done.set(true);
    }
    System.out.println("EXIT");
  }

  @After
  public void after(TestContext context) {
    System.out.println("AFTER TEST");
    this.tearDown(context);
  }

  @Override
  protected void endpointHandler(MqttEndpoint endpoint, TestContext context) {

    System.out.println("START");
    endpoint.exceptionHandler(t -> {
      System.out.println("GOT ERR " + t.getClass());
      // log.error("Exception raised", t);

      if (t instanceof DecoderException) {
        this.async.complete();
        System.out.println("COMPLETE");
      }

    });

    endpoint.accept(false);
  }
}