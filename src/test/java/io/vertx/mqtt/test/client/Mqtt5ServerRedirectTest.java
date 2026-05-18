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

import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttVersion;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;
import io.vertx.mqtt.MqttServer;
import io.vertx.mqtt.messages.codes.MqttDisconnectReasonCode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for MQTT 5.0 Server Redirection (SERVER_REFERENCE property).
 *
 * Scenarios:
 *   1. CONNACK with SERVER_REFERENCE and connection refused → client auto-redirects,
 *      connect() Future succeeds against the target server.
 *   2. Server sends DISCONNECT with SERVER_REFERENCE after connection is established →
 *      client auto-reconnects to the target server transparently.
 *   3. autoServerRedirect=false → redirect is NOT performed; connect() Future fails.
 *   4. SERVER_REFERENCE with a comma-separated list → client picks one at random.
 */
@RunWith(VertxUnitRunner.class)
public class Mqtt5ServerRedirectTest {

  private Vertx vertx;
  private MqttServer server1;
  private MqttServer server2;

  @Before
  public void before() {
    vertx = Vertx.vertx();
    server1 = MqttServer.create(vertx);
    server2 = MqttServer.create(vertx);
  }

  @After
  public void after(TestContext ctx) {
    server1.close(ctx.asyncAssertSuccess(v1 ->
      server2.close(ctx.asyncAssertSuccess(v2 ->
        vertx.close(ctx.asyncAssertSuccess())))));
  }

  // -------------------------------------------------------------------------

  /**
   * server1 refuses connection (SERVER_UNAVAILABLE) and includes a SERVER_REFERENCE
   * pointing to server2.  server2 accepts.
   * The client's connect() Future must eventually succeed against server2.
   */
  @Test
  public void connackRedirectToNewServer(TestContext ctx) {
    Async done = ctx.async();

    server2.endpointHandler(ep -> {
      ep.accept(false);
      done.complete();
    });

    // Start server2 first so its port is known, then configure server1 to redirect to it
    server2.listen(0).onComplete(ctx.asyncAssertSuccess(v2 -> {
      server1.endpointHandler(ep -> {
        MqttProperties props = new MqttProperties();
        props.add(new MqttProperties.StringProperty(
          MqttProperties.MqttPropertyType.SERVER_REFERENCE.value(), "localhost:" + server2.actualPort()));
        ep.reject(MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE, props);
      });

      server1.listen(0).onComplete(ctx.asyncAssertSuccess(v1 -> {
        MqttClient client = MqttClient.create(vertx, v5Options(true));
        client.connect(server1.actualPort(), "localhost")
          .onComplete(ctx.asyncAssertSuccess());
      }));
    }));

    done.awaitSuccess(5000);
  }

  /**
   * server1 accepts the connection but immediately sends DISCONNECT with SERVER_REFERENCE
   * pointing to server2.  The client must reconnect to server2 transparently.
   */
  @Test
  public void disconnectRedirectToNewServer(TestContext ctx) {
    Async done = ctx.async();

    server2.endpointHandler(ep -> {
      ep.accept(false);
      done.complete();
    });

    server2.listen(0).onComplete(ctx.asyncAssertSuccess(v2 -> {
      server1.endpointHandler(ep -> {
        ep.accept(false);
        MqttProperties props = new MqttProperties();
        props.add(new MqttProperties.StringProperty(
          MqttProperties.MqttPropertyType.SERVER_REFERENCE.value(), "localhost:" + server2.actualPort()));
        ep.disconnect(MqttDisconnectReasonCode.USE_ANOTHER_SERVER, props);
      });

      server1.listen(0).onComplete(ctx.asyncAssertSuccess(v1 -> {
        MqttClient client = MqttClient.create(vertx, v5Options(true));
        client.connect(server1.actualPort(), "localhost")
          .onComplete(ctx.asyncAssertSuccess());
      }));
    }));

    done.awaitSuccess(5000);
  }

  /**
   * When autoServerRedirect=false the client must NOT follow the SERVER_REFERENCE
   * in a failed CONNACK and the connect() Future must fail.
   */
  @Test
  public void connackRedirectDisabled(TestContext ctx) {
    Async done = ctx.async();

    server2.endpointHandler(ep -> {
      ep.accept(false);
      ctx.fail("Client must NOT have connected to server2");
    });

    server2.listen(0).onComplete(ctx.asyncAssertSuccess(v2 -> {
      server1.endpointHandler(ep -> {
        MqttProperties props = new MqttProperties();
        props.add(new MqttProperties.StringProperty(
          MqttProperties.MqttPropertyType.SERVER_REFERENCE.value(), "localhost:" + server2.actualPort()));
        ep.reject(MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE, props);
      });

      server1.listen(0).onComplete(ctx.asyncAssertSuccess(v1 -> {
        MqttClient client = MqttClient.create(vertx, v5Options(false)); // redirect disabled
        client.connect(server1.actualPort(), "localhost")
          .onComplete(ar -> {
            ctx.assertTrue(ar.failed());
            done.complete();
          });
      }));
    }));

    done.awaitSuccess(5000);
  }

  /**
   * SERVER_REFERENCE contains a comma-separated list of two servers; the client
   * must pick one at random and successfully connect to it.
   */
  @Test
  public void connackRedirectFromList(TestContext ctx) {
    Async done = ctx.async();
    MqttServer server3 = MqttServer.create(vertx);

    server2.endpointHandler(ep -> { ep.accept(false); done.complete(); });
    server3.endpointHandler(ep -> { ep.accept(false); done.complete(); });

    // Start server2 and server3 in parallel, then server1
    CompositeFuture.all(server2.listen(0), server3.listen(0))
      .onComplete(ctx.asyncAssertSuccess(v -> {
        server1.endpointHandler(ep -> {
          MqttProperties props = new MqttProperties();
          props.add(new MqttProperties.StringProperty(
            MqttProperties.MqttPropertyType.SERVER_REFERENCE.value(),
            "localhost:" + server2.actualPort() + ", localhost:" + server3.actualPort()));
          ep.reject(MqttConnectReturnCode.CONNECTION_REFUSED_SERVER_UNAVAILABLE, props);
        });

        server1.listen(0).onComplete(ctx.asyncAssertSuccess(v1 -> {
          MqttClient client = MqttClient.create(vertx, v5Options(true));
          client.connect(server1.actualPort(), "localhost")
            .onComplete(ctx.asyncAssertSuccess());
        }));
      }));

    done.awaitSuccess(5000);
    server3.close();
  }

  // -------------------------------------------------------------------------

  private MqttClientOptions v5Options(boolean autoServerRedirect) {
    MqttClientOptions opts = new MqttClientOptions();
    opts.setVersion(MqttVersion.MQTT_5.protocolLevel());
    opts.setAutoServerRedirect(autoServerRedirect);
    return opts;
  }
}
