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
import io.vertx.mqtt.messages.MqttAuthenticationExchangeMessage;
import io.vertx.mqtt.messages.codes.MqttAuthenticateReasonCode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for the MQTT 5.0 enhanced authentication (AUTH) exchange (§4.12).
 *
 * Flow under test:
 *   client --CONNECT(method, data)--> server
 *   server --AUTH(CONTINUE, challenge)--> client
 *   client --AUTH(CONTINUE, response)--> server
 *   server --CONNACK(SUCCESS)--> client
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
    server.close().onComplete(ctx.asyncAssertSuccess(v -> vertx.close().onComplete(ctx.asyncAssertSuccess())));
  }

  // -----------------------------------------------------------------------

  /**
   * Full enhanced-auth handshake: server challenges, client responds, server accepts.
   */
  @Test
  public void enhancedAuthHandshake(TestContext ctx) {
    Async connected = ctx.async();

    final String authMethod = "SCRAM-SHA-256";
    final Buffer clientInitialData = Buffer.buffer("client-initial");
    final Buffer serverChallenge = Buffer.buffer("server-challenge");
    final Buffer clientResponse = Buffer.buffer("client-response");

    server.endpointHandler(endpoint -> {
      // Verify the CONNECT carried authenticationMethod via MQTT5 properties
      MqttProperties connectProps = endpoint.connectProperties();
      MqttProperties.MqttProperty<?> connectAuthMethod =
        connectProps.getProperty(MqttProperties.MqttPropertyType.AUTHENTICATION_METHOD.value());
      ctx.assertNotNull(connectAuthMethod, "CONNECT must carry AUTHENTICATION_METHOD");
      ctx.assertEquals(authMethod, connectAuthMethod.value());

      // Register handler to receive the client's AUTH response
      endpoint.authenticationExchangeHandler(authMsg -> {
        ctx.assertEquals(MqttAuthenticateReasonCode.CONTINUE_AUTHENTICATION, authMsg.reasonCode());
        ctx.assertEquals(authMethod, authMsg.authenticationMethod());
        ctx.assertEquals(clientResponse, authMsg.authenticationData());

        // Auth succeeded — send CONNACK
        endpoint.accept(false);
      });

      // Send AUTH challenge to client
      MqttProperties challengeProps = new MqttProperties();
      challengeProps.add(new MqttProperties.StringProperty(
        MqttProperties.MqttPropertyType.AUTHENTICATION_METHOD.value(), authMethod));
      challengeProps.add(new MqttProperties.BinaryProperty(
        MqttProperties.MqttPropertyType.AUTHENTICATION_DATA.value(), serverChallenge.getBytes()));
      endpoint.authenticationExchange(
        MqttAuthenticationExchangeMessage.create(MqttAuthenticateReasonCode.CONTINUE_AUTHENTICATION, challengeProps));
    });

    startServer(ctx, () -> {
      MqttClientOptions options = new MqttClientOptions();
      options.setVersion(MqttVersion.MQTT_5.protocolLevel());
      options.setAuthenticationMethod(authMethod);
      options.setAuthenticationData(clientInitialData);

      MqttClient client = MqttClient.create(vertx, options);

      // Handle incoming AUTH challenge from server
      client.authenticationExchangeHandler(authMsg -> {
        ctx.assertEquals(MqttAuthenticateReasonCode.CONTINUE_AUTHENTICATION, authMsg.reasonCode());
        ctx.assertEquals(authMethod, authMsg.authenticationMethod());
        ctx.assertEquals(serverChallenge, authMsg.authenticationData());

        // Send client's AUTH response
        MqttProperties responseProps = new MqttProperties();
        responseProps.add(new MqttProperties.StringProperty(
          MqttProperties.MqttPropertyType.AUTHENTICATION_METHOD.value(), authMethod));
        responseProps.add(new MqttProperties.BinaryProperty(
          MqttProperties.MqttPropertyType.AUTHENTICATION_DATA.value(), clientResponse.getBytes()));
        client.authenticationExchange(
          MqttAuthenticationExchangeMessage.create(MqttAuthenticateReasonCode.CONTINUE_AUTHENTICATION, responseProps));
      });

      client.connect(server.actualPort(), "localhost")
        .onComplete(ctx.asyncAssertSuccess(ack -> connected.complete()));
    });

    connected.awaitSuccess(5000);
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
