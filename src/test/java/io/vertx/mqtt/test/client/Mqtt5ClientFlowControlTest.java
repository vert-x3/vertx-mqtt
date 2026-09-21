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
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;
import io.vertx.mqtt.MqttException;
import io.vertx.mqtt.MqttServer;
import io.vertx.mqtt.messages.codes.MqttPubAckReasonCode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests for MQTT 5.0 flow control enforcement:
 * - Receive Maximum: client must not exceed the server's in-flight QoS 1/2 limit.
 * - Maximum QoS: client must reject publishes whose QoS exceeds server's Maximum QoS.
 */
@RunWith(VertxUnitRunner.class)
public class Mqtt5ClientFlowControlTest {

  private static final String TOPIC = "/mqtt5/flow/test";

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
  // Receive Maximum enforcement
  // -----------------------------------------------------------------------

  /**
   * Server advertises RECEIVE_MAXIMUM=2 in CONNACK.
   * Client sends 2 QoS 1 messages without waiting for PUBACK: both succeed.
   * A third QoS 1 message must fail immediately with MQTT_INFLIGHT_QUEUE_FULL.
   */
  @Test
  public void receiveMaximumRespected(TestContext ctx) {
    Async thirdFailed = ctx.async();

    server.endpointHandler(endpoint -> {
      // Advertise RECEIVE_MAXIMUM=2 — do NOT send PUBACK automatically so
      // the client's in-flight counter stays at 2 after the first two publishes.
      endpoint.accept(false, buildConnAckPropsWithReceiveMaximum(2));
      // intentionally no publishHandler → messages pile up unacknowledged
    });

    startServer(ctx, () -> {
      MqttClientOptions opts = v5Options();
      opts.setAutoAck(false);
      // raise local inflight limit so it doesn't interfere
      opts.setMaxInflightQueue(100);
      MqttClient client = MqttClient.create(vertx, opts);

      client.connect(server.actualPort(), "localhost")
          .onComplete(ctx.asyncAssertSuccess(ack -> {
            // First two publishes should succeed
            Future<Integer> f1 = client.publish(TOPIC, Buffer.buffer("msg1"), MqttQoS.AT_LEAST_ONCE, false, false);
            Future<Integer> f2 = client.publish(TOPIC, Buffer.buffer("msg2"), MqttQoS.AT_LEAST_ONCE, false, false);

            CompositeFuture.all(f1, f2).onComplete(ctx.asyncAssertSuccess(v -> {
              // Third publish must fail: server Receive Maximum exceeded
              client.publish(TOPIC, Buffer.buffer("msg3"), MqttQoS.AT_LEAST_ONCE, false, false)
                  .onComplete(ctx.asyncAssertFailure(err -> {
                    ctx.assertTrue(err instanceof MqttException);
                    ctx.assertEquals(MqttException.MQTT_INFLIGHT_QUEUE_FULL,
                        ((MqttException) err).code());
                    thirdFailed.complete();
                  }));
            }));
          }));
    });

    thirdFailed.awaitSuccess(5000);
  }

  /**
   * Server advertises RECEIVE_MAXIMUM=2 but QoS 0 messages are NOT counted against it.
   * Sending 3 QoS 0 messages after 2 unacknowledged QoS 1 messages must all succeed.
   */
  @Test
  public void receiveMaximumDoesNotApplyToQos0(TestContext ctx) {
    Async allSent = ctx.async();

    server.endpointHandler(endpoint -> {
      endpoint.accept(false, buildConnAckPropsWithReceiveMaximum(2));
      // intentionally no publishHandler → QoS1 messages stay unacknowledged
    });

    startServer(ctx, () -> {
      MqttClientOptions opts = v5Options();
      opts.setAutoAck(false);
      opts.setMaxInflightQueue(100);
      MqttClient client = MqttClient.create(vertx, opts);

      client.connect(server.actualPort(), "localhost")
          .onComplete(ctx.asyncAssertSuccess(ack -> {
            // Saturate server receive window with 2 QoS 1 messages
            Future<Integer> f1 = client.publish(TOPIC, Buffer.buffer("q1-1"), MqttQoS.AT_LEAST_ONCE, false, false);
            Future<Integer> f2 = client.publish(TOPIC, Buffer.buffer("q1-2"), MqttQoS.AT_LEAST_ONCE, false, false);

            CompositeFuture.all(f1, f2).onComplete(ctx.asyncAssertSuccess(v -> {
              // QoS 0 publishes must still succeed regardless of receive maximum
              List<Future> qos0 = new ArrayList<>();
              for (int i = 0; i < 3; i++) {
                qos0.add(client.publish(TOPIC, Buffer.buffer("q0-" + i), MqttQoS.AT_MOST_ONCE, false, false));
              }
              CompositeFuture.all(qos0).onComplete(ctx.asyncAssertSuccess(v2 -> allSent.complete()));
            }));
          }));
    });

    allSent.awaitSuccess(5000);
  }

