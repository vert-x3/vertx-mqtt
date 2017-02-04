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
package io.vertx.mqtt.test;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.mqtt.MqttEndpoint;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicInteger;

import static org.eclipse.paho.client.mqttv3.MqttConnectOptions.MQTT_VERSION_3_1;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@RunWith(VertxUnitRunner.class)
public class MqttInvalidClientIdentifierTest extends MqttBaseTest {

  @Before
  public void before(TestContext context) {
    this.setUp(context);
  }

  @After
  public void after(TestContext context) {
    this.tearDown(context);
  }

  private final AtomicInteger publishCount = new AtomicInteger();

  @Test
  public void testInvalidClientIdentifier() throws Exception {
    MemoryPersistence persistence = new MemoryPersistence();
    MqttClient client = new MqttClient(String.format("tcp://%s:%d", MQTT_SERVER_HOST, MQTT_SERVER_PORT), "a-very-long-identifier-that-is-invalid", persistence);
    MqttConnectOptions options = new MqttConnectOptions();
    options.setMqttVersion(MQTT_VERSION_3_1);
    options.setAutomaticReconnect(false);
    try {
      client.connect(options);
      for (int i = 0;i < 100;i++) {
        client.publish("the-message-" + i, new byte[0], MqttQoS.AT_LEAST_ONCE.value(), false);
        Thread.sleep(1);
      }
      fail();
    } catch (MqttException ignore) {
      assertEquals(0, publishCount.get());
      // OK
    }
  }

  @Override
  protected void endpointHandler(MqttEndpoint endpoint) {
    endpoint.publishAutoAck(true);
    endpoint.publishHandler(msg -> {
      publishCount.incrementAndGet();
    });
    endpoint.accept(false);
  }
}
