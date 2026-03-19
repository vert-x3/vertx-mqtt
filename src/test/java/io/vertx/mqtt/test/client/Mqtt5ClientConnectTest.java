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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttVersion;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;
import io.vertx.mqtt.MqttServer;

/**
 * Tests for the MQTT v5 CONNECT packet properties sent by the client.
 * The embedded MqttServer inspects endpoint.connectProperties() to verify
 * that each property is correctly encoded by MqttClientImpl.
 */
@RunWith(VertxUnitRunner.class)
public class Mqtt5ClientConnectTest {

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
   * Connecting with version=5 must result in the server seeing protocol level 5.
   */
  @Test
  public void connectWithVersion5(TestContext ctx) {
    Async serverLatch = ctx.async();
    Async connected = ctx.async();

    server.endpointHandler(endpoint -> {
      ctx.assertEquals((int) MqttVersion.MQTT_5.protocolLevel(), endpoint.protocolVersion());
      ctx.assertEquals(MqttVersion.MQTT_5.protocolName(), endpoint.protocolName());
      endpoint.accept(false);
      serverLatch.complete();
    });

    startServer(ctx, () -> {
      MqttClientOptions options = new MqttClientOptions();
      options.setVersion(MqttVersion.MQTT_5.protocolLevel());

      MqttClient.create(vertx, options)
          .connect(server.actualPort(), "localhost")
          .onComplete(ctx.asyncAssertSuccess(v -> connected.complete()));
    });

    serverLatch.awaitSuccess(5000);
    connected.awaitSuccess(5000);
  }

  /**
   * setSessionExpireInterval must be encoded as SESSION_EXPIRY_INTERVAL in the CONNECT properties.
   */
  @Test
  public void connectWithSessionExpireInterval(TestContext ctx) {
    Long expected = 300l;
    Async serverLatch = ctx.async();

    server.endpointHandler(endpoint -> {
      MqttProperties.MqttProperty<?> prop = endpoint.connectProperties().getProperty(MqttProperties.SESSION_EXPIRY_INTERVAL);
      ctx.assertNotNull(prop);
      ctx.assertEquals(expected, Integer.toUnsignedLong((Integer) prop.value()));
      endpoint.accept(false);
      serverLatch.complete();
    });

    startServer(ctx, () -> {
      MqttClientOptions options = new MqttClientOptions();
      options.setVersion(MqttVersion.MQTT_5.protocolLevel());
      options.setSessionExpireInterval(expected);

      MqttClient.create(vertx, options)
          .connect(server.actualPort(), "localhost");
    });

    serverLatch.awaitSuccess(5000);
  }

  /**
   * setSessionExpireInterval must be encoded as SESSION_EXPIRY_INTERVAL in the CONNECT properties.
   */
  @Test
  public void connectWithMaxSessionExpireInterval(TestContext ctx) {
    Long expected = 4294967295l;
    Async serverLatch = ctx.async();

    server.endpointHandler(endpoint -> {
      MqttProperties.MqttProperty<?> prop = endpoint.connectProperties().getProperty(MqttProperties.SESSION_EXPIRY_INTERVAL);
      ctx.assertNotNull(prop);
      ctx.assertEquals(expected, Integer.toUnsignedLong((Integer) prop.value()));
      endpoint.accept(false);
      serverLatch.complete();
    });

    startServer(ctx, () -> {
      MqttClientOptions options = new MqttClientOptions();
      options.setVersion(MqttVersion.MQTT_5.protocolLevel());
      options.setSessionExpireInterval(expected);

      MqttClient.create(vertx, options)
          .connect(server.actualPort(), "localhost");
    });

    serverLatch.awaitSuccess(5000);
  }

  /**
   * setReceiveMaximum must be encoded as RECEIVE_MAXIMUM in the CONNECT properties.
   */
  @Test
  public void connectWithReceiveMaximum(TestContext ctx) {
    Integer expected = 300;
    Async serverLatch = ctx.async();

    server.endpointHandler(endpoint -> {
      MqttProperties.MqttProperty<?> prop = endpoint.connectProperties().getProperty(MqttProperties.RECEIVE_MAXIMUM);
      ctx.assertNotNull(prop);
      ctx.assertEquals(expected.longValue(), Integer.toUnsignedLong((Integer) prop.value()));
      endpoint.accept(false);
      serverLatch.complete();
    });

    startServer(ctx, () -> {
      MqttClientOptions options = new MqttClientOptions();
      options.setVersion(MqttVersion.MQTT_5.protocolLevel());
      options.setReceiveMaximum(expected);
      options.setTopicAliasMaximum(null); // suppress default so we only see RECEIVE_MAXIMUM

      MqttClient.create(vertx, options)
          .connect(server.actualPort(), "localhost");
    });

    serverLatch.awaitSuccess(5000);
  }

