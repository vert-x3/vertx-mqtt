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
import io.netty.handler.codec.mqtt.MqttVersion;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;
import io.vertx.mqtt.MqttServer;
import io.vertx.mqtt.messages.MqttSubAckMessage;
import io.vertx.mqtt.MqttException;
import io.vertx.mqtt.messages.codes.MqttSubAckReasonCode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.Map;

/**
 * Tests for the MQTT v5 client SUBSCRIBE and SUBACK flow.
 * Verifies that:
 * <ul>
 * <li>SUBSCRIBE with MQTT properties is sent correctly and properties are received server-side.</li>
 * <li>SUBACK reason codes (from the server) are surfaced in the client's subscribeCompletionHandler.</li>
 * </ul>
 */
@RunWith(VertxUnitRunner.class)
public class Mqtt5ClientSubscribeTest {

  private static final String MQTT_TOPIC = "/mqtt5/test";
  private static final int SUBSCRIPTION_IDENTIFIER = 42;

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
   * SUBSCRIBE carrying a SUBSCRIPTION_IDENTIFIER must arrive on the server
   * with the identifier intact.
   */
  @Test
  public void subscribeWithSubscriptionIdentifier(TestContext ctx) {
    Async serverReceived = ctx.async();

    server.endpointHandler(endpoint -> {
      endpoint.subscribeHandler(subscribe -> {
        MqttProperties.MqttProperty<?> prop = subscribe.properties().getProperty(MqttProperties.SUBSCRIPTION_IDENTIFIER);
        ctx.assertNotNull(prop, "SUBSCRIPTION_IDENTIFIER property must be present");
        ctx.assertEquals(SUBSCRIPTION_IDENTIFIER, prop.value());

        // Acknowledge — grant the requested QoS
        endpoint.subscribeAcknowledge(subscribe.messageId(),
            List.of(MqttSubAckReasonCode.qosGranted(subscribe.topicSubscriptions().get(0).qualityOfService())),
            MqttProperties.NO_PROPERTIES);

        serverReceived.complete();
      });
      endpoint.accept(false);
    });

    startServer(ctx, () -> {
      MqttClientOptions options = v5Options();
      MqttClient client = MqttClient.create(vertx, options);

      client.connect(server.actualPort(), "localhost")
          .onComplete(ctx.asyncAssertSuccess(ack -> {
            MqttProperties props = new MqttProperties();
            props.add(new MqttProperties.IntegerProperty(MqttProperties.SUBSCRIPTION_IDENTIFIER, SUBSCRIPTION_IDENTIFIER));
            client.subscribe(Map.of(MQTT_TOPIC, 1), props);
          }));
    });

    serverReceived.awaitSuccess(5000);
  }

  /**
   * Server grants QoS 1 in SUBACK — the client's subscribeCompletionHandler must
   * see a MqttSubAckMessage with the granted code.
   */
  @Test
  public void subscribeAckGrantedQos1(TestContext ctx) {
    Async ackReceived = ctx.async();

    server.endpointHandler(endpoint -> {
      endpoint.subscribeHandler(subscribe -> endpoint.subscribeAcknowledge(subscribe.messageId(),
          List.of(MqttSubAckReasonCode.GRANTED_QOS1),
          MqttProperties.NO_PROPERTIES));
      endpoint.accept(false);
    });

    startServer(ctx, () -> {
      MqttClientOptions options = v5Options();
      MqttClient client = MqttClient.create(vertx, options);

      client.subscribeCompletionHandler((MqttSubAckMessage ack) -> {
        ctx.assertEquals(1, ack.grantedQoSLevels().size());
        ctx.assertEquals((int) MqttSubAckReasonCode.GRANTED_QOS1.value(), ack.grantedQoSLevels().get(0).intValue());
        ackReceived.complete();
      });

      client.connect(server.actualPort(), "localhost")
          .onComplete(ctx.asyncAssertSuccess(ack -> client.subscribe(Map.of(MQTT_TOPIC, 1))));
    });

    ackReceived.awaitSuccess(5000);
  }

  /**
   * Server refuses the subscription with QUOTA_EXCEEDED — client sees
   * the error reason code in the SUBACK.
   */
  @Test
  public void subscribeAckError(TestContext ctx) {
    Async ackReceived = ctx.async();

    server.endpointHandler(endpoint -> {
      endpoint.subscribeHandler(subscribe -> endpoint.subscribeAcknowledge(subscribe.messageId(),
          List.of(MqttSubAckReasonCode.UNSPECIFIED_ERROR),
          MqttProperties.NO_PROPERTIES));
      endpoint.accept(false);
    });

    startServer(ctx, () -> {
      MqttClientOptions options = v5Options();
      MqttClient client = MqttClient.create(vertx, options);

      client.subscribeCompletionHandler((MqttSubAckMessage ack) -> {
        ctx.assertEquals(1, ack.grantedQoSLevels().size());
        ctx.assertEquals(MqttSubAckReasonCode.UNSPECIFIED_ERROR.value(), ack.grantedQoSLevels().get(0).byteValue());
        ackReceived.complete();
      });

      client.connect(server.actualPort(), "localhost")
          .onComplete(ctx.asyncAssertSuccess(ack -> client.subscribe(Map.of("/topic", MqttQoS.AT_LEAST_ONCE.value()))));
    });

    ackReceived.awaitSuccess(5000);
  }

