/*
 * Copyright 2018 Bosch Software Innovations GmbH
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

package io.vertx.mqtt.impl;

import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;

import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.impl.NetSocketInternal;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.mqtt.MqttAuth;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.MqttServerOptions;
import io.vertx.mqtt.messages.MqttPublishMessage;

@RunWith(VertxUnitRunner.class)
public class MqttEndpointImplTest {

    @Rule
    public Timeout timeout = Timeout.seconds(3);

    private static Vertx vertx;

    @BeforeClass
    public static void setUp() {
        vertx = Vertx.vertx();
    }

    @AfterClass
    public static void shutDown(TestContext ctx) {
        vertx.close(ctx.asyncAssertSuccess());
    }

    @Test
    public void testServerAcceptsPublishedMessage(TestContext ctx) {

      final Context serverContext = vertx.getOrCreateContext();
      final Context utilityContext = vertx.getOrCreateContext();
      assertFalse(serverContext == utilityContext);

      Async connackSent = ctx.async();
      Async messageProcessed = ctx.async();

      NetSocketInternal con = mock(NetSocketInternal.class);
      // writeMessage will be invoked by the endpoint when
      // it accepts a connection request
      when(con.writeMessage(any())).then(invocation -> {
          connackSent.complete();
          return con;
      });

      final Handler<MqttPublishMessage> publishHandler = pub -> {
          System.out.println(String.format("processing message on %s context",
                  Vertx.currentContext() == serverContext ? "server" : "utility"));
          messageProcessed.complete();
      };
      final Handler<MqttEndpoint> endpointHandler = ep -> {

          System.out.println(String.format("processing CONNECT on %s context",
                  Vertx.currentContext() == serverContext ? "server" : "utility"));

          ctx.assertTrue(Vertx.currentContext() == serverContext);
          ep.publishAutoAck(true);
          ep.publishHandler(publishHandler);

          utilityContext.runOnContext(invokeUtility -> {
              System.out.println(String.format("sending CONNACK on %s context",
                      Vertx.currentContext() == serverContext ? "server" : "utility"));
              ctx.assertTrue(Vertx.currentContext() == utilityContext);
              // we send the CONNACK while running on the utility context
              ep.accept(false);
          });
      };

      MqttServerConnection serverCon = new MqttServerConnection(con, new MqttServerOptions());
      serverCon.init(endpointHandler, t -> {});
      MqttEndpointImpl endpoint = new MqttEndpointImpl(
              con,
              "client-id",
              new MqttAuth("client", "password"),
              null,
              true,
              3,
              "MQTT",
              10);

      // WHEN a client connects to the server
      serverContext.runOnContext(connect -> {
          // this will eventually result in the writeMessage() method
          // being invoked on the connection
          endpointHandler.handle(endpoint);
      });
      // and publishes its first message after it
      // has received the server's CONNACK
      connackSent.await();
      System.out.println("received CONNACK from server, now publishing message...");
      MqttPublishMessage msg = mock(MqttPublishMessage.class);
      serverContext.runOnContext(publish -> {
          System.out.println(String.format("receiving PUBLISH from client on %s context",
                  Vertx.currentContext() == serverContext ? "server" : "utility"));
          serverCon.handlePublish(msg);
      });

      // THEN the message gets processed
      messageProcessed.await();
      // and the connection is not closed
      verify(con, never()).close();
    }
}