  /**
   * setMaximumPacketSize must be encoded as MAXIMUM_PACKET_SIZE in the CONNECT properties.
   */
  @Test
  public void connectWithMaximumPacketSize(TestContext ctx) {
    Long expected = 65535l;
    Async serverLatch = ctx.async();

    server.endpointHandler(endpoint -> {
      MqttProperties.MqttProperty<?> prop = endpoint.connectProperties().getProperty(MqttProperties.MAXIMUM_PACKET_SIZE);
      ctx.assertNotNull(prop);
      ctx.assertEquals(expected, Integer.toUnsignedLong((Integer) prop.value()));
      endpoint.accept(false);
      serverLatch.complete();
    });

    startServer(ctx, () -> {
      MqttClientOptions options = new MqttClientOptions();
      options.setVersion(MqttVersion.MQTT_5.protocolLevel());
      options.setMaximumPacketSize(expected);

      MqttClient.create(vertx, options)
          .connect(server.actualPort(), "localhost");
    });

    serverLatch.awaitSuccess(5000);
  }

  /**
   * setTopicAliasMaximum must be encoded as TOPIC_ALIAS_MAXIMUM in the CONNECT properties.
   */
  @Test
  public void connectWithTopicAliasMaximum(TestContext ctx) {
    Integer expected = 5;
    Async serverLatch = ctx.async();

    server.endpointHandler(endpoint -> {
      MqttProperties.MqttProperty<?> prop = endpoint.connectProperties().getProperty(MqttProperties.TOPIC_ALIAS_MAXIMUM);
      ctx.assertNotNull(prop);
      ctx.assertEquals(expected.longValue(), Integer.toUnsignedLong((Integer) prop.value()));
      endpoint.accept(false);
      serverLatch.complete();
    });

    startServer(ctx, () -> {
      MqttClientOptions options = new MqttClientOptions();
      options.setVersion(MqttVersion.MQTT_5.protocolLevel());
      options.setTopicAliasMaximum(expected);

      MqttClient.create(vertx, options)
          .connect(server.actualPort(), "localhost");
    });

    serverLatch.awaitSuccess(5000);
  }

  /**
   * All v5 CONNECT properties should be encoded together correctly.
   */
  @Test
  public void connectWithAllProperties(TestContext ctx) {
    Async serverLatch = ctx.async();

    server.endpointHandler(endpoint -> {
      MqttProperties props = endpoint.connectProperties();
      ctx.assertNotNull(props.getProperty(MqttProperties.SESSION_EXPIRY_INTERVAL));
      ctx.assertNotNull(props.getProperty(MqttProperties.RECEIVE_MAXIMUM));
      ctx.assertNotNull(props.getProperty(MqttProperties.MAXIMUM_PACKET_SIZE));
      ctx.assertNotNull(props.getProperty(MqttProperties.TOPIC_ALIAS_MAXIMUM));
      ctx.assertEquals(60l, Integer.toUnsignedLong((Integer) props.getProperty(MqttProperties.SESSION_EXPIRY_INTERVAL).value()));
      ctx.assertEquals(20l, Integer.toUnsignedLong((Integer) props.getProperty(MqttProperties.RECEIVE_MAXIMUM).value()));
      ctx.assertEquals(32768l, Integer.toUnsignedLong((Integer) props.getProperty(MqttProperties.MAXIMUM_PACKET_SIZE).value()));
      ctx.assertEquals(3l, Integer.toUnsignedLong((Integer) props.getProperty(MqttProperties.TOPIC_ALIAS_MAXIMUM).value()));
      endpoint.accept(false);
      serverLatch.complete();
    });

    startServer(ctx, () -> {
      MqttClientOptions options = new MqttClientOptions();
      options.setVersion(MqttVersion.MQTT_5.protocolLevel());
      options.setSessionExpireInterval(60l);
      options.setReceiveMaximum(20);
      options.setMaximumPacketSize(32768l);
      options.setTopicAliasMaximum(3);

      MqttClient.create(vertx, options)
          .connect(server.actualPort(), "localhost");
    });

    serverLatch.awaitSuccess(5000);
  }

  /**
   * setAuthenticationMethod must be encoded as AUTHENTICATION_METHOD in the CONNECT properties.
   */
  @Test
  public void connectWithAuthenticationMethod(TestContext ctx) {
    String expected = "SCRAM-SHA-256";
    Async serverLatch = ctx.async();

    server.endpointHandler(endpoint -> {
      MqttProperties.MqttProperty<?> prop = endpoint.connectProperties().getProperty(MqttProperties.AUTHENTICATION_METHOD);
      ctx.assertNotNull(prop);
      ctx.assertEquals(expected, prop.value());
      endpoint.accept(false);
      serverLatch.complete();
    });

    startServer(ctx, () -> {
      MqttClientOptions options = new MqttClientOptions();
      options.setVersion(MqttVersion.MQTT_5.protocolLevel());
      options.setAuthenticationMethod(expected);

      MqttClient.create(vertx, options)
          .connect(server.actualPort(), "localhost");
    });

    serverLatch.awaitSuccess(5000);
  }

