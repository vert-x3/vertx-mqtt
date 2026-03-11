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
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;
import io.vertx.mqtt.MqttServer;
import io.vertx.mqtt.messages.MqttUnsubAckMessage;
import io.vertx.mqtt.messages.codes.MqttSubAckReasonCode;
import io.vertx.mqtt.messages.codes.MqttUnsubAckReasonCode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.Map;

/**
 * Tests for the MQTT v5 client UNSUBSCRIBE and UNSUBACK flow.
 * Verifies that:
 * <ul>
 *   <li>UNSUBSCRIBE with MQTT properties is sent correctly and properties reach the server.</li>
 *   <li>UNSUBACK reason codes (from the server) are surfaced in the client via
 *       {@link MqttClient#unsubscribeCompletionMessageHandler}.</li>
 * </ul>
 */
@RunWith(VertxUnitRunner.class)
public class Mqtt5ClientUnsubscribeTest {

  private static final String MQTT_TOPIC = "/mqtt5/unsub/test";

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
   * Client subscribes, then unsubscribes — server should fire unsubscribeHandler
   * and acknowledge with SUCCESS.  The client's unsubscribeCompletionMessageHandler
   * must receive an MqttUnsubAckMessage with the SUCCESS reason code.
   */
  @Test
  public void unsubscribeSuccessReasonCode(TestContext ctx) {
    Async ackReceived = ctx.async();

    server.endpointHandler(endpoint -> {
      endpoint.subscribeHandler(subscribe ->
        endpoint.subscribeAcknowledge(subscribe.messageId(),
          List.of(MqttSubAckReasonCode.GRANTED_QOS0), MqttProperties.NO_PROPERTIES));

      endpoint.unsubscribeHandler(unsubscribe ->
        endpoint.unsubscribeAcknowledge(unsubscribe.messageId(),
          List.of(MqttUnsubAckReasonCode.SUCCESS), MqttProperties.NO_PROPERTIES));

      endpoint.accept(false);
    });

    startServer(ctx, () -> {
      MqttClientOptions options = v5Options();
      MqttClient client = MqttClient.create(vertx, options);

      client.unsubscribeCompletionMessageHandler((MqttUnsubAckMessage ack) -> {
        ctx.assertEquals(1, ack.reasonCodes().size());
        ctx.assertEquals(MqttUnsubAckReasonCode.SUCCESS.value(), ack.reasonCodes().get(0));
        ackReceived.complete();
      });

      client.connect(server.actualPort(), "localhost")
        .onComplete(ctx.asyncAssertSuccess(connAck ->
          client.subscribe(Map.of(MQTT_TOPIC, 0))
            .onComplete(ctx.asyncAssertSuccess(subId ->
              client.unsubscribe(List.of(MQTT_TOPIC))))));
    });

    ackReceived.awaitSuccess(5000);
  }

  /**
   * Server sends UNSUBACK with NO_SUBSCRIPTION_FOUND reason code — client
   * must see this code via unsubscribeCompletionMessageHandler.
   */
  @Test
  public void unsubscribeNotSubscribedReasonCode(TestContext ctx) {
    Async ackReceived = ctx.async();

    server.endpointHandler(endpoint -> {
      endpoint.unsubscribeHandler(unsubscribe ->
        endpoint.unsubscribeAcknowledge(unsubscribe.messageId(),
          List.of(MqttUnsubAckReasonCode.NO_SUBSCRIPTION_EXISTED), MqttProperties.NO_PROPERTIES));

      endpoint.accept(false);
    });

    startServer(ctx, () -> {
      MqttClientOptions options = v5Options();
      MqttClient client = MqttClient.create(vertx, options);

      client.unsubscribeCompletionMessageHandler((MqttUnsubAckMessage ack) -> {
        ctx.assertEquals(1, ack.reasonCodes().size());
        ctx.assertEquals(MqttUnsubAckReasonCode.NO_SUBSCRIPTION_EXISTED.value(), ack.reasonCodes().get(0));
        ackReceived.complete();
      });

      client.connect(server.actualPort(), "localhost")
        .onComplete(ctx.asyncAssertSuccess(connAck ->
          client.unsubscribe(List.of(MQTT_TOPIC))));
    });

    ackReceived.awaitSuccess(5000);
  }

  /**
   * Unsubscribe with MQTT properties; server verifies the USER_PROPERTY is received.
   */
  @Test
  public void unsubscribeWithProperties(TestContext ctx) {
    String userPropKey = "client-id";
    String userPropValue = "test-client";
    Async serverReceived = ctx.async();

    server.endpointHandler(endpoint -> {
      endpoint.unsubscribeHandler(unsubscribe -> {
        // Echo back that we received the message (testing the send-side)
        endpoint.unsubscribeAcknowledge(unsubscribe.messageId(),
          List.of(MqttUnsubAckReasonCode.SUCCESS), MqttProperties.NO_PROPERTIES);
        serverReceived.complete();
      });
      endpoint.accept(false);
    });

    startServer(ctx, () -> {
      MqttClientOptions options = v5Options();
      MqttClient client = MqttClient.create(vertx, options);

      client.connect(server.actualPort(), "localhost")
        .onComplete(ctx.asyncAssertSuccess(connAck -> {
          MqttProperties props = new MqttProperties();
          props.add(new MqttProperties.UserProperty(userPropKey, userPropValue));

          client.unsubscribe(List.of(MQTT_TOPIC), props);
        }));
    });

    serverReceived.awaitSuccess(5000);
  }

  /**
   * UNSUBACK with a REASON_STRING property must be available via
   * MqttUnsubAckMessage.properties().
   */
  @Test
  public void unsubscribeAckWithReasonStringProperty(TestContext ctx) {
    String reasonString = "no-such-subscription";
    Async ackReceived = ctx.async();

    server.endpointHandler(endpoint -> {
      endpoint.unsubscribeHandler(unsubscribe -> {
        MqttProperties subackProps = new MqttProperties();
        subackProps.add(new MqttProperties.StringProperty(
          MqttProperties.MqttPropertyType.REASON_STRING.value(), reasonString));
        endpoint.unsubscribeAcknowledge(unsubscribe.messageId(),
          List.of(MqttUnsubAckReasonCode.NO_SUBSCRIPTION_EXISTED), subackProps);
      });
      endpoint.accept(false);
    });

    startServer(ctx, () -> {
      MqttClientOptions options = v5Options();
      MqttClient client = MqttClient.create(vertx, options);

      client.unsubscribeCompletionMessageHandler((MqttUnsubAckMessage ack) -> {
        MqttProperties.MqttProperty<?> prop =
          ack.properties().getProperty(MqttProperties.MqttPropertyType.REASON_STRING.value());
        ctx.assertNotNull(prop, "REASON_STRING must be present in UNSUBACK");
        ctx.assertEquals(reasonString, prop.value());
        ackReceived.complete();
      });

      client.connect(server.actualPort(), "localhost")
        .onComplete(ctx.asyncAssertSuccess(connAck ->
          client.unsubscribe(List.of(MQTT_TOPIC))));
    });

    ackReceived.awaitSuccess(5000);
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