  /**
   * SUBACK with a reason-string property in MqttProperties must be surfaced
   * on the client via MqttSubAckMessage.properties().
   */
  @Test
  public void subscribeAckWithReasonStringProperty(TestContext ctx) {
    String reasonString = "test-reason";
    Async ackReceived = ctx.async();

    server.endpointHandler(endpoint -> {
      endpoint.subscribeHandler(subscribe -> {
        MqttProperties subackProps = new MqttProperties();
        subackProps.add(new MqttProperties.StringProperty(MqttProperties.REASON_STRING, reasonString));
        endpoint.subscribeAcknowledge(subscribe.messageId(), List.of(MqttSubAckReasonCode.GRANTED_QOS0), subackProps);
      });
      endpoint.accept(false);
    });

    startServer(ctx, () -> {
      MqttClientOptions options = v5Options();
      MqttClient client = MqttClient.create(vertx, options);

      client.subscribeCompletionHandler((MqttSubAckMessage ack) -> {
        MqttProperties.MqttProperty<?> prop = ack.properties().getProperty(MqttProperties.REASON_STRING);
        ctx.assertNotNull(prop, "REASON_STRING property must be present in SUBACK");
        ctx.assertEquals(reasonString, prop.value());
        ackReceived.complete();
      });

      client.connect(server.actualPort(), "localhost")
          .onComplete(ctx.asyncAssertSuccess(ack -> client.subscribe(Map.of(MQTT_TOPIC, 0))));
    });

    ackReceived.awaitSuccess(5000);
  }

  /**
   * When the server sends SUBSCRIPTION_IDENTIFIER_AVAILABLE=0 in CONNACK,
   * any SUBSCRIBE with a SUBSCRIPTION_IDENTIFIER property must be rejected
   * client-side with MQTT_SUBSCRIPTION_IDENTIFIERS_NOT_SUPPORTED.
   */
  @Test
  public void subscribeWithIdentifierRejectedWhenServerDisablesIt(TestContext ctx) {
    Async rejected = ctx.async();

    server.endpointHandler(endpoint -> {
      // Server explicitly disables subscription identifiers
      MqttProperties connAckProps = new MqttProperties();
      connAckProps.add(new MqttProperties.IntegerProperty(
        MqttProperties.SUBSCRIPTION_IDENTIFIER_AVAILABLE, 0));
      endpoint.accept(false, connAckProps);
    });

    startServer(ctx, () -> {
      MqttClient client = MqttClient.create(vertx, v5Options());
      client.connect(server.actualPort(), "localhost")
        .onComplete(ctx.asyncAssertSuccess(ack -> {
          MqttProperties props = new MqttProperties();
          props.add(new MqttProperties.IntegerProperty(MqttProperties.SUBSCRIPTION_IDENTIFIER, SUBSCRIPTION_IDENTIFIER));

          client.subscribe(Map.of(MQTT_TOPIC, 1), props)
            .onComplete(ctx.asyncAssertFailure(err -> {
              ctx.assertTrue(err instanceof MqttException, "Expected MqttException");
              ctx.assertEquals(MqttException.MQTT_SUBSCRIPTION_IDENTIFIERS_NOT_SUPPORTED,
                ((MqttException) err).code());
              rejected.complete();
            }));
        }));
    });

    rejected.awaitSuccess(5000);
  }

  /**
   * A SUBSCRIBE with a SUBSCRIPTION_IDENTIFIER on a non-MQTT-5 connection must
   * be rejected client-side with MQTT_SUBSCRIPTION_IDENTIFIERS_NOT_SUPPORTED.
   */
  @Test
  public void subscribeWithIdentifierRejectedOnMqtt4(TestContext ctx) {
    Async rejected = ctx.async();

    server.endpointHandler(endpoint -> endpoint.accept(false));

    startServer(ctx, () -> {
      // Plain MQTT 4 client
      MqttClientOptions opts = new MqttClientOptions();
      MqttClient client = MqttClient.create(vertx, opts);
      client.connect(server.actualPort(), "localhost")
        .onComplete(ctx.asyncAssertSuccess(ack -> {
          MqttProperties props = new MqttProperties();
          props.add(new MqttProperties.IntegerProperty(MqttProperties.SUBSCRIPTION_IDENTIFIER, SUBSCRIPTION_IDENTIFIER));

          client.subscribe(Map.of(MQTT_TOPIC, 1), props)
            .onComplete(ctx.asyncAssertFailure(err -> {
              ctx.assertTrue(err instanceof MqttException, "Expected MqttException");
              ctx.assertEquals(MqttException.MQTT_SUBSCRIPTION_IDENTIFIERS_NOT_SUPPORTED,
                ((MqttException) err).code());
              rejected.complete();
            }));
        }));
    });

    rejected.awaitSuccess(5000);
  }

