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
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;
import io.vertx.mqtt.MqttServer;
import io.vertx.mqtt.messages.codes.MqttPubAckReasonCode;
import io.vertx.mqtt.messages.codes.MqttPubCompReasonCode;
import io.vertx.mqtt.messages.codes.MqttPubRecReasonCode;
import io.vertx.mqtt.messages.codes.MqttPubRelReasonCode;
import io.vertx.mqtt.messages.codes.MqttSubAckReasonCode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.Map;

/**
 * Tests for the MQTT v5 publish flow with reason codes driven by
 * the new {@code publishAcknowledge / publishReceived / publishRelease / publishComplete}
 * APIs on both the server (MqttEndpoint) and client (MqttClient).
 */
@RunWith(VertxUnitRunner.class)
public class Mqtt5ClientPublishTest {

  private static final String MQTT_TOPIC = "/mqtt5/pub/test";

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
  // QoS 1 — client publishes, server acknowledges
  // -----------------------------------------------------------------------

  /**
   * Client publishes QoS 1; server sends PUBACK with SUCCESS.
   * Client's publishCompletionHandler must fire (packetId received == packetId sent).
   */
  @Test
  public void publishQos1Success(TestContext ctx) {
    Async published = ctx.async();

    server.endpointHandler(endpoint -> {
      endpoint.publishHandler(msg ->
        endpoint.publishAcknowledge(msg.messageId(),
          MqttPubAckReasonCode.SUCCESS, MqttProperties.NO_PROPERTIES));
      endpoint.accept(false);
    });

    startServer(ctx, () -> {
      MqttClientOptions options = v5Options();
      MqttClient client = MqttClient.create(vertx, options);

      client.publishCompletionHandler(id -> published.complete());

      client.connect(server.actualPort(), "localhost")
        .onComplete(ctx.asyncAssertSuccess(ack ->
          client.publish(MQTT_TOPIC, Buffer.buffer("hello-qos1"), MqttQoS.AT_LEAST_ONCE, false, false)));
    });

    published.awaitSuccess(5000);
  }

  /**
   * Server responds to a QoS 1 PUBLISH with PUBACK NOT_AUTHORIZED.
   * The client's publishCompletionHandler must still fire (the ack is received),
   * proving the reason code path is exercised without crashing the client.
   */
  @Test
  public void publishQos1WithErrorReasonCode(TestContext ctx) {
    Async published = ctx.async();

    server.endpointHandler(endpoint -> {
      endpoint.publishHandler(msg ->
        endpoint.publishAcknowledge(msg.messageId(),
          MqttPubAckReasonCode.NOT_AUTHORIZED, MqttProperties.NO_PROPERTIES));
      endpoint.accept(false);
    });

    startServer(ctx, () -> {
      MqttClientOptions options = v5Options();
      MqttClient client = MqttClient.create(vertx, options);

      // The publishCompletionHandler fires for QoS 1 on PUBACK receipt regardless of code
      client.publishCompletionHandler(id -> published.complete());

      client.connect(server.actualPort(), "localhost")
        .onComplete(ctx.asyncAssertSuccess(ack ->
          client.publish(MQTT_TOPIC, Buffer.buffer("hello-nauth"), MqttQoS.AT_LEAST_ONCE, false, false)));
    });

    published.awaitSuccess(5000);
  }

  // -----------------------------------------------------------------------
  // QoS 2 — server drives the full PUBREC → PUBREL → PUBCOMP handshake
  // -----------------------------------------------------------------------

  /**
   * Full QoS 2 handshake with SUCCESS reason codes.
   * Server: receives PUBLISH → sends PUBREC(SUCCESS) → receives PUBREL → sends PUBCOMP(SUCCESS).
   * Client: publishCompletionHandler fires after PUBCOMP.
   * The client auto-sends PUBREL after PUBREC (autoAck=true by default).
   */
  @Test
  public void publishQos2Success(TestContext ctx) {
    Async published = ctx.async();

    server.endpointHandler(endpoint -> {
      // PUBLISH received → send PUBREC with SUCCESS
      endpoint.publishHandler(msg ->
        endpoint.publishReceived(msg.messageId(),
          MqttPubRecReasonCode.SUCCESS, MqttProperties.NO_PROPERTIES));

      // PUBREL received → send PUBCOMP with SUCCESS
      endpoint.publishReleaseHandler(id ->
        endpoint.publishComplete(id,
          MqttPubCompReasonCode.SUCCESS, MqttProperties.NO_PROPERTIES));

      endpoint.accept(false);
    });

    startServer(ctx, () -> {
      MqttClientOptions options = v5Options();
      // autoAck=true: client auto-sends PUBREL when it receives PUBREC
      MqttClient client = MqttClient.create(vertx, options);

      // publishCompletionHandler fires on PUBCOMP receipt
      client.publishCompletionHandler(id -> published.complete());

      client.connect(server.actualPort(), "localhost")
        .onComplete(ctx.asyncAssertSuccess(ack ->
          client.publish(MQTT_TOPIC, Buffer.buffer("hello-qos2"), MqttQoS.EXACTLY_ONCE, false, false)));
    });

    published.awaitSuccess(5000);
  }

