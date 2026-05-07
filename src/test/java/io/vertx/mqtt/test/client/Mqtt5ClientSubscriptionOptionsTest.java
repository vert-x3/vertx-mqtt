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
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttSubscriptionOption;
import io.netty.handler.codec.mqtt.MqttSubscriptionOption.RetainedHandlingPolicy;
import io.netty.handler.codec.mqtt.MqttTopicSubscription;
import io.netty.handler.codec.mqtt.MqttVersion;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;
import io.vertx.mqtt.MqttServer;
import io.vertx.mqtt.messages.codes.MqttSubAckReasonCode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/**
 * Tests for MQTT 5.0 subscription options (No Local, Retain As Published,
 * Retain Handling) via the new
 * {@link MqttClient#subscribe(List, MqttProperties)} API.
 */
@RunWith(VertxUnitRunner.class)
public class Mqtt5ClientSubscriptionOptionsTest {

  private static final String TOPIC = "/mqtt5/sub/options";

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
   * Subscribe with NO_LOCAL=true: the server sees noLocal flag in subscription options.
   */
  @Test
  public void subscribeWithNoLocal(TestContext ctx) {
    Async serverLatch = ctx.async();

    server.endpointHandler(endpoint -> {
      endpoint.subscribeHandler(subscribe -> {
        ctx.assertFalse(subscribe.topicSubscriptions().isEmpty());
        MqttSubscriptionOption opt = subscribe.topicSubscriptions().get(0).subscriptionOption();
        ctx.assertTrue(opt.isNoLocal());
        endpoint.subscribeAcknowledge(subscribe.messageId(),
          List.of(MqttSubAckReasonCode.GRANTED_QOS0), MqttProperties.NO_PROPERTIES);
        serverLatch.complete();
      });
      endpoint.accept(false);
    });

    startServer(ctx, () -> {
      MqttClient client = MqttClient.create(vertx, v5Options());
      client.connect(server.actualPort(), "localhost")
        .onComplete(ctx.asyncAssertSuccess(ack -> {
          MqttSubscriptionOption option = new MqttSubscriptionOption(
            MqttQoS.AT_MOST_ONCE, true, false, RetainedHandlingPolicy.SEND_AT_SUBSCRIBE);
          client.subscribe(
            List.of(new MqttTopicSubscription(TOPIC, option)),
            MqttProperties.NO_PROPERTIES);
        }));
    });

    serverLatch.awaitSuccess(5000);
  }

  /**
   * Subscribe with RETAIN_AS_PUBLISHED=true: the server sees retainAsPublished in options.
   */
  @Test
  public void subscribeWithRetainAsPublished(TestContext ctx) {
    Async serverLatch = ctx.async();

    server.endpointHandler(endpoint -> {
      endpoint.subscribeHandler(subscribe -> {
        MqttSubscriptionOption opt = subscribe.topicSubscriptions().get(0).subscriptionOption();
        ctx.assertTrue(opt.isRetainAsPublished());
        endpoint.subscribeAcknowledge(subscribe.messageId(),
          List.of(MqttSubAckReasonCode.GRANTED_QOS1), MqttProperties.NO_PROPERTIES);
        serverLatch.complete();
      });
      endpoint.accept(false);
    });

    startServer(ctx, () -> {
      MqttClient client = MqttClient.create(vertx, v5Options());
      client.connect(server.actualPort(), "localhost")
        .onComplete(ctx.asyncAssertSuccess(ack -> {
          MqttSubscriptionOption option = new MqttSubscriptionOption(
            MqttQoS.AT_LEAST_ONCE, false, true, RetainedHandlingPolicy.SEND_AT_SUBSCRIBE);
          client.subscribe(
            List.of(new MqttTopicSubscription(TOPIC, option)),
            MqttProperties.NO_PROPERTIES);
        }));
    });

    serverLatch.awaitSuccess(5000);
  }

  /**
   * Subscribe with each RetainedHandlingPolicy value: server verifies the correct policy.
   */
  @Test
  public void subscribeWithRetainHandlingDontSend(TestContext ctx) {
    Async serverLatch = ctx.async();

    server.endpointHandler(endpoint -> {
      endpoint.subscribeHandler(subscribe -> {
        MqttSubscriptionOption opt = subscribe.topicSubscriptions().get(0).subscriptionOption();
        ctx.assertEquals(RetainedHandlingPolicy.DONT_SEND_AT_SUBSCRIBE, opt.retainHandling());
        endpoint.subscribeAcknowledge(subscribe.messageId(),
          List.of(MqttSubAckReasonCode.GRANTED_QOS2), MqttProperties.NO_PROPERTIES);
        serverLatch.complete();
      });
      endpoint.accept(false);
    });

    startServer(ctx, () -> {
      MqttClient client = MqttClient.create(vertx, v5Options());
      client.connect(server.actualPort(), "localhost")
        .onComplete(ctx.asyncAssertSuccess(ack -> {
          MqttSubscriptionOption option = new MqttSubscriptionOption(
            MqttQoS.EXACTLY_ONCE, false, false, RetainedHandlingPolicy.DONT_SEND_AT_SUBSCRIBE);
          client.subscribe(
            List.of(new MqttTopicSubscription(TOPIC, option)),
            MqttProperties.NO_PROPERTIES);
        }));
    });

    serverLatch.awaitSuccess(5000);
  }

  /**
   * All three subscription options active at once.
   */
  @Test
  public void subscribeWithAllOptions(TestContext ctx) {
    Async serverLatch = ctx.async();

    server.endpointHandler(endpoint -> {
      endpoint.subscribeHandler(subscribe -> {
        MqttSubscriptionOption opt = subscribe.topicSubscriptions().get(0).subscriptionOption();
        ctx.assertTrue(opt.isNoLocal());
        ctx.assertTrue(opt.isRetainAsPublished());
        ctx.assertEquals(RetainedHandlingPolicy.SEND_AT_SUBSCRIBE_IF_NOT_YET_EXISTS, opt.retainHandling());
        ctx.assertEquals(MqttQoS.AT_LEAST_ONCE, opt.qos());
        endpoint.subscribeAcknowledge(subscribe.messageId(),
          List.of(MqttSubAckReasonCode.GRANTED_QOS1), MqttProperties.NO_PROPERTIES);
        serverLatch.complete();
      });
      endpoint.accept(false);
    });

    startServer(ctx, () -> {
      MqttClient client = MqttClient.create(vertx, v5Options());
      client.connect(server.actualPort(), "localhost")
        .onComplete(ctx.asyncAssertSuccess(ack -> {
          MqttSubscriptionOption option = new MqttSubscriptionOption(
            MqttQoS.AT_LEAST_ONCE, true, true, RetainedHandlingPolicy.SEND_AT_SUBSCRIBE_IF_NOT_YET_EXISTS);
          client.subscribe(
            List.of(new MqttTopicSubscription(TOPIC, option)),
            MqttProperties.NO_PROPERTIES);
        }));
    });

    serverLatch.awaitSuccess(5000);
  }

  // -----------------------------------------------------------------------

  private MqttClientOptions v5Options() {
    MqttClientOptions opts = new MqttClientOptions();
    opts.setVersion(MqttVersion.MQTT_5.protocolLevel());
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
