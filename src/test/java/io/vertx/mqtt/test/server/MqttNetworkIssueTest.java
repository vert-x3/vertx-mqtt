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

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.mqtt.MqttEndpoint;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * MQTT server testing about network issues
 */
@RunWith(VertxUnitRunner.class)
public class MqttNetworkIssueTest extends MqttBaseTest {

  private static final Logger log = LoggerFactory.getLogger(MqttNetworkIssueTest.class);

  private Proxy proxy;

  private long started;
  private long ended;

  @Before
  public void before(TestContext context) {

    this.setUp(context);

    this.proxy = new Proxy(this.vertx, MQTT_SERVER_HOST, MQTT_SERVER_PORT);
    this.proxy.start(context.asyncAssertSuccess());
  }

  @After
  public void after(TestContext context) {

    this.tearDown(context);
    this.proxy.stop(context.asyncAssertSuccess());
  }

  @Test
  public void keepAliveTimeout(TestContext context) {

    Async async = context.async();
    int keepAliveInterval = 5;
    // MQTT spec : server will wait half more then the keepAliveInterval
    int timeout = keepAliveInterval + keepAliveInterval / 2;

    try {
      MemoryPersistence persistence = new MemoryPersistence();
      MqttClient client = new MqttClient(String.format("tcp://%s:%d", Proxy.SERVER_HOST, Proxy.SERVER_PORT), "12345", persistence);

      MqttConnectOptions options = new MqttConnectOptions();
      options.setAutomaticReconnect(false);
      options.setKeepAliveInterval(keepAliveInterval);

      this.started = System.currentTimeMillis();

      client.setCallback(new MqttCallback() {

        @Override
        public void connectionLost(Throwable throwable) {

          ended = System.currentTimeMillis();
          log.info("Elapsed : " + (ended - started));
          async.complete();
        }

        @Override
        public void messageArrived(String s, MqttMessage mqttMessage) throws Exception {

        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {

        }
      });

      client.connect(options);

      vertx.setTimer(1000, t -> {
        proxy.pause();
      });

      async.await();

      long elapsed = ended - started;
      // consider a range of 500 ms
      context.assertTrue(elapsed > (timeout * 1000 - 500) && elapsed < (timeout * 1000 + 500));

    } catch (MqttException e) {
      context.fail(e);
    }

  }

  @Override
  protected void endpointHandler(MqttEndpoint endpoint) {

    endpoint.closeHandler(v -> {

      log.info("MQTT remote client connection closed");
    });

    endpoint.accept(false);
  }
}
