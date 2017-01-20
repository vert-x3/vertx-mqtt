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

package io.vertx.mqtt.test;

import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.mqtt.MqttEndpoint;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * MQTT server testing about clients connection
 */
@RunWith(VertxUnitRunner.class)
public class MqttConnectionTest extends MqttBaseTest {

  private MqttConnectReturnCode expectedReturnCode;

  private static final String MQTT_USERNAME = "username";
  private static final String MQTT_PASSWORD = "password";

  private MqttEndpoint endpoint;

  @Before
  public void before(TestContext context) {

    this.setUp(context);
  }

  @After
  public void after(TestContext context) {

    this.tearDown(context);
  }

  @Test
  public void accepted(TestContext context) {

    this.expectedReturnCode = MqttConnectReturnCode.CONNECTION_ACCEPTED;

    try {
      MemoryPersistence persistence = new MemoryPersistence();
      MqttClient client = new MqttClient(String.format("tcp://%s:%d", MQTT_SERVER_HOST, MQTT_SERVER_PORT), "12345", persistence);
      client.connect();
      context.assertTrue(true);
    } catch (MqttException e) {
      context.assertTrue(false);
      e.printStackTrace();
    }
  }

  @Test
  public void refusedIdentifierRejected(TestContext context) {

    this.expectedReturnCode = MqttConnectReturnCode.CONNECTION_REFUSED_IDENTIFIER_REJECTED;

    try {
      MemoryPersistence persistence = new MemoryPersistence();
      MqttClient client = new MqttClient(String.format("tcp://%s:%d", MQTT_SERVER_HOST, MQTT_SERVER_PORT), "12345", persistence);
      client.connect();
      context.assertTrue(false);
    } catch (MqttException e) {
      context.assertTrue(e.getReasonCode() == MqttException.REASON_CODE_INVALID_CLIENT_ID);
      e.printStackTrace();
    }
  }

  @Test
  public void refusedServerUnavailable(TestContext context) {

    this.expectedReturnCode = MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE;

    try {
      MemoryPersistence persistence = new MemoryPersistence();
      MqttClient client = new MqttClient(String.format("tcp://%s:%d", MQTT_SERVER_HOST, MQTT_SERVER_PORT), "12345", persistence);
      client.connect();
      context.assertTrue(false);
    } catch (MqttException e) {
      context.assertTrue(e.getReasonCode() == MqttException.REASON_CODE_BROKER_UNAVAILABLE);
      e.printStackTrace();
    }
  }

  @Test
  public void refusedBadUsernamePassword(TestContext context) {

    this.expectedReturnCode = MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD;

    try {
      MemoryPersistence persistence = new MemoryPersistence();
      MqttConnectOptions options = new MqttConnectOptions();
      options.setUserName("wrong_username");
      options.setPassword("wrong_password".toCharArray());
      MqttClient client = new MqttClient(String.format("tcp://%s:%d", MQTT_SERVER_HOST, MQTT_SERVER_PORT), "12345", persistence);
      client.connect(options);
      context.assertTrue(false);
    } catch (MqttException e) {
      context.assertTrue(e.getReasonCode() == MqttException.REASON_CODE_FAILED_AUTHENTICATION);
      e.printStackTrace();
    }
  }

  @Test
  public void refusedNotAuthorized(TestContext context) {

    this.expectedReturnCode = MqttConnectReturnCode.CONNECTION_REFUSED_NOT_AUTHORIZED;

    try {
      MemoryPersistence persistence = new MemoryPersistence();
      MqttClient client = new MqttClient(String.format("tcp://%s:%d", MQTT_SERVER_HOST, MQTT_SERVER_PORT), "12345", persistence);
      client.connect();
      context.assertTrue(false);
    } catch (MqttException e) {
      context.assertTrue(e.getReasonCode() == MqttException.REASON_CODE_NOT_AUTHORIZED);
      e.printStackTrace();
    }
  }

  @Test
  public void refusedUnacceptableProtocolVersion(TestContext context) {

    this.expectedReturnCode = MqttConnectReturnCode.CONNECTION_REFUSED_UNACCEPTABLE_PROTOCOL_VERSION;

    try {
      MemoryPersistence persistence = new MemoryPersistence();
      MqttConnectOptions options = new MqttConnectOptions();
      // trying the old 3.1
      options.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1);
      MqttClient client = new MqttClient(String.format("tcp://%s:%d", MQTT_SERVER_HOST, MQTT_SERVER_PORT), "12345", persistence);
      client.connect(options);
      context.assertTrue(false);
    } catch (MqttException e) {
      context.assertTrue(e.getReasonCode() == MqttException.REASON_CODE_INVALID_PROTOCOL_VERSION);
      e.printStackTrace();
    }
  }

  @Test
  public void connectionAlreadyAccepted(TestContext context) {

    this.expectedReturnCode = MqttConnectReturnCode.CONNECTION_ACCEPTED;

    try {
      MemoryPersistence persistence = new MemoryPersistence();
      MqttClient client = new MqttClient(String.format("tcp://%s:%d", MQTT_SERVER_HOST, MQTT_SERVER_PORT), "12345", persistence);
      client.connect();
      // try to accept a connection already accepted
      this.endpoint.accept(false);
      context.assertTrue(false);
    } catch (MqttException e) {
      context.assertTrue(false);
      e.printStackTrace();
    } catch (IllegalStateException e) {
      context.assertTrue(true);
      e.printStackTrace();
    }
  }

  @Override
  protected void endpointHandler(MqttEndpoint endpoint) {

    MqttConnectReturnCode returnCode = this.expectedReturnCode;

    switch (this.expectedReturnCode) {

      case CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD:

        returnCode =
          (endpoint.auth().userName().equals(MQTT_USERNAME) &&
            endpoint.auth().password().equals(MQTT_PASSWORD)) ?
            MqttConnectReturnCode.CONNECTION_ACCEPTED :
            MqttConnectReturnCode.CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD;
        break;

      case CONNECTION_REFUSED_UNACCEPTABLE_PROTOCOL_VERSION:

        returnCode = endpoint.protocolVersion() == MqttConnectOptions.MQTT_VERSION_3_1_1 ?
          MqttConnectReturnCode.CONNECTION_ACCEPTED :
          MqttConnectReturnCode.CONNECTION_REFUSED_UNACCEPTABLE_PROTOCOL_VERSION;
        break;
    }

    if (returnCode == MqttConnectReturnCode.CONNECTION_ACCEPTED) {
      endpoint.accept(false);
    } else {
      endpoint.reject(returnCode);
    }

    this.endpoint = endpoint;
  }
}
