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

import io.netty.handler.codec.DecoderException;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;
import org.junit.Test;

import static io.netty.handler.codec.mqtt.MqttQoS.AT_MOST_ONCE;
import static org.junit.Assert.assertTrue;

/**
 * MQTT client testing about the maximum message size
 */
public class MqttClientMaxMessageSizeIT extends MqttClientBaseIT {

  private static final Logger log = LoggerFactory.getLogger(MqttClientMaxMessageSizeIT.class);

  private static final String MQTT_TOPIC = "/my_topic";
  private static final int MQTT_MAX_MESSAGE_SIZE = 50;
  private static final int MQTT_BIG_MESSAGE_SIZE = MQTT_MAX_MESSAGE_SIZE + 1;


  @Test
  public void decoderMaxMessageSize(TestContext context) throws InterruptedException {
    Async async = context.async();
    MqttClient client = MqttClient.create(Vertx.vertx(),
      new MqttClientOptions()
        .setMaxMessageSize(MQTT_MAX_MESSAGE_SIZE)
    );

    client.subscribeCompletionHandler(sc -> {
      log.info("SUBACK <---");
      byte[] message = new byte[MQTT_BIG_MESSAGE_SIZE];
      client.publish(MQTT_TOPIC, Buffer.buffer(message), AT_MOST_ONCE, false, false);
      log.info("PUBLISH ---> ... with big message size which should cause decoder exception");
    });

    client.exceptionHandler(t->{
      log.error("Exception raised", t);

      if (t instanceof DecoderException) {
        log.info("PUBLISH <--- message with big size");
        async.countDown();
      }
    });

    log.info("CONNECT --->");
    client.connect(port, host).onComplete(context.asyncAssertSuccess(v -> {
      log.info("CONNACK <---");
      client.subscribe(MQTT_TOPIC, 0);
      log.info("SUBSCRIBE --->");
    }));

    async.await();
  }
}
