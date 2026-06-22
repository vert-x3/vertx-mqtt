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

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.mqtt.MqttConnectPayload;
import io.netty.handler.codec.mqtt.MqttConnectVariableHeader;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import io.netty.handler.codec.mqtt.MqttFixedHeader;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageFactory;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttPublishVariableHeader;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttVersion;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;
import io.vertx.mqtt.MqttServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Tests for MQTT 5.0 topic alias handling in both directions:
 *   - client → server  (server-side decoding in MqttServerConnection)
 *   - server → client  (client-side decoding in MqttClientImpl)
 *
 * The five scenarios mirror the MQTT 5.0 spec §3.3.2.3.4:
 *   1. Alias defined correctly (happy path)
 *   2. Alias used without prior definition → protocol error
 *   3. Alias value exceeds declared maximum → protocol error (server→client direction)
 *   4. Alias overwrite is legal
 *   5. Empty topic + undefined alias → close connection (important edge case)
 */
@RunWith(VertxUnitRunner.class)
public class Mqtt5TopicAliasHandlingTest {

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

  // ======================================================================
  // Test 1 – Alias definito correttamente  (client → server)
  //
  //   PUBLISH topic="a/b/c" alias=1   → server stores mapping 1 → "a/b/c"
  //   PUBLISH topic=""      alias=1   → server resolves to "a/b/c"
  //   Both publishHandler invocations must see topicName = "a/b/c"
  // ======================================================================
  @Test
  public void test1_aliasDefinedCorrectly(TestContext ctx) {
    Async first  = ctx.async();
    Async second = ctx.async();
    AtomicInteger count = new AtomicInteger();

    server.endpointHandler(endpoint -> {
      // Server advertises TOPIC_ALIAS_MAXIMUM=10 → client will auto-assign aliases
      endpoint.accept(false, buildConnAckProps(10));
      endpoint.publishHandler(msg -> {
        int n = count.incrementAndGet();
        if (n == 1) {
          ctx.assertEquals("a/b/c", msg.topicName());
          first.complete();
        } else if (n == 2) {
          // Alias must have been resolved server-side; handler sees the real topic
          ctx.assertEquals("a/b/c", msg.topicName());
          second.complete();
        }
      });
    });

    startServer(ctx, () -> {
      MqttClient client = MqttClient.create(vertx, v5Options(255));
      client.connect(server.actualPort(), "localhost")
        .onComplete(ctx.asyncAssertSuccess(ack -> {
          // First publish: client sends full topic + alias; second: alias-only
          client.publish("a/b/c", Buffer.buffer("1"), MqttQoS.AT_MOST_ONCE, false, false);
          client.publish("a/b/c", Buffer.buffer("2"), MqttQoS.AT_MOST_ONCE, false, false);
        }));
    });

    first.awaitSuccess(5000);
    second.awaitSuccess(5000);
  }

  // ======================================================================
  // Test 2 – Alias senza definizione  (client → server)
  //
  //   Alias 1 is first defined for "a/b/c".
  //   Then PUBLISH topic="" alias=2 arrives — alias 2 has never been mapped.
  //   The server must close the connection (protocol error).
  // ======================================================================
  @Test
  public void test2_aliasUndefined(TestContext ctx) throws InterruptedException {
    Async serverClosed = ctx.async();

    server.endpointHandler(endpoint -> {
      endpoint.accept(false, buildConnAckProps(10));
      endpoint.closeHandler(v -> serverClosed.complete());
    });

    startServer(ctx, null);

    // Raw MQTT 5 client: first define alias 1, then use undefined alias 2
    rawMqtt5Test(server.actualPort(), 10, ch -> {
      ch.writeAndFlush(buildPublish("a/b/c", 1)); // defines alias 1
      ch.writeAndFlush(buildPublish("", 2));       // alias 2 never defined → error
    });

    serverClosed.awaitSuccess(5000);
  }

  // ======================================================================
  // Test 3 – Alias fuori range  (server → client)
  //
  //   Client declares TopicAliasMaximum = 10 in CONNECT.
  //   Server sends PUBLISH with alias = 11.
  //   Client must close the connection (TOPIC_ALIAS_INVALID).
  // ======================================================================
  @Test
  public void test3_aliasOutOfRange_serverToClient(TestContext ctx) {
    Async clientClosed = ctx.async();

    server.endpointHandler(endpoint -> {
      endpoint.accept(false);
      // Send a PUBLISH to the client carrying alias=11 (beyond client's declared max of 10)
      MqttProperties props = new MqttProperties();
      props.add(new MqttProperties.IntegerProperty(MqttProperties.TOPIC_ALIAS, 11));
      endpoint.publish("some/topic", Buffer.buffer("data"),
        MqttQoS.AT_MOST_ONCE, false, false, 0, props);
    });

    startServer(ctx, () -> {
      // Client declares it accepts at most 10 aliases from the server
      MqttClient client = MqttClient.create(vertx, v5Options(10));
      client.closeHandler(v -> clientClosed.complete());
      client.connect(server.actualPort(), "localhost");
    });

    clientClosed.awaitSuccess(5000);
  }

