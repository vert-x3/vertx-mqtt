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

package io.vertx.mqtt.test.server;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.MqttServerOptions;
import io.vertx.test.proxy.HAProxy;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * MQTT server testing about clients connection
 */
@RunWith(VertxUnitRunner.class)
public class MqttServerWithProxyConnectionTest extends MqttServerBaseTest {

  private static final Logger log = LoggerFactory
    .getLogger(MqttServerWithProxyConnectionTest.class);
  private static final String MQTT_USERNAME = "username";
  private static final String MQTT_PASSWORD = "password";
  private static final int MQTT_TIMEOUT_ON_CONNECT = 5;
  private static final int mockRemotePort = 12345;
  private static final String mockRemoteIp = "1.1.1.1";
  private static final int mockLocalPort = 54321;
  private static final String mockLocalIp = "10.10.10.10";
  private static final int haproxyDefaultPort = 11080;
  private MqttEndpoint endpoint;
  private HAProxy haProxy;

  @Before
  public void before(TestContext context) throws Exception {

    MqttServerOptions options = new MqttServerOptions().setUseProxyProtocol(true);
    options.setTimeoutOnConnect(MQTT_TIMEOUT_ON_CONNECT);

    this.setUp(context, options);
  }

  private void createHaproxy(HaproxyVersion version) throws Exception {
    haProxy = new HAProxy(
      SocketAddress.inetSocketAddress(MQTT_SERVER_PORT, MQTT_SERVER_HOST), version.header);
    haProxy.start(vertx);
    System.out.println(String.format("haproxy server with %s started", version.name()));
  }


  @After
  public void after(TestContext context) {
    this.tearDown(context);
    haProxy.stop();
    System.out.println("haproxy server stopped");
  }

  @Test
  public void testHaproxyV1TCP4(TestContext context) {
    try {
      createHaproxy(HaproxyVersion.V1_TCP4);

      MemoryPersistence persistence = new MemoryPersistence();
      MqttClient client = new MqttClient(
        String.format("tcp://%s:%d", MQTT_SERVER_HOST, haproxyDefaultPort), "12345", persistence);
      client.connect();

      System.out.println("client remoteAddress: " + endpoint.remoteAddress());

      context.assertEquals(endpoint.remoteAddress().host(), mockRemoteIp);
      context.assertEquals(endpoint.remoteAddress().port(), mockRemotePort);

      client.disconnect();
    } catch (Exception e) {
      context.fail(e);
    }
  }

  @Test
  public void testHaproxyV2TCP4(TestContext context) {
    try {
      createHaproxy(HaproxyVersion.V2_TCP4);

      MemoryPersistence persistence = new MemoryPersistence();
      MqttClient client = new MqttClient(
        String.format("tcp://%s:%d", MQTT_SERVER_HOST, haproxyDefaultPort), "12345", persistence);
      client.connect();

      System.out.println("client remoteAddress: " + endpoint.remoteAddress());

      context.assertEquals(endpoint.remoteAddress().host(), mockRemoteIp);
      context.assertEquals(endpoint.remoteAddress().port(), mockRemotePort);

      client.disconnect();
    } catch (Exception e) {
      context.fail(e);
    }
  }


  @Override
  protected void endpointHandler(MqttEndpoint endpoint, TestContext context) {
    super.endpointHandler(endpoint, context);
    this.endpoint = endpoint;
  }

  public enum HaproxyVersion {
    /**
     * version1 tcp4
     */
    V1_TCP4(HAProxy
      .createVersion2TCP4ProtocolHeader(
        SocketAddress.inetSocketAddress(mockRemotePort, mockRemoteIp),
        SocketAddress.inetSocketAddress(mockLocalPort, mockLocalIp))),

    /**
     * version2 tcp4
     */
    V2_TCP4(HAProxy
      .createVersion2TCP4ProtocolHeader(
        SocketAddress.inetSocketAddress(mockRemotePort, mockRemoteIp),
        SocketAddress.inetSocketAddress(mockLocalPort, mockLocalIp)));

    private final Buffer header;

    HaproxyVersion(Buffer header) {
      this.header = header;
    }

  }
}

