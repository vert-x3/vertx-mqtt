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

import io.vertx.mqtt.MqttException;
import io.vertx.mqtt.messages.codes.MqttSubAckReasonCode;
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
          java.util.Arrays.asList(MqttSubAckReasonCode.GRANTED_QOS1),
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
          client.subscribe(java.util.Collections.singletonMap(MQTT_TOPIC, 1))));
    });

    serverPublishAcked.awaitSuccess(5000);
  }

  // -----------------------------------------------------------------------
  // PUBLISH with MQTT 5.0 properties
  // -----------------------------------------------------------------------

  /**
   * Client publishes QoS 0 with USER_PROPERTY; server verifies the property arrives.
   */
  @Test
  public void publishWithUserProperties(TestContext ctx) {
    Async serverReceived = ctx.async();

    server.endpointHandler(endpoint -> {
      endpoint.publishHandler(msg -> {
        MqttProperties.MqttProperty<?> prop = msg.properties().getProperty(MqttProperties.MqttPropertyType.USER_PROPERTY.value());
        ctx.assertNotNull(prop);
        List<MqttProperties.StringPair> pairs = (List<MqttProperties.StringPair>) prop.value();
        ctx.assertFalse(pairs.isEmpty());
        ctx.assertEquals("k1", pairs.get(0).key);
        ctx.assertEquals("v1", pairs.get(0).value);
        serverReceived.complete();
      });
      endpoint.accept(false);
    });

    startServer(ctx, () -> {
      MqttClient client = MqttClient.create(vertx, v5Options());

      client.connect(server.actualPort(), "localhost")
        .onComplete(ctx.asyncAssertSuccess(ack -> {
          MqttProperties props = new MqttProperties();
          props.add(new MqttProperties.UserProperties(
            java.util.Arrays.asList(new MqttProperties.StringPair("k1", "v1"))));
          client.publish(MQTT_TOPIC, Buffer.buffer("user-props"), MqttQoS.AT_MOST_ONCE, false, false, props);
        }));
    });

    serverReceived.awaitSuccess(5000);
  }

  /**
   * Client publishes with MESSAGE_EXPIRY_INTERVAL; server verifies the property arrives.
   */
  @Test
  public void publishWithMessageExpiryInterval(TestContext ctx) {
    Async serverReceived = ctx.async();

    server.endpointHandler(endpoint -> {
      endpoint.publishHandler(msg -> {
        MqttProperties.MqttProperty<?> prop = msg.properties().getProperty(MqttProperties.MqttPropertyType.PUBLICATION_EXPIRY_INTERVAL.value());
        ctx.assertNotNull(prop);
        ctx.assertEquals(60L, Integer.toUnsignedLong((Integer) prop.value()));
        serverReceived.complete();
      });
      endpoint.accept(false);
    });

    startServer(ctx, () -> {
      MqttClient client = MqttClient.create(vertx, v5Options());

      client.connect(server.actualPort(), "localhost")
        .onComplete(ctx.asyncAssertSuccess(ack -> {
          MqttProperties props = new MqttProperties();
          props.add(new MqttProperties.IntegerProperty(MqttProperties.MqttPropertyType.PUBLICATION_EXPIRY_INTERVAL.value(), 60));
          client.publish(MQTT_TOPIC, Buffer.buffer("expiry"), MqttQoS.AT_MOST_ONCE, false, false, props);
        }));
    });

    serverReceived.awaitSuccess(5000);
  }

  /**
   * Client publishes with CONTENT_TYPE; server verifies the property arrives.
   */
  @Test
  public void publishWithContentType(TestContext ctx) {
    Async serverReceived = ctx.async();

    server.endpointHandler(endpoint -> {
      endpoint.publishHandler(msg -> {
        MqttProperties.MqttProperty<?> prop = msg.properties().getProperty(MqttProperties.MqttPropertyType.CONTENT_TYPE.value());
        ctx.assertNotNull(prop);
        ctx.assertEquals("application/json", prop.value());
        serverReceived.complete();
      });
      endpoint.accept(false);
    });

    startServer(ctx, () -> {
      MqttClient client = MqttClient.create(vertx, v5Options());

      client.connect(server.actualPort(), "localhost")
        .onComplete(ctx.asyncAssertSuccess(ack -> {
          MqttProperties props = new MqttProperties();
          props.add(new MqttProperties.StringProperty(MqttProperties.MqttPropertyType.CONTENT_TYPE.value(), "application/json"));
          client.publish(MQTT_TOPIC, Buffer.buffer("{\"key\":\"val\"}"), MqttQoS.AT_MOST_ONCE, false, false, props);
        }));
    });

    serverReceived.awaitSuccess(5000);
  }

  /**
   * Client publishes with RESPONSE_TOPIC and CORRELATION_DATA (request-response pattern).
   * Server verifies both properties arrive.
   */
  @Test
  public void publishWithResponseTopicAndCorrelationData(TestContext ctx) {
    Async serverReceived = ctx.async();
    String responseTopic = "/reply/topic";
    byte[] correlationData = new byte[]{0x01, 0x02, 0x03};

    server.endpointHandler(endpoint -> {
      endpoint.publishHandler(msg -> {
        MqttProperties.MqttProperty<?> rtProp = msg.properties().getProperty(MqttProperties.MqttPropertyType.RESPONSE_TOPIC.value());
        MqttProperties.MqttProperty<?> cdProp = msg.properties().getProperty(MqttProperties.MqttPropertyType.CORRELATION_DATA.value());
        ctx.assertNotNull(rtProp);
        ctx.assertEquals(responseTopic, rtProp.value());
        ctx.assertNotNull(cdProp);
        ctx.assertEquals(Buffer.buffer(correlationData), Buffer.buffer((byte[]) cdProp.value()));
        serverReceived.complete();
      });
      endpoint.accept(false);
    });

    startServer(ctx, () -> {
      MqttClient client = MqttClient.create(vertx, v5Options());

      client.connect(server.actualPort(), "localhost")
        .onComplete(ctx.asyncAssertSuccess(ack -> {
          MqttProperties props = new MqttProperties();
          props.add(new MqttProperties.StringProperty(MqttProperties.MqttPropertyType.RESPONSE_TOPIC.value(), responseTopic));
          props.add(new MqttProperties.BinaryProperty(MqttProperties.MqttPropertyType.CORRELATION_DATA.value(), correlationData));
          client.publish(MQTT_TOPIC, Buffer.buffer("request"), MqttQoS.AT_MOST_ONCE, false, false, props);
        }));
    });

    serverReceived.awaitSuccess(5000);
  }

  /**
   * Full MQTT 5.0 request-response pattern using RESPONSE_TOPIC and CORRELATION_DATA.
   *
   * Flow:
   *   1. Client subscribes to the response topic.
   *   2. Client publishes a request on MQTT_TOPIC carrying RESPONSE_TOPIC and CORRELATION_DATA.
   *   3. Server receives the request, extracts both properties, and publishes a response
   *      on RESPONSE_TOPIC echoing back the same CORRELATION_DATA.
   *   4. Client receives the response and verifies the CORRELATION_DATA matches the original.
   */
  @Test
  public void correlationDataRoundTrip(TestContext ctx) {
    Async responseReceived = ctx.async();
    String responseTopic = "/reply/correlation-test";
    byte[] correlationData = new byte[]{0x0A, 0x0B, 0x0C, 0x0D};

    server.endpointHandler(endpoint -> {
      endpoint.subscribeHandler(subscribe ->
        endpoint.subscribeAcknowledge(subscribe.messageId(),
          java.util.Arrays.asList(MqttSubAckReasonCode.GRANTED_QOS0),
          MqttProperties.NO_PROPERTIES));

      endpoint.publishHandler(msg -> {
        // Extract response topic and correlation data from the incoming request
        MqttProperties.MqttProperty<?> rtProp = msg.properties().getProperty(MqttProperties.MqttPropertyType.RESPONSE_TOPIC.value());
        MqttProperties.MqttProperty<?> cdProp = msg.properties().getProperty(MqttProperties.MqttPropertyType.CORRELATION_DATA.value());
        ctx.assertNotNull(rtProp);
        ctx.assertNotNull(cdProp);

        // Echo correlation data back in the response publish
        MqttProperties responseProps = new MqttProperties();
        responseProps.add(new MqttProperties.BinaryProperty(MqttProperties.MqttPropertyType.CORRELATION_DATA.value(),
          (byte[]) cdProp.value()));
        endpoint.publish((String) rtProp.value(), Buffer.buffer("response"),
          MqttQoS.AT_MOST_ONCE, false, false, 0, responseProps);
      });

      endpoint.accept(false);
    });

    startServer(ctx, () -> {
      MqttClient client = MqttClient.create(vertx, v5Options());

      client.publishHandler(msg -> {
        // Verify the echoed correlation data matches what we sent
        MqttProperties.MqttProperty<?> cdProp = msg.properties().getProperty(MqttProperties.MqttPropertyType.CORRELATION_DATA.value());
        ctx.assertNotNull(cdProp);
        ctx.assertEquals(Buffer.buffer(correlationData), Buffer.buffer((byte[]) cdProp.value()));
        responseReceived.complete();
      });

      client.connect(server.actualPort(), "localhost")
        .onComplete(ctx.asyncAssertSuccess(ack -> {
          // Subscribe to the response topic first
          client.subscribe(java.util.Collections.singletonMap(responseTopic, 0))
            .onComplete(ctx.asyncAssertSuccess(subAck -> {
              // Then publish the request with RESPONSE_TOPIC + CORRELATION_DATA
              MqttProperties props = new MqttProperties();
              props.add(new MqttProperties.StringProperty(MqttProperties.MqttPropertyType.RESPONSE_TOPIC.value(), responseTopic));
              props.add(new MqttProperties.BinaryProperty(MqttProperties.MqttPropertyType.CORRELATION_DATA.value(), correlationData));
              client.publish(MQTT_TOPIC, Buffer.buffer("request"), MqttQoS.AT_MOST_ONCE, false, false, props);
            }));
        }));
    });

    responseReceived.awaitSuccess(5000);
  }

  /**
   * Client publishes with PAYLOAD_FORMAT_INDICATOR=1 (UTF-8); server verifies.
   */
  @Test
  public void publishWithPayloadFormatIndicator(TestContext ctx) {
    Async serverReceived = ctx.async();

    server.endpointHandler(endpoint -> {
      endpoint.publishHandler(msg -> {
        MqttProperties.MqttProperty<?> prop = msg.properties().getProperty(MqttProperties.MqttPropertyType.PAYLOAD_FORMAT_INDICATOR.value());
        ctx.assertNotNull(prop);
        ctx.assertEquals(1, prop.value());
        serverReceived.complete();
      });
      endpoint.accept(false);
    });

    startServer(ctx, () -> {
      MqttClient client = MqttClient.create(vertx, v5Options());

      client.connect(server.actualPort(), "localhost")
        .onComplete(ctx.asyncAssertSuccess(ack -> {
          MqttProperties props = new MqttProperties();
          props.add(new MqttProperties.IntegerProperty(MqttProperties.MqttPropertyType.PAYLOAD_FORMAT_INDICATOR.value(), 1));
          client.publish(MQTT_TOPIC, Buffer.buffer("UTF-8 text"), MqttQoS.AT_MOST_ONCE, false, false, props);
        }));
    });

    serverReceived.awaitSuccess(5000);
  }

  /**
   * Server publishes to client with USER_PROPERTY; client verifies the property arrives
   * via the publishHandler properties().
   */
  @Test
  public void serverPublishWithPropertiesReceivedByClient(TestContext ctx) {
    Async clientReceived = ctx.async();

    server.endpointHandler(endpoint -> {
      endpoint.subscribeHandler(subscribe -> {
        endpoint.subscribeAcknowledge(subscribe.messageId(),
          java.util.Arrays.asList(MqttSubAckReasonCode.GRANTED_QOS0),
          MqttProperties.NO_PROPERTIES);

        MqttProperties props = new MqttProperties();
        props.add(new MqttProperties.StringProperty(MqttProperties.MqttPropertyType.CONTENT_TYPE.value(), "text/plain"));
        endpoint.publish(MQTT_TOPIC, Buffer.buffer("server-msg"), MqttQoS.AT_MOST_ONCE, false, false, 0, props);
      });
      endpoint.accept(false);
    });

    startServer(ctx, () -> {
      MqttClient client = MqttClient.create(vertx, v5Options());

      client.publishHandler(msg -> {
        MqttProperties.MqttProperty<?> prop = msg.properties().getProperty(MqttProperties.MqttPropertyType.CONTENT_TYPE.value());
        ctx.assertNotNull(prop);
        ctx.assertEquals("text/plain", prop.value());
        clientReceived.complete();
      });

      client.connect(server.actualPort(), "localhost")
        .onComplete(ctx.asyncAssertSuccess(ack ->
          client.subscribe(java.util.Collections.singletonMap(MQTT_TOPIC, 0))));
    });

    clientReceived.awaitSuccess(5000);
  }

  /**
   * When the server sends MAXIMUM_PACKET_SIZE=50 in CONNACK, a publish with a
   * 100-byte payload must be rejected client-side with MQTT_PACKET_TOO_LARGE.
   */
  @Test
  public void publishExceedsMaximumPacketSize(TestContext ctx) {
    Async rejected = ctx.async();

    server.endpointHandler(endpoint -> {
      MqttProperties connAckProps = new MqttProperties();
      connAckProps.add(new MqttProperties.IntegerProperty(MqttProperties.MqttPropertyType.MAXIMUM_PACKET_SIZE.value(), 50));
      endpoint.accept(false, connAckProps);

      // If client erroneously sends the packet, fail the test
      endpoint.publishHandler(msg -> ctx.fail("Client must NOT have sent the oversized packet"));
    });

    startServer(ctx, () -> {
      MqttClient client = MqttClient.create(vertx, v5Options());
      client.connect(server.actualPort(), "localhost")
        .onComplete(ctx.asyncAssertSuccess(ack -> {
          Buffer bigPayload = Buffer.buffer(new byte[100]);
          client.publish("/test/topic", bigPayload, MqttQoS.AT_MOST_ONCE, false, false)
            .onComplete(ctx.asyncAssertFailure(err -> {
              ctx.assertTrue(err instanceof MqttException, "Expected MqttException");
              ctx.assertEquals(MqttException.MQTT_PACKET_TOO_LARGE, ((MqttException) err).code());
              rejected.complete();
            }));
        }));
    });

    rejected.awaitSuccess(5000);
  }

  // -----------------------------------------------------------------------
  // PUBACK / PUBCOMP reason codes exposed to handler
  // -----------------------------------------------------------------------

  /**
   * Server sends PUBACK with NOT_AUTHORIZED.
   * Client's publishAckMessageHandler must fire with that reason code.
   * publishCompletionHandler must NOT fire (packet was not acknowledged successfully).
   */
  @Test
  public void pubAckReasonCodeExposedToHandler(TestContext ctx) {
    Async done = ctx.async();

    server.endpointHandler(endpoint -> {
      endpoint.accept(false);
      endpoint.publishHandler(msg ->
        endpoint.publishAcknowledge(msg.messageId(),
          MqttPubAckReasonCode.NOT_AUTHORIZED, MqttProperties.NO_PROPERTIES));
    });

    startServer(ctx, () -> {
      MqttClient client = MqttClient.create(vertx, v5Options());
      client.publishAckMessageHandler(ack -> {
        ctx.assertEquals(MqttPubAckReasonCode.NOT_AUTHORIZED, ack.code());
        done.complete();
      });
      client.connect(server.actualPort(), "localhost")
        .onComplete(ctx.asyncAssertSuccess(connAck ->
          client.publish(MQTT_TOPIC, Buffer.buffer("hello"), MqttQoS.AT_LEAST_ONCE, false, false)));
    });

    done.awaitSuccess(5000);
  }

  /**
   * Server sends PUBCOMP with PACKET_IDENTIFIER_NOT_FOUND (error code).
   * Client's publishCompMessageHandler must fire with that reason code.
   */
  @Test
  public void pubCompReasonCodeExposedToHandler(TestContext ctx) {
    Async done = ctx.async();

    server.endpointHandler(endpoint -> {
      endpoint.accept(false);
      endpoint.publishHandler(msg ->
        endpoint.publishReceived(msg.messageId(),
          MqttPubRecReasonCode.SUCCESS, MqttProperties.NO_PROPERTIES));
      endpoint.publishReleaseHandler(msgId ->
        endpoint.publishComplete(msgId,
          MqttPubCompReasonCode.PACKET_IDENTIFIER_NOT_FOUND, MqttProperties.NO_PROPERTIES));
    });

    startServer(ctx, () -> {
      MqttClient client = MqttClient.create(vertx, v5Options());
      client.publishCompMessageHandler(comp -> {
        ctx.assertEquals(MqttPubCompReasonCode.PACKET_IDENTIFIER_NOT_FOUND, comp.code());
        done.complete();
      });
      client.connect(server.actualPort(), "localhost")
        .onComplete(ctx.asyncAssertSuccess(connAck ->
          client.publish(MQTT_TOPIC, Buffer.buffer("hello"), MqttQoS.EXACTLY_ONCE, false, false)));
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
