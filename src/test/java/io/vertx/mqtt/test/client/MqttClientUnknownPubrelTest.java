/*
 * Copyright (c) 2026 IoT Invent GmbH
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
 *
 * AI Disclosure: This file was largely AI-generated. The AI-generated
 * portions are made available under CC0-1.0 and not subject to the
 * project's licence. The human contributor has reviewed and verified
 * that the code is correct.
 *
 * SPDX-License-Identifier: Apache-2.0 AND CC0-1.0
 * Assisted-by: Anthropic Claude Opus 5.5 (claude-opus-5-5)
 */

package io.vertx.mqtt.test.client;

import io.netty.handler.codec.mqtt.MqttVersion;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;
import io.vertx.mqtt.MqttServer;
import io.vertx.mqtt.MqttServerOptions;
import io.vertx.mqtt.messages.codes.MqttPubCompReasonCode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * The client answers a PUBREL for a packet id it holds no QoS 2 state for with a PUBCOMP. That happens after a
 * reconnect with a kept session: the server re-sends the PUBREL whose PUBREC was sent on the previous connection.
 */
@RunWith(VertxUnitRunner.class)
public class MqttClientUnknownPubrelTest {

  private static final int UNKNOWN_PACKET_ID = 42;

  private Vertx vertx;
  private MqttServer server;

  @Before
  public void before() {
    vertx = Vertx.vertx();
    server = MqttServer.create(vertx, new MqttServerOptions().setPort(0));
  }

  @After
  public void after(TestContext ctx) {
    server.close().onComplete(ctx.asyncAssertSuccess(v -> vertx.close().onComplete(ctx.asyncAssertSuccess())));
  }

  /**
   * MQTT 3.1.1 section 4.3.3: the receiver of a PUBREL responds with a PUBCOMP.
   */
  @Test
  public void unknownPubrelIsCompletedMqtt311(TestContext ctx) {
    Async completed = ctx.async();
    server.endpointHandler(endpoint -> {
      endpoint.publishCompletionHandler(id -> {
        ctx.assertEquals(UNKNOWN_PACKET_ID, id);
        completed.complete();
      });
      endpoint.accept(false);
      endpoint.publishRelease(UNKNOWN_PACKET_ID);
    });
    int port = server.listen().await().actualPort();

    MqttClient client = MqttClient.create(vertx, new MqttClientOptions());
    client.connect(port, "localhost").onComplete(ctx.asyncAssertSuccess());
    completed.awaitSuccess(10000);
  }

  /**
   * MQTT 5.0 section 3.7.2.1: PUBCOMP with reason code 0x92 (Packet Identifier not found), which is not an error
   * during recovery.
   */
  @Test
  public void unknownPubrelIsCompletedWithPacketIdentifierNotFoundMqtt5(TestContext ctx) {
    Async completed = ctx.async();
    server.endpointHandler(endpoint -> {
      endpoint.publishCompletionMessageHandler(pubcomp -> {
        ctx.assertEquals(UNKNOWN_PACKET_ID, pubcomp.messageId());
        ctx.assertEquals(MqttPubCompReasonCode.PACKET_IDENTIFIER_NOT_FOUND, pubcomp.code());
        completed.complete();
      });
      endpoint.accept(false);
      endpoint.publishRelease(UNKNOWN_PACKET_ID);
    });
    int port = server.listen().await().actualPort();

    MqttClientOptions options = new MqttClientOptions();
    options.setVersion(MqttVersion.MQTT_5.protocolLevel());
    MqttClient client = MqttClient.create(vertx, options);
    client.connect(port, "localhost").onComplete(ctx.asyncAssertSuccess());
    completed.awaitSuccess(10000);
  }
}
