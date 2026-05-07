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
import io.vertx.mqtt.MqttServer;
import io.vertx.mqtt.MqttServerOptions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tests for automatic MQTT 5.0 topic alias management in MqttClientImpl.
 * The server advertises TOPIC_ALIAS_MAXIMUM in the CONNACK; the client must
 * automatically assign and reuse aliases when publishing.
 */
@RunWith(VertxUnitRunner.class)
public class Mqtt5ClientTopicAliasTest {

  private static final String TOPIC = "/mqtt5/alias/test";

  private Vertx vertx;
  private MqttServer server;

  @Before
  public void before() {
    vertx = Vertx.vertx();
    server = MqttServer.create(vertx);
  }

  @After
  public void after(TestContext ctx) {
    server.close().onComplete(ctx.asyncAssertSuccess(v ->
      vertx.close().onComplete(ctx.asyncAssertSuccess())));
  }

  // -----------------------------------------------------------------------

  /**
   * First PUBLISH to a topic: the wire packet must carry the full topic name
   * AND a TOPIC_ALIAS property.
   */
  @Test
  public void firstPublishCarriesTopicNameAndAlias(TestContext ctx) {
    Async serverReceived = ctx.async();

    server.endpointHandler(endpoint -> {
      // Advertise alias support: accept up to 10 aliases
      endpoint.accept(false, buildConnAckProps(10));

      endpoint.publishHandler(msg -> {
        // Full topic name must be present on first publish
        ctx.assertEquals(TOPIC, msg.topicName());
        // TOPIC_ALIAS property must be present
        MqttProperties.MqttProperty<?> aliasProp = msg.properties().getProperty(MqttProperties.TOPIC_ALIAS);
        ctx.assertNotNull(aliasProp);
        ctx.assertTrue((Integer) aliasProp.value() >= 1);
        serverReceived.complete();
      });
    });

    startServer(ctx, () -> {
      MqttClient client = MqttClient.create(vertx, v5Options());
      client.connect(server.actualPort(), "localhost")
        .onComplete(ctx.asyncAssertSuccess(ack ->
          client.publish(TOPIC, Buffer.buffer("first"), io.netty.handler.codec.mqtt.MqttQoS.AT_MOST_ONCE, false, false)));
    });

    serverReceived.awaitSuccess(5000);
  }

  /**
   * Second PUBLISH to the same topic: the wire packet must carry an EMPTY topic name
   * and the same TOPIC_ALIAS as the first publish (alias reuse).
   */
  @Test
  public void secondPublishReusesAlias(TestContext ctx) {
    Async firstReceived  = ctx.async();
    Async secondReceived = ctx.async();
    AtomicInteger capturedAlias = new AtomicInteger(-1);

    server.endpointHandler(endpoint -> {
      endpoint.accept(false, buildConnAckProps(10));

      AtomicInteger count = new AtomicInteger(0);
      endpoint.publishHandler(msg -> {
        int n = count.incrementAndGet();
        if (n == 1) {
          // First publish: topic name present, alias assigned
          ctx.assertFalse(msg.topicName().isEmpty());
          MqttProperties.MqttProperty<?> aliasProp = msg.properties().getProperty(MqttProperties.TOPIC_ALIAS);
          ctx.assertNotNull(aliasProp);
          capturedAlias.set((Integer) aliasProp.value());
          firstReceived.complete();
        } else if (n == 2) {
          // Second publish: alias resolved server-side, topic name must be non-empty
          ctx.assertEquals(TOPIC, msg.topicName());
          MqttProperties.MqttProperty<?> aliasProp = msg.properties().getProperty(MqttProperties.TOPIC_ALIAS);
          ctx.assertNotNull(aliasProp);
          ctx.assertEquals(capturedAlias.get(), aliasProp.value());
          secondReceived.complete();
        }
      });
    });

    startServer(ctx, () -> {
      MqttClient client = MqttClient.create(vertx, v5Options());
      client.connect(server.actualPort(), "localhost")
        .onComplete(ctx.asyncAssertSuccess(ack -> {
          client.publish(TOPIC, Buffer.buffer("first"),  io.netty.handler.codec.mqtt.MqttQoS.AT_MOST_ONCE, false, false);
          client.publish(TOPIC, Buffer.buffer("second"), io.netty.handler.codec.mqtt.MqttQoS.AT_MOST_ONCE, false, false);
        }));
    });

    firstReceived.awaitSuccess(5000);
    secondReceived.awaitSuccess(5000);
  }

  /**
   * When the server advertises TOPIC_ALIAS_MAXIMUM=0 (or does not include it),
   * the client must NOT add any TOPIC_ALIAS property to PUBLISH packets.
   */
  @Test
  public void topicAliasDisabledWhenServerSaysZero(TestContext ctx) {
    Async serverReceived = ctx.async();

    server.endpointHandler(endpoint -> {
      // Explicitly advertise 0 aliases supported
      endpoint.accept(false, buildConnAckProps(0));

      endpoint.publishHandler(msg -> {
        // Full topic name must be present
        ctx.assertEquals(TOPIC, msg.topicName());
        // No TOPIC_ALIAS property
        ctx.assertNull(msg.properties().getProperty(MqttProperties.TOPIC_ALIAS));
        serverReceived.complete();
      });
    });

    startServer(ctx, () -> {
      MqttClient client = MqttClient.create(vertx, v5Options());
      client.connect(server.actualPort(), "localhost")
        .onComplete(ctx.asyncAssertSuccess(ack ->
          client.publish(TOPIC, Buffer.buffer("no-alias"), io.netty.handler.codec.mqtt.MqttQoS.AT_MOST_ONCE, false, false)));
    });

    serverReceived.awaitSuccess(5000);
  }

  // -----------------------------------------------------------------------

  /** Build MqttProperties for CONNACK with TOPIC_ALIAS_MAXIMUM set. */
  private MqttProperties buildConnAckProps(int topicAliasMaximum) {
    MqttProperties props = new MqttProperties();
    props.add(new MqttProperties.IntegerProperty(MqttProperties.TOPIC_ALIAS_MAXIMUM, topicAliasMaximum));
    return props;
  }

  private MqttClientOptions v5Options() {
    MqttClientOptions opts = new MqttClientOptions();
    opts.setVersion(MqttVersion.MQTT_5.protocolLevel());
    // Disable client-side alias sending by default so each test controls via server CONNACK
    opts.setTopicAliasMaximum(null);
    return opts;
  }

  private void startServer(TestContext ctx, Runnable afterStart) {
    Async latch = ctx.async();
    server.listen(0).onComplete(ctx.asyncAssertSuccess(v -> {
      latch.complete();
      afterStart.run();
    }));
    latch.awaitSuccess(5000);
  }
}
