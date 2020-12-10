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

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;
import io.vertx.mqtt.MqttServer;
import io.vertx.mqtt.MqttTopicSubscription;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

/**
 * MQTT client keep alive tests using a Vert.x MQTT server to accommodate testing.
 */
@RunWith(VertxUnitRunner.class)
public class MqttClientKeepAliveTest {

  private Vertx vertx;
  private MqttServer server;

  private void startServer(TestContext ctx) {
    Async async = ctx.async();
    server.listen(ctx.asyncAssertSuccess(server -> async.complete()));
    async.awaitSuccess(10000);
  }

  @Before
  public void before(TestContext ctx) {
    vertx = Vertx.vertx();
    server = MqttServer.create(vertx);
  }

  @After
  public void after(TestContext ctx) {
    server.close(ctx.asyncAssertSuccess(v -> {
      vertx.close(ctx.asyncAssertSuccess());
    }));
  }

  @Test
  public void autoKeepAlive(TestContext ctx) {
    AtomicInteger pings = new AtomicInteger();
    server.endpointHandler(endpoint -> {
      endpoint.accept(false);
      endpoint.autoKeepAlive(true);
      endpoint.pingHandler(v -> {
        pings.incrementAndGet();
      });
    });
    startServer(ctx);
    MqttClientOptions options = new MqttClientOptions();
    options.setAutoKeepAlive(true);
    options.setKeepAliveInterval(1);
    MqttClient client = MqttClient.create(vertx, options);
    client.connect(MqttClientOptions.DEFAULT_PORT, MqttClientOptions.DEFAULT_HOST, ctx.asyncAssertSuccess(ack -> {
      Async async = ctx.async();
      AtomicInteger pongs = new AtomicInteger();
      client.pingResponseHandler(v -> {
        if (pongs.incrementAndGet() == 4) {
          client.disconnect();
        }
      });
      client.closeHandler(v -> {
        assertEquals(4, pings.get());
        assertEquals(4, pongs.get());
        async.complete();
      });
    }));
  }

  @Test
  public void clientWillDisconnectOnMissingPingResponse(TestContext ctx) {
    AtomicInteger pings = new AtomicInteger();
    server.endpointHandler(endpoint -> {
      endpoint.autoKeepAlive(false); // Tell the server not to respond to PINGREQ
      endpoint.accept(false);
      endpoint.pingHandler(v -> pings.incrementAndGet());
    });
    startServer(ctx);
    MqttClientOptions options = new MqttClientOptions();
    options.setKeepAliveInterval(1);
    MqttClient client = MqttClient.create(vertx, options);
    client.connect(MqttClientOptions.DEFAULT_PORT, MqttClientOptions.DEFAULT_HOST, ctx.asyncAssertSuccess(ack -> {
      Async async = ctx.async();
      client.closeHandler(v -> {
        assertEquals(2, pings.get());
        async.complete();
      });
    }));
  }

  @Test
  public void clientWillDisconnectOnMissingManualPingResponse(TestContext ctx) {
    AtomicInteger pings = new AtomicInteger();
    server.endpointHandler(endpoint -> {
      endpoint.accept(false);
      endpoint.autoKeepAlive(false);
      endpoint.pingHandler(v -> pings.incrementAndGet());
    });
    startServer(ctx);
    MqttClientOptions options = new MqttClientOptions();
    options.setKeepAliveInterval(2);
    options.setAutoKeepAlive(false);
    MqttClient client = MqttClient.create(vertx, options);
    client.connect(MqttClientOptions.DEFAULT_PORT, MqttClientOptions.DEFAULT_HOST, ctx.asyncAssertSuccess(ack -> {
      Async async = ctx.async();
      AtomicInteger pongs = new AtomicInteger();
      client.pingResponseHandler(v -> {
        pongs.incrementAndGet();
      });
      client.ping();
      client.closeHandler(v -> {
        assertEquals(0, pongs.get());
        assertEquals(1, pings.get());
        async.complete();
      });
    }));
  }

  @Test
  public void clientSendingRegularMessageDoesNotPreventClientPings(TestContext ctx) {
    AtomicInteger pings = new AtomicInteger();
    AtomicInteger messages = new AtomicInteger();
    server.endpointHandler(endpoint -> {
      endpoint.accept(false);
      endpoint.pingHandler(v -> {
        pings.incrementAndGet();
      });
      endpoint.publishHandler(msg -> {
        if (messages.incrementAndGet() == 4) {
          endpoint.close();
        }
      });
    });
    startServer(ctx);
    MqttClientOptions options = new MqttClientOptions();
    options.setKeepAliveInterval(2);
    options.setAutoKeepAlive(true);
    MqttClient client = MqttClient.create(vertx, options);
    client.connect(MqttClientOptions.DEFAULT_PORT, MqttClientOptions.DEFAULT_HOST, ctx.asyncAssertSuccess(ack -> {
      Async async = ctx.async();
      long timerID = vertx.setPeriodic(500, id -> {
        client.publish("greetings", Buffer.buffer("hello"), MqttQoS.AT_MOST_ONCE, false, false);
      });
      AtomicInteger pongs = new AtomicInteger();
      client.pingResponseHandler(v -> pongs.incrementAndGet());
      client.closeHandler(v -> {
        vertx.cancelTimer(timerID);
        ctx.assertEquals(0, pings.get());
        ctx.assertEquals(0, pongs.get());
        async.complete();
      });
    }));
  }

  @Test
  public void serverSendingRegularMessageDoesNotPreventClientPings(TestContext ctx) {
    AtomicInteger pings = new AtomicInteger();
    server.endpointHandler(endpoint -> {
      endpoint.accept(false);
      endpoint.subscribeHandler(subscribe -> {
        List<MqttQoS> grantedQosLevels = subscribe
          .topicSubscriptions()
          .stream()
          .map(MqttTopicSubscription::qualityOfService)
          .collect(Collectors.toList());
        endpoint.subscribeAcknowledge(subscribe.messageId(), grantedQosLevels);
        long id = vertx.setPeriodic(500, handler -> {
          endpoint.publish(subscribe.topicSubscriptions().get(0).topicName(),
            Buffer.buffer("hello"),
            subscribe.topicSubscriptions().get(0).qualityOfService(), false, false);
        });
        endpoint.unsubscribeHandler(unsub -> {
          vertx.cancelTimer(id);
          endpoint.close();
        });
      });
      endpoint.pingHandler(v -> {
        pings.incrementAndGet();
      });
    });
    startServer(ctx);
    MqttClientOptions options = new MqttClientOptions();
    options.setKeepAliveInterval(1);
    options.setAutoKeepAlive(true);
    MqttClient client = MqttClient.create(vertx, options);
    client.connect(MqttClientOptions.DEFAULT_PORT, MqttClientOptions.DEFAULT_HOST, ctx.asyncAssertSuccess(ack -> {
      Async async = ctx.async();
      AtomicInteger pongs = new AtomicInteger();
      client.subscribe("topic/topic", 0);
      AtomicInteger count = new AtomicInteger();
      client.publishHandler(msg -> {
        if (count.incrementAndGet() == 5) {
          client.unsubscribe("topic/topic");
        }
      });
      client.pingResponseHandler(v -> pongs.incrementAndGet());
      client.closeHandler(v -> {
        ctx.assertTrue(pings.get() > 0);
        ctx.assertTrue(pongs.get() > 0);
        async.complete();
      });
    }));
  }
}