  /**
   * setAuthenticationMethod + setAuthenticationData must both appear in CONNECT properties.
   */
  @Test
  public void connectWithAuthenticationMethodAndData(TestContext ctx) {
    String expectedMethod = "SCRAM-SHA-256";
    byte[] expectedData = new byte[]{0x01, 0x02, 0x03, 0x04};
    Async serverLatch = ctx.async();

    server.endpointHandler(endpoint -> {
      MqttProperties props = endpoint.connectProperties();
      MqttProperties.MqttProperty<?> methodProp = props.getProperty(MqttProperties.AUTHENTICATION_METHOD);
      MqttProperties.MqttProperty<?> dataProp   = props.getProperty(MqttProperties.AUTHENTICATION_DATA);
      ctx.assertNotNull(methodProp);
      ctx.assertEquals(expectedMethod, methodProp.value());
      ctx.assertNotNull(dataProp);
      ctx.assertEquals(Buffer.buffer(expectedData), Buffer.buffer((byte[]) dataProp.value()));
      endpoint.accept(false);
      serverLatch.complete();
    });

    startServer(ctx, () -> {
      MqttClientOptions options = new MqttClientOptions();
      options.setVersion(MqttVersion.MQTT_5.protocolLevel());
      options.setAuthenticationMethod(expectedMethod);
      options.setAuthenticationData(Buffer.buffer(expectedData));

      MqttClient.create(vertx, options)
          .connect(server.actualPort(), "localhost");
    });

    serverLatch.awaitSuccess(5000);
  }

  // -----------------------------------------------------------------------
  // CONNACK properties received by the client
  // -----------------------------------------------------------------------

  /**
   * Client sets REQUEST_RESPONSE_INFORMATION=1 in CONNECT.
   * Server replies with RESPONSE_INFORMATION in CONNACK.
   * The connect Future result (MqttConnAckMessage) must expose the value
   * via responseInformation().
   */
  @Test
  public void connackResponseInformation(TestContext ctx) {
    String expected = "responses/client-id-1";
    Async done = ctx.async();

    server.endpointHandler(endpoint -> {
      // Verify the client actually asked for response information
      MqttProperties.MqttProperty<?> reqProp =
        endpoint.connectProperties().getProperty(MqttProperties.REQUEST_RESPONSE_INFORMATION);
      ctx.assertNotNull(reqProp);
      ctx.assertEquals(1, reqProp.value());

      // Include RESPONSE_INFORMATION in CONNACK
      MqttProperties connAckProps = new MqttProperties();
      connAckProps.add(new MqttProperties.StringProperty(MqttProperties.RESPONSE_INFORMATION, expected));
      endpoint.accept(false, connAckProps);
    });

    startServer(ctx, () -> {
      MqttClientOptions options = new MqttClientOptions();
      options.setVersion(MqttVersion.MQTT_5.protocolLevel());
      options.setRequestResponseInformation(true);

      MqttClient.create(vertx, options)
        .connect(server.actualPort(), "localhost")
        .onComplete(ctx.asyncAssertSuccess(ack -> {
          ctx.assertEquals(expected, ack.responseInformation());
          done.complete();
        }));
    });

    done.awaitSuccess(5000);
  }

  /**
   * When the client does NOT request response information (REQUEST_RESPONSE_INFORMATION absent
   * or 0), the server MUST NOT include RESPONSE_INFORMATION in CONNACK.
   * Verify responseInformation() returns null.
   */
  @Test
  public void connackResponseInformationAbsentWhenNotRequested(TestContext ctx) {
    Async done = ctx.async();

    server.endpointHandler(endpoint -> {
      // Send CONNACK with no RESPONSE_INFORMATION (server respects the spec)
      endpoint.accept(false);
    });

    startServer(ctx, () -> {
      MqttClientOptions options = new MqttClientOptions();
      options.setVersion(MqttVersion.MQTT_5.protocolLevel());
      // requestResponseInformation is not set (defaults to null / false)

      MqttClient.create(vertx, options)
        .connect(server.actualPort(), "localhost")
        .onComplete(ctx.asyncAssertSuccess(ack -> {
          ctx.assertNull(ack.responseInformation());
          done.complete();
        }));
    });

    done.awaitSuccess(5000);
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