  // ======================================================================
  // Test 4 – Alias overwrite è legale  (client → server)
  //
  //   PUBLISH topic="a/b" alias=5  → server stores 5 → "a/b"
  //   PUBLISH topic="x/y" alias=5  → server OVERWRITES 5 → "x/y"
  //   PUBLISH topic=""    alias=5  → server resolves to "x/y"
  //   All three messages must reach the publishHandler with the correct topic.
  // ======================================================================
  @Test
  public void test4_aliasOverwrite(TestContext ctx) throws InterruptedException {
    Async first  = ctx.async();
    Async second = ctx.async();
    Async third  = ctx.async();
    AtomicInteger count = new AtomicInteger();

    server.endpointHandler(endpoint -> {
      // TOPIC_ALIAS_MAXIMUM=0 in CONNACK: the high-level client won't auto-manage aliases,
      // so raw Netty sends the crafted packets we control.
      endpoint.accept(false, buildConnAckProps(0));
      endpoint.publishHandler(msg -> {
        int n = count.incrementAndGet();
        if (n == 1) {
          ctx.assertEquals("a/b", msg.topicName());
          first.complete();
        } else if (n == 2) {
          ctx.assertEquals("x/y", msg.topicName()); // mapping overwritten
          second.complete();
        } else if (n == 3) {
          ctx.assertEquals("x/y", msg.topicName()); // resolved after overwrite
          third.complete();
          endpoint.close(); // clean up: close connection so rawMqtt5Test can return
        }
      });
    });

    startServer(ctx, null);

    rawMqtt5Test(server.actualPort(), 10, ch -> {
      ch.writeAndFlush(buildPublish("a/b", 5)); // define  alias 5 → "a/b"
      ch.writeAndFlush(buildPublish("x/y", 5)); // overwrite alias 5 → "x/y"
      ch.writeAndFlush(buildPublish("", 5));    // resolve  alias 5 → "x/y"
    });

    first.awaitSuccess(5000);
    second.awaitSuccess(5000);
    third.awaitSuccess(5000);
  }

  // ======================================================================
  // Test 5 – Edge case: topic="" + alias mai definito → close connection
  //          (client → server)
  //
  //   MQTT 5.0 §3.3.2.3.4: "It is a Protocol Error if the Topic Alias
  //   is not included in the Topic Alias Mappings."
  //   Many implementations miss this: they ignore the empty topic instead of
  //   closing.  The server MUST close the connection.
  // ======================================================================
  @Test
  public void test5_emptyTopicUndefinedAlias(TestContext ctx) throws InterruptedException {
    Async serverClosed = ctx.async();

    server.endpointHandler(endpoint -> {
      endpoint.accept(false, buildConnAckProps(10));
      endpoint.closeHandler(v -> serverClosed.complete());
    });

    startServer(ctx, null);

    // Send PUBLISH with topic="" and alias=3 that was NEVER defined → protocol error
    rawMqtt5Test(server.actualPort(), 10, ch ->
      ch.writeAndFlush(buildPublish("", 3)));

    serverClosed.awaitSuccess(5000);
  }

  // ======================================================================
  // Helpers
  // ======================================================================

  /** CONNACK properties advertising how many client-to-server aliases the server accepts. */
  private MqttProperties buildConnAckProps(int topicAliasMaximum) {
    MqttProperties props = new MqttProperties();
    props.add(new MqttProperties.IntegerProperty(MqttProperties.TOPIC_ALIAS_MAXIMUM, topicAliasMaximum));
    return props;
  }

  /**
   * MqttClientOptions for MQTT 5 with the given topicAliasMaximum.
   * This value is sent in CONNECT and controls how many server→client aliases the client accepts.
   */
  private MqttClientOptions v5Options(int topicAliasMaximum) {
    MqttClientOptions opts = new MqttClientOptions();
    opts.setVersion(MqttVersion.MQTT_5.protocolLevel());
    opts.setTopicAliasMaximum(topicAliasMaximum);
    return opts;
  }

