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

import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.messages.codes.MqttAuthenticateReasonCode;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.client.MqttToken;
import org.eclipse.paho.mqttv5.client.internal.ClientComms;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;

import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * MQTT server testing about server auth
 */
public class Mqtt5ServerAuthTest extends MqttServerBaseTest {

  private static final Logger log = LoggerFactory.getLogger(Mqtt5ServerAuthTest.class);

  private Async async;

  private MqttAuthenticateReasonCode reasonCode;
  private String authMethod;
  private String authData;

  @Before
  public void before(TestContext context) {

    this.setUp(context);
  }

  @After
  public void after(TestContext context) {

    this.tearDown(context);
  }

  @Test
  public void receiveAuth(TestContext context) {
    this.reasonCode = MqttAuthenticateReasonCode.CONTINUE_AUTHENTICATION;
    this.authMethod = "test";
    this.authData = "test";

    this.async = context.async(1);

    try {
      MemoryPersistence persistence = new MemoryPersistence();
      MqttAsyncClient client = new MqttAsyncClient(String.format("tcp://%s:%d", MQTT_SERVER_HOST, MQTT_SERVER_PORT), "12345", persistence);
      client.connect().waitForCompletion();

      MqttProperties props = new MqttProperties();
      props.setAuthenticationMethod(authMethod);
      props.setAuthenticationData(authData.getBytes(StandardCharsets.UTF_8));

      Field senderField = client.getClass().getDeclaredField("comms");
      senderField.setAccessible(true);

      ClientComms comms = (ClientComms) senderField.get(client);
      comms.sendNoWait(new MqttAuthPacket(reasonCode.value(), props), new MqttToken(client.getClientId()));

      log.info("send auth packet");
      this.async.await();

    } catch (MqttException e) {
      context.fail(e);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  protected void endpointHandler(MqttEndpoint endpoint, TestContext context) {

    endpoint.authenticationExchangeHandler(auth -> {
      log.info("recv auth packet");
      MqttAuthenticateReasonCode code = auth.reasonCode();
      String method = auth.authenticationMethod();
      String data = auth.authenticationData().toString();
      context.assertEquals(code, this.reasonCode);
      context.assertTrue(Objects.equals(method, this.authMethod));
      context.assertTrue(Objects.equals(data, this.authData));
      log.info("test auth pass");
      async.countDown();
    });
    endpoint.accept(false);
  }
}