  // -----------------------------------------------------------------------
  // Maximum QoS enforcement
  // -----------------------------------------------------------------------

  /**
   * Server advertises MAXIMUM_QOS=1 in CONNACK.
   * Client attempts to publish with QoS 2: must fail immediately.
   */
  @Test
  public void maxQosEnforced(TestContext ctx) {
    Async failedLatch = ctx.async();

    server.endpointHandler(endpoint -> {
      endpoint.accept(false, buildConnAckPropsWithMaxQos(1));
    });

    startServer(ctx, () -> {
      MqttClient client = MqttClient.create(vertx, v5Options());

      client.connect(server.actualPort(), "localhost")
          .onComplete(ctx.asyncAssertSuccess(ack -> {
            client.publish(TOPIC, Buffer.buffer("qos2-rejected"), MqttQoS.EXACTLY_ONCE, false, false)
                .onComplete(ctx.asyncAssertFailure(err -> {
                  ctx.assertTrue(err instanceof MqttException);
                  ctx.assertEquals(MqttException.MQTT_QOS_UNSUPPORTED, ((MqttException) err).code());
                  failedLatch.complete();
                }));
          }));
    });

    failedLatch.awaitSuccess(5000);
  }

  /**
   * Server advertises MAXIMUM_QOS=1 in CONNACK.
   * Client publishes with QoS 0 and QoS 1: both must succeed.
   */
  @Test
  public void maxQos1AllowsLowerQos(TestContext ctx) {
    Async bothSent = ctx.async();

    server.endpointHandler(endpoint -> {
      // When client publishes QoS1, server must send PUBACK
      endpoint.publishHandler(msg -> endpoint.publishAcknowledge(msg.messageId(), MqttPubAckReasonCode.SUCCESS, MqttProperties.NO_PROPERTIES));
      endpoint.accept(false, buildConnAckPropsWithMaxQos(1));
    });

    startServer(ctx, () -> {
      MqttClient client = MqttClient.create(vertx, v5Options());

      client.connect(server.actualPort(), "localhost")
          .onComplete(ctx.asyncAssertSuccess(ack -> {
            Future<Integer> f0 = client.publish(TOPIC, Buffer.buffer("qos0"), MqttQoS.AT_MOST_ONCE, false, false);
            Future<Integer> f1 = client.publish(TOPIC, Buffer.buffer("qos1"), MqttQoS.AT_LEAST_ONCE, false, false);
            CompositeFuture.all(f0, f1).onComplete(ctx.asyncAssertSuccess(v -> bothSent.complete()));
          }));
    });

    bothSent.awaitSuccess(5000);
  }

  // -----------------------------------------------------------------------

  private MqttProperties buildConnAckPropsWithReceiveMaximum(int receiveMaximum) {
    MqttProperties props = new MqttProperties();
    props.add(new MqttProperties.IntegerProperty(MqttProperties.MqttPropertyType.RECEIVE_MAXIMUM.value(), receiveMaximum));
    return props;
  }

  private MqttProperties buildConnAckPropsWithMaxQos(int maxQos) {
    MqttProperties props = new MqttProperties();
    props.add(new MqttProperties.IntegerProperty(MqttProperties.MqttPropertyType.MAXIMUM_QOS.value(), maxQos));
    return props;
  }

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