  /**
   * When the server sends WILDCARD_SUBSCRIPTION_AVAILABLE=0 in CONNACK,
   * any SUBSCRIBE with a wildcard topic filter must be rejected client-side
   * with MQTT_WILDCARD_SUBSCRIPTIONS_NOT_SUPPORTED.
   */
  @Test
  public void subscribeWithWildcardRejectedWhenServerDisablesIt(TestContext ctx) {
    Async rejected = ctx.async();

    server.endpointHandler(endpoint -> {
      MqttProperties connAckProps = new MqttProperties();
      connAckProps.add(new MqttProperties.IntegerProperty(
        MqttProperties.WILDCARD_SUBSCRIPTION_AVAILABLE, 0));
      endpoint.accept(false, connAckProps);
    });

    startServer(ctx, () -> {
      MqttClient client = MqttClient.create(vertx, v5Options());
      client.connect(server.actualPort(), "localhost")
        .onComplete(ctx.asyncAssertSuccess(ack -> {
          ctx.assertFalse(ack.wildcardSubscriptionAvailable(),
            "wildcardSubscriptionAvailable() must return false");

          client.subscribe(Map.of("/wildcard/+/topic", 1))
            .onComplete(ctx.asyncAssertFailure(err -> {
              ctx.assertTrue(err instanceof MqttException, "Expected MqttException");
              ctx.assertEquals(MqttException.MQTT_WILDCARD_SUBSCRIPTIONS_NOT_SUPPORTED,
                ((MqttException) err).code());
              rejected.complete();
            }));
        }));
    });

    rejected.awaitSuccess(5000);
  }

  /**
   * When the server sends SHARED_SUBSCRIPTION_AVAILABLE=0 in CONNACK,
   * any SUBSCRIBE with a $share/... topic filter must be rejected client-side
   * with MQTT_SHARED_SUBSCRIPTIONS_NOT_SUPPORTED.
   */
  @Test
  public void subscribeWithSharedRejectedWhenServerDisablesIt(TestContext ctx) {
    Async rejected = ctx.async();

    server.endpointHandler(endpoint -> {
      MqttProperties connAckProps = new MqttProperties();
      connAckProps.add(new MqttProperties.IntegerProperty(
        MqttProperties.SHARED_SUBSCRIPTION_AVAILABLE, 0));
      endpoint.accept(false, connAckProps);
    });

    startServer(ctx, () -> {
      MqttClient client = MqttClient.create(vertx, v5Options());
      client.connect(server.actualPort(), "localhost")
        .onComplete(ctx.asyncAssertSuccess(ack -> {
          ctx.assertFalse(ack.sharedSubscriptionAvailable(),
            "sharedSubscriptionAvailable() must return false");

          client.subscribe(Map.of("$share/group/topic", 1))
            .onComplete(ctx.asyncAssertFailure(err -> {
              ctx.assertTrue(err instanceof MqttException, "Expected MqttException");
              ctx.assertEquals(MqttException.MQTT_SHARED_SUBSCRIPTIONS_NOT_SUPPORTED,
                ((MqttException) err).code());
              rejected.complete();
            }));
        }));
    });

    rejected.awaitSuccess(5000);
  }

  /**
   * When the server omits WILDCARD_SUBSCRIPTION_AVAILABLE in CONNACK (default = supported),
   * wildcardSubscriptionAvailable() must return null and wildcard subscriptions must succeed.
   */
  @Test
  public void wildcardSubscriptionAvailableNullWhenAbsent(TestContext ctx) {
    Async done = ctx.async();

    server.endpointHandler(endpoint -> {
      endpoint.subscribeHandler(sub ->
        endpoint.subscribeAcknowledge(sub.messageId(),
          List.of(MqttSubAckReasonCode.GRANTED_QOS0), MqttProperties.NO_PROPERTIES));
      endpoint.accept(false); // no WILDCARD_SUBSCRIPTION_AVAILABLE property
    });

    startServer(ctx, () -> {
      MqttClient client = MqttClient.create(vertx, v5Options());
      client.connect(server.actualPort(), "localhost")
        .onComplete(ctx.asyncAssertSuccess(ack -> {
          ctx.assertNull(ack.wildcardSubscriptionAvailable(),
            "wildcardSubscriptionAvailable() must be null when absent");
          client.subscribe(Map.of("/wildcard/#", 0))
            .onComplete(ctx.asyncAssertSuccess(id -> done.complete()));
        }));
    });

    done.awaitSuccess(5000);
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