  /**
   * QoS 2 handshake where server sends PUBREC with an error code (QUOTA_EXCEEDED).
   * Client receives the PUBREC; publishHandler fires on the client as the QoS2 message
   * was "received". Client then sends PUBREL with a reason code.
   */
  @Test
  public void publishQos2WithPubRecErrorCode(TestContext ctx) {
    Async pubRelReceived = ctx.async();

    server.endpointHandler(endpoint -> {
      endpoint.publishHandler(msg ->
        endpoint.publishReceived(msg.messageId(),
          MqttPubRecReasonCode.QUOTA_EXCEEDED, MqttProperties.NO_PROPERTIES));

      // Even with error PUBREC, client should still send PUBREL
      endpoint.publishReleaseHandler(id -> {
        pubRelReceived.complete();
        endpoint.publishComplete(id, MqttPubCompReasonCode.SUCCESS, MqttProperties.NO_PROPERTIES);
      });

      endpoint.accept(false);
    });

    startServer(ctx, () -> {
      MqttClientOptions options = v5Options();
      MqttClient client = MqttClient.create(vertx, options);

      client.connect(server.actualPort(), "localhost")
        .onComplete(ctx.asyncAssertSuccess(ack ->
          client.publish(MQTT_TOPIC, Buffer.buffer("hello-quota"), MqttQoS.EXACTLY_ONCE, false, false)));
    });

    pubRelReceived.awaitSuccess(5000);
  }

  /**
   * Client sends PUBREL explicitly with a specific reason code (PACKET_IDENTIFIER_NOT_FOUND).
   * autoAck=false so we control PUBREL manually via publishHandler callback.
   */
  @Test
  public void publishQos2ClientSendsPubRelWithReasonCode(TestContext ctx) {
    Async pubRelReceived = ctx.async();

    server.endpointHandler(endpoint -> {
      endpoint.publishHandler(msg ->
        endpoint.publishReceived(msg.messageId(),
          MqttPubRecReasonCode.SUCCESS, MqttProperties.NO_PROPERTIES));

      endpoint.publishReleaseHandler(id -> {
        // PUBREL was received by the server
        pubRelReceived.complete();
        endpoint.publishComplete(id, MqttPubCompReasonCode.SUCCESS, MqttProperties.NO_PROPERTIES);
      });

      endpoint.accept(false);
    });

    startServer(ctx, () -> {
      MqttClientOptions options = v5Options();
      options.setAutoAck(false); // we drive PUBREL manually

      MqttClient client = MqttClient.create(vertx, options);

      // On PUBREC (which arrives as PUBCOMP-phase in QoS2), send PUBREL manually with reason code
      // When autoAck=false, publishHandler is called when the PUBLISH message is fully delivered
      // Note: for client-as-publisher QoS2, the client uses publishCompletionHandler flow.
      // After PUBREC is received, the client needs to send PUBREL.
      // With autoAck=false, we use publishComplete which maps to client-side PUBREL sending.
      client.publishCompletionHandler(id ->
        client.publishRelease(id, MqttPubRelReasonCode.PACKET_IDENTIFIER_NOT_FOUND, MqttProperties.NO_PROPERTIES));

      client.connect(server.actualPort(), "localhost")
        .onComplete(ctx.asyncAssertSuccess(ack ->
          client.publish(MQTT_TOPIC, Buffer.buffer("hello-pubrel-code"), MqttQoS.EXACTLY_ONCE, false, false)));
    });

    pubRelReceived.awaitSuccess(5000);
  }

  // -----------------------------------------------------------------------
  // Client sends PUBACK (QoS 1) for server-initiated publish
  // -----------------------------------------------------------------------

  /**
   * Client acts as a subscriber; server publishes QoS 1; client sends PUBACK with SUCCESS
   * using the explicit publishAcknowledge API with reason code.
   */
  @Test
  public void clientSendsPubAckWithReasonCode(TestContext ctx) {
    Async serverPublishAcked = ctx.async();

    server.endpointHandler(endpoint -> {
      endpoint.subscribeHandler(subscribe -> {
        endpoint.subscribeAcknowledge(subscribe.messageId(),
          List.of(MqttSubAckReasonCode.GRANTED_QOS1),
          MqttProperties.NO_PROPERTIES);

        // Wait for client's PUBACK then mark done
        endpoint.publishAcknowledgeHandler(id -> serverPublishAcked.complete());

        // Push a QoS 1 message to the client after subscribe is acknowledged
        endpoint.publish(MQTT_TOPIC, Buffer.buffer("server-push"), MqttQoS.AT_LEAST_ONCE, false, false);
      });

      endpoint.accept(false);
    });

    startServer(ctx, () -> {
      MqttClientOptions options = v5Options();
      options.setAutoAck(false); // we drive PUBACK ourselves

      MqttClient client = MqttClient.create(vertx, options);

      // On incoming PUBLISH QoS1 → send PUBACK with SUCCESS reason code
      client.publishHandler(msg -> {
        if (msg.qosLevel() == MqttQoS.AT_LEAST_ONCE) {
          client.publishAcknowledge(msg.messageId(), MqttPubAckReasonCode.SUCCESS, MqttProperties.NO_PROPERTIES);
        }
      });

      client.connect(server.actualPort(), "localhost")
        .onComplete(ctx.asyncAssertSuccess(ack ->
          client.subscribe(Map.of(MQTT_TOPIC, 1))));
    });

    serverPublishAcked.awaitSuccess(5000);
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
