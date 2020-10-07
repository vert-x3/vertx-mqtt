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

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.mqtt.MqttEncoder;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageBuilders;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.util.CharsetUtil;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetClient;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.mqtt.MqttClientOptions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.charset.StandardCharsets;

/**
 * MQTT server testing about bad clients not MQTT exchange compliant
 */
@RunWith(VertxUnitRunner.class)
public class MqttServerBadClientTest extends MqttServerBaseTest {

  private static final String MQTT_TOPIC = "/my_topic";
  private static final String MQTT_MESSAGE = "I'm a bad client";

  @Before
  public void before(TestContext context) {
    this.setUp(context);
  }

  @After
  public void after(TestContext context) {
    this.tearDown(context);
  }

  @Test
  public void multipleConnect(TestContext context) throws InterruptedException {

    // There are should not be any exceptions during the test
    mqttServer.exceptionHandler(t -> {
      context.assertTrue(false);
    });

    EventLoopGroup group = new NioEventLoopGroup();
    try {

      Bootstrap bootstrap = new Bootstrap();
      bootstrap
        .group(group)
        .channel(NioSocketChannel.class)
        .handler(new ChannelInitializer<SocketChannel>() {
          @Override
          protected void initChannel(SocketChannel ch) throws Exception {

            ChannelPipeline pipeline = ch.pipeline();
            pipeline.addLast("mqttEncoder", MqttEncoder.INSTANCE);
          }
        });

      // Start the client.
      ChannelFuture f = bootstrap.connect(MQTT_SERVER_HOST, MQTT_SERVER_PORT).sync();
      long tick = System.currentTimeMillis();

      MqttClientOptions options = new MqttClientOptions();
      f.channel().writeAndFlush(createConnectPacket(options)).sync();
      f.channel().writeAndFlush(createConnectPacket(options)).sync();

      // Wait until the connection is closed.
      f.channel().closeFuture().sync();
      long tock = System.currentTimeMillis();

      // Default timeout is 90 seconds
      // If connection was closed earlier that means that it was a server
      context.assertTrue((tock - tick) / 1000 < 90);

    } finally {
      // Shut down the event loop to terminate all threads.
      group.shutdownGracefully();
    }
  }

  @Test
  public void noConnectTest(TestContext context) throws Exception {

    EventLoopGroup group = new NioEventLoopGroup();
    try {

      Bootstrap bootstrap = new Bootstrap();
      bootstrap
        .group(group)
        .channel(NioSocketChannel.class)
        .handler(new ChannelInitializer<SocketChannel>() {
          @Override
          protected void initChannel(SocketChannel ch) throws Exception {

            ChannelPipeline pipeline = ch.pipeline();
            pipeline.addLast("mqttEncoder", MqttEncoder.INSTANCE);
          }
        });

      // Start the client.
      ChannelFuture f = bootstrap.connect(MQTT_SERVER_HOST, MQTT_SERVER_PORT).sync();

      f.channel().writeAndFlush(createPublishMessage());

      // Wait until the connection is closed.
      f.channel().closeFuture().sync();

      context.assertTrue(true);

    } finally {
      // Shut down the event loop to terminate all threads.
      group.shutdownGracefully();
    }
  }

  @Test
  public void unknownMessageType(TestContext context) {

    NetClient client = this.vertx.createNetClient();
    Async async = context.async();

    client.connect(MQTT_SERVER_PORT, MQTT_SERVER_HOST, done -> {

      if (done.succeeded()) {

        byte[] packet = new byte[] { (byte)0xF0, (byte)0x00};

        done.result().write(Buffer.buffer(packet));

        done.result().closeHandler(v -> {
          async.complete();
        });

      } else {
        context.fail();
      }
    });

    async.await();
  }

  private final ByteBufAllocator ALLOCATOR = new UnpooledByteBufAllocator(false);

  private MqttPublishMessage createPublishMessage() {
    ByteBuf payload =  ALLOCATOR.buffer();
    payload.writeBytes(MQTT_MESSAGE.getBytes(CharsetUtil.UTF_8));

    return MqttMessageBuilders.publish()
      .qos(MqttQoS.AT_LEAST_ONCE)
      .retained(true)
      .topicName(MQTT_TOPIC)
      .messageId(1)
      .payload(payload)
      .build();
  }

  private MqttMessage createConnectPacket(MqttClientOptions options) {
    return MqttMessageBuilders.connect()
      .hasUser(options.hasUsername())
      .hasPassword(options.hasPassword())
      .willRetain(options.isWillRetain())
      .willQoS(MqttQoS.valueOf(options.getWillQoS()))
      .willFlag(options.isWillFlag())
      .willTopic(options.getWillTopic())
      .willMessage(options.getWillMessage() != null ? options.getWillMessage().getBytes(StandardCharsets.UTF_8) : null)
      .cleanSession(options.isCleanSession())
      .keepAlive(options.getKeepAliveTimeSeconds())
      .clientId(options.getClientId() == null ? "" : options.getClientId())
      .username(options.hasUsername() ? options.getUsername() : null)
      .password(options.hasPassword() ? options.getPassword().getBytes() : null)
      .build();
  }
}
