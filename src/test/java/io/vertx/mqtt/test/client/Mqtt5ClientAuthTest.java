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

import io.netty.handler.codec.mqtt.MqttFixedHeader;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageFactory;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttReasonCodeAndPropertiesVariableHeader;
import io.netty.handler.codec.mqtt.MqttVersion;
import io.vertx.core.Vertx;
import io.vertx.core.net.impl.NetSocketInternal;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;
import io.vertx.mqtt.MqttServer;
import io.vertx.mqtt.messages.MqttAuthenticationExchangeMessage;
import io.vertx.mqtt.messages.codes.MqttAuthenticateReasonCode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tests for MQTT 5.0 AUTH packet handling on the client side
 * (Enhanced Authentication, MQTT 5.0 §3.15).
 *
 * Covers:
 *   1. server-sent AUTH → registered handler is invoked with the right
 *      reason code, authentication method and authentication data
 *   2. authenticationExchange() with a non-v5 client returns a failed Future
 *   3. authenticationExchange() on a not-connected client returns a failed Future
 */
@RunWith(VertxUnitRunner.class)
public class Mqtt5ClientAuthTest {

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

  // -------------------------------------------------------------------------

  /**
   * The server accepts the connection then sends an AUTH packet
   * (CONTINUE_AUTHENTICATION, with method "SCRAM-SHA-1" and a challenge in the
   * authentication data). The client's authenticationExchangeHandler must be
   * invoked with these exact values.
   */
  @Test
  public void clientReceivesAuthFromServer(TestContext ctx) {
    Async done = ctx.async();

    final String expectedMethod = "SCRAM-SHA-1";
    final byte[] expectedChallenge = "challenge-bytes".getBytes();

    server.endpointHandler(ep -> {
      ep.accept(false);
      // After CONNACK is flushed, push an AUTH packet on the same channel.
      vertx.runOnContext(v -> {
        try {
          Field connField = ep.getClass().getDeclaredField("conn");
          connField.setAccessible(true);
          NetSocketInternal conn = (NetSocketInternal) connField.get(ep);

          MqttProperties props = new MqttProperties();
          props.add(new MqttProperties.StringProperty(
            MqttProperties.MqttPropertyType.AUTHENTICATION_METHOD.value(), expectedMethod));
          props.add(new MqttProperties.BinaryProperty(
            MqttProperties.MqttPropertyType.AUTHENTICATION_DATA.value(), expectedChallenge));

          MqttFixedHeader fixedHeader = new MqttFixedHeader(
            MqttMessageType.AUTH, false, MqttQoS.AT_MOST_ONCE, false, 0);
          MqttReasonCodeAndPropertiesVariableHeader varHeader =
            new MqttReasonCodeAndPropertiesVariableHeader(
              MqttAuthenticateReasonCode.CONTINUE_AUTHENTICATION.value(), props);
          MqttMessage auth = MqttMessageFactory.newMessage(fixedHeader, varHeader, null);
          conn.writeMessage(auth);
        } catch (Exception e) {
          ctx.fail(e);
        }
      });
    });

    AtomicReference<MqttAuthenticationExchangeMessage> received = new AtomicReference<>();

    server.listen(0).onComplete(ctx.asyncAssertSuccess(s -> {
      MqttClient client = MqttClient.create(vertx, v5Options());
      client.authenticationExchangeHandler(msg -> {
        received.set(msg);
        ctx.assertEquals(MqttAuthenticateReasonCode.CONTINUE_AUTHENTICATION, msg.reasonCode());
        ctx.assertEquals(expectedMethod, msg.authenticationMethod());
        ctx.assertNotNull(msg.authenticationData());
        ctx.assertTrue(java.util.Arrays.equals(expectedChallenge, msg.authenticationData().getBytes()));
        done.complete();
      });
      client.connect(server.actualPort(), "localhost").onComplete(ctx.asyncAssertSuccess());
    }));

    done.awaitSuccess(5000);
    ctx.assertNotNull(received.get());
  }

  // -------------------------------------------------------------------------

  /**
   * authenticationExchange() must fail fast when the client is configured for
   * a protocol version other than MQTT 5.0 — AUTH is a v5-only packet.
   */
  @Test
  public void authenticationExchangeRejectedOnNonV5(TestContext ctx) {
    Async done = ctx.async();

    MqttClientOptions v3 = new MqttClientOptions(); // default = MQTT 3.1.1
    MqttClient client = MqttClient.create(vertx, v3);

    server.endpointHandler(ep -> ep.accept(false));
    server.listen(0).onComplete(ctx.asyncAssertSuccess(s -> {
      client.connect(server.actualPort(), "localhost").onComplete(ctx.asyncAssertSuccess(connAck -> {
        client.authenticationExchange(MqttAuthenticateReasonCode.SUCCESS, MqttProperties.NO_PROPERTIES)
          .onComplete(ctx.asyncAssertFailure(err -> {
            ctx.assertTrue(err instanceof IllegalStateException);
            ctx.assertTrue(err.getMessage().contains("MQTT 5"));
            done.complete();
          }));
      }));
    }));

    done.awaitSuccess(5000);
  }

  // -------------------------------------------------------------------------

  /**
   * authenticationExchange() on a v5 client that hasn't connected yet must
   * fail with IllegalStateException rather than NPE-ing on a null channel.
   */
  @Test
  public void authenticationExchangeRejectedWhenNotConnected(TestContext ctx) {
    Async done = ctx.async();

    server.endpointHandler(ep -> ep.accept(false));
    server.listen(0).onComplete(ctx.asyncAssertSuccess(s -> {
      MqttClient client = MqttClient.create(vertx, v5Options());
      // Force the client to allocate a context by connecting and immediately disconnecting.
      client.connect(server.actualPort(), "localhost").onComplete(ctx.asyncAssertSuccess(connAck -> {
        client.disconnect().onComplete(ctx.asyncAssertSuccess(v -> {
          client.authenticationExchange(MqttAuthenticateReasonCode.RE_AUTHENTICATE, MqttProperties.NO_PROPERTIES)
            .onComplete(ctx.asyncAssertFailure(err -> {
              ctx.assertTrue(err instanceof IllegalStateException);
              ctx.assertTrue(err.getMessage().contains("not connected"));
              done.complete();
            }));
        }));
      }));
    }));

    done.awaitSuccess(5000);
  }

  // -------------------------------------------------------------------------

  private MqttClientOptions v5Options() {
    MqttClientOptions opts = new MqttClientOptions();
    opts.setVersion(MqttVersion.MQTT_5.protocolLevel());
    return opts;
  }
}
