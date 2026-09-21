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

import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttVersion;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;
import io.vertx.mqtt.MqttClientWillOptions;
import io.vertx.mqtt.MqttServer;
import io.vertx.mqtt.MqttWill;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;

/**
 * Tests for MQTT 5.0 Will message properties (§3.1.3.2).
 * Verifies that all 6 will properties are correctly encoded by MqttClientImpl
 * and arrive at the server endpoint.
 */
@RunWith(VertxUnitRunner.class)
public class Mqtt5ClientWillTest {

  private Vertx vertx;
  private MqttServer server;

  @Before
  public void before() {
    vertx = Vertx.vertx();
    server = MqttServer.create(vertx);
  }

  @After
  public void after(TestContext ctx) {
    server.close().onComplete(ctx.asyncAssertSuccess(v -> vertx.close().onComplete(ctx.asyncAssertSuccess())));
  }

  // -----------------------------------------------------------------------

  /**
   * All 6 MQTT 5.0 will properties must survive the CONNECT encoding and be
   * visible on the server side via {@code endpoint.will().getWillProperties()}.
   */
  @Test
  public void willMqtt5PropertiesAreSentCorrectly(TestContext ctx) {
    Async async = ctx.async();

    final String expectedTopic = "will/topic";
    final Buffer expectedPayload = Buffer.buffer("goodbye");
    final int expectedQos = 1;
    final long expectedWillDelay = 30L;
    final int expectedFormatIndicator = 1; // UTF-8
    final String expectedContentType = "text/plain";
    final String expectedResponseTopic = "response/topic";
    final Buffer expectedCorrelation = Buffer.buffer("corr-id-42");
    final String expectedUserKey = "reason";
    final String expectedUserVal = "shutdown";

    server.endpointHandler(endpoint -> {
      MqttWill will = endpoint.will();

      ctx.assertTrue(will.isWillFlag(), "willFlag must be true");
      ctx.assertEquals(expectedTopic, will.getWillTopic());
      ctx.assertEquals(expectedPayload, will.getWillMessage());
      ctx.assertEquals(expectedQos, will.getWillQos());

      MqttProperties props = will.getWillProperties();
      ctx.assertNotNull(props, "will properties must not be null");

      // WILL_DELAY_INTERVAL
      MqttProperties.MqttProperty<?> willDelay =
        props.getProperty(MqttProperties.MqttPropertyType.WILL_DELAY_INTERVAL.value());
      ctx.assertNotNull(willDelay, "WILL_DELAY_INTERVAL must be present");
      ctx.assertEquals((int) expectedWillDelay, willDelay.value());

      // PAYLOAD_FORMAT_INDICATOR
      MqttProperties.MqttProperty<?> formatIndicator =
        props.getProperty(MqttProperties.MqttPropertyType.PAYLOAD_FORMAT_INDICATOR.value());
      ctx.assertNotNull(formatIndicator, "PAYLOAD_FORMAT_INDICATOR must be present");
      ctx.assertEquals(expectedFormatIndicator, formatIndicator.value());

      // CONTENT_TYPE
      MqttProperties.MqttProperty<?> contentType =
        props.getProperty(MqttProperties.MqttPropertyType.CONTENT_TYPE.value());
      ctx.assertNotNull(contentType, "CONTENT_TYPE must be present");
      ctx.assertEquals(expectedContentType, contentType.value());

      // RESPONSE_TOPIC
      MqttProperties.MqttProperty<?> responseTopic =
        props.getProperty(MqttProperties.MqttPropertyType.RESPONSE_TOPIC.value());
      ctx.assertNotNull(responseTopic, "RESPONSE_TOPIC must be present");
      ctx.assertEquals(expectedResponseTopic, responseTopic.value());

      // CORRELATION_DATA
      MqttProperties.MqttProperty<?> correlationData =
        props.getProperty(MqttProperties.MqttPropertyType.CORRELATION_DATA.value());
      ctx.assertNotNull(correlationData, "CORRELATION_DATA must be present");
      ctx.assertTrue(Arrays.equals(expectedCorrelation.getBytes(), (byte[]) correlationData.value()),
        "CORRELATION_DATA bytes must match");

      // USER_PROPERTY
      MqttProperties.MqttProperty<?> userProp =
        props.getProperty(MqttProperties.MqttPropertyType.USER_PROPERTY.value());
      ctx.assertNotNull(userProp, "USER_PROPERTY must be present");
      @SuppressWarnings("unchecked")
      List<MqttProperties.StringPair> pairs = (List<MqttProperties.StringPair>) userProp.value();
      ctx.assertFalse(pairs.isEmpty(), "USER_PROPERTY list must not be empty");
      ctx.assertEquals(expectedUserKey, pairs.get(0).key);
      ctx.assertEquals(expectedUserVal, pairs.get(0).value);

      endpoint.accept(false);
      async.complete();
    });

    startServer(ctx, () -> {
      MqttClientWillOptions willOpts = new MqttClientWillOptions()
        .setTopic(expectedTopic)
        .setMessageBytes(expectedPayload)
        .setQos(expectedQos)
        .setWillDelayInterval(expectedWillDelay)
        .setPayloadFormatIndicator(expectedFormatIndicator)
        .setContentType(expectedContentType)
        .setResponseTopic(expectedResponseTopic)
        .setCorrelationData(expectedCorrelation)
        .addUserProperty(expectedUserKey, expectedUserVal);

      MqttClientOptions options = new MqttClientOptions();
      options.setVersion(MqttVersion.MQTT_5.protocolLevel());
      options.setWillOptions(willOpts);

      MqttClient client = MqttClient.create(vertx, options);
      client.connect(server.actualPort(), "localhost")
        .onComplete(ctx.asyncAssertSuccess());
    });

    async.awaitSuccess(5000);
  }

  // -----------------------------------------------------------------------

  private void startServer(TestContext ctx, Runnable afterStart) {
    Async latch = ctx.async();
    server.listen(0).onComplete(ctx.asyncAssertSuccess(v -> {
      latch.complete();
      afterStart.run();
    }));
    latch.awaitSuccess(5000);
  }
}