  /** Start the server on a random port; block until it is ready. */
  private void startServer(TestContext ctx, Runnable afterStart) {
    Async latch = ctx.async();
    server.listen(0).onComplete(ctx.asyncAssertSuccess(v -> {
      latch.complete();
      if (afterStart != null) afterStart.run();
    }));
    latch.awaitSuccess(5000);
  }

  /**
   * Connect a raw MQTT 5 client (Netty Bootstrap + decoder/encoder),
   * send CONNECT declaring {@code clientTopicAliasMaximum},
   * wait for CONNACK, call {@code afterConnack} with the channel,
   * then block until the connection is closed (or 5 s timeout).
   */
  private void rawMqtt5Test(int port, int clientTopicAliasMaximum,
                             Consumer<Channel> afterConnack) throws InterruptedException {
    EventLoopGroup group = new NioEventLoopGroup(1);
    CountDownLatch connackLatch = new CountDownLatch(1);
    CountDownLatch closedLatch  = new CountDownLatch(1);
    try {
      Bootstrap bootstrap = new Bootstrap()
        .group(group)
        .channel(NioSocketChannel.class)
        .handler(new ChannelInitializer<SocketChannel>() {
          @Override
          protected void initChannel(SocketChannel ch) {
            ch.pipeline()
              .addLast("dec", new MqttDecoder())
              .addLast("enc", MqttEncoder.INSTANCE)
              .addLast("h", new ChannelInboundHandlerAdapter() {
                @Override
                public void channelRead(ChannelHandlerContext cx, Object msg) {
                  // Signal when CONNACK is received so we can safely send PUBLISH
                  if (msg instanceof io.netty.handler.codec.mqtt.MqttConnAckMessage) {
                    connackLatch.countDown();
                  }
                }
                @Override
                public void channelInactive(ChannelHandlerContext cx) {
                  closedLatch.countDown();
                }
              });
          }
        });

      ChannelFuture f = bootstrap.connect("localhost", port).sync();
      Channel ch = f.channel();

      // Send MQTT 5 CONNECT
      ch.writeAndFlush(buildMqtt5Connect(clientTopicAliasMaximum));

      // Wait for CONNACK before sending application packets
      connackLatch.await(5, TimeUnit.SECONDS);
      afterConnack.accept(ch);

      // Block until the connection closes (server closes on error, or we closed it ourselves)
      closedLatch.await(5, TimeUnit.SECONDS);
    } finally {
      group.shutdownGracefully();
    }
  }

  /** Build an MQTT 5 CONNECT packet with TOPIC_ALIAS_MAXIMUM in the properties. */
  private MqttMessage buildMqtt5Connect(int topicAliasMaximum) {
    MqttProperties connectProps = new MqttProperties();
    connectProps.add(new MqttProperties.IntegerProperty(
      MqttProperties.TOPIC_ALIAS_MAXIMUM, topicAliasMaximum));

    MqttConnectVariableHeader varHeader = new MqttConnectVariableHeader(
      "MQTT",                          // protocol name
      MqttVersion.MQTT_5.protocolLevel(), // 5
      false, false,                    // no username / password
      false, 0, false,                 // no will
      true,                            // clean session
      60,                              // keep-alive (s)
      connectProps);

    MqttConnectPayload payload = new MqttConnectPayload(
      "raw-alias-test-" + System.nanoTime(),
      (String) null, (byte[]) null, (String) null, (byte[]) null); // no will / auth

    MqttFixedHeader fixedHeader = new MqttFixedHeader(
      MqttMessageType.CONNECT, false, MqttQoS.AT_MOST_ONCE, false, 0);
    return MqttMessageFactory.newMessage(fixedHeader, varHeader, payload);
  }

  /**
   * Build a QoS-0 PUBLISH packet carrying the given topic name and TOPIC_ALIAS property.
   * Pass {@code topicName = ""} to produce an alias-only packet (no topic name on the wire).
   */
  private io.netty.handler.codec.mqtt.MqttPublishMessage buildPublish(String topicName, int alias) {
    MqttProperties props = new MqttProperties();
    props.add(new MqttProperties.IntegerProperty(MqttProperties.TOPIC_ALIAS, alias));
    MqttFixedHeader fixedHeader = new MqttFixedHeader(
      MqttMessageType.PUBLISH, false, MqttQoS.AT_MOST_ONCE, false, 0);
    MqttPublishVariableHeader varHeader = new MqttPublishVariableHeader(topicName, 0, props);
    return (io.netty.handler.codec.mqtt.MqttPublishMessage)
      MqttMessageFactory.newMessage(fixedHeader, varHeader, Unpooled.EMPTY_BUFFER);
  }
}
