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
import io.netty.handler.codec.mqtt.MqttConnectPayload;
import io.netty.handler.codec.mqtt.MqttConnectVariableHeader;
import io.netty.handler.codec.mqtt.MqttEncoder;
import io.netty.handler.codec.mqtt.MqttFixedHeader;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageFactory;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttPublishVariableHeader;
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

  private static final String PROTOCOL_NAME = "MQTT";
  private static final int PROTOCOL_VERSION = 4;
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

    client.connect(MQTT_SERVER_PORT, MQTT_SERVER_HOST).onComplete(context.asyncAssertSuccess(res -> {
      byte[] packet = new byte[] { (byte)0xF0, (byte)0x00};

      res.write(Buffer.buffer(packet));

      res.closeHandler(v -> {
        async.complete();
      });
    }));

    async.await();
  }

  private final ByteBufAllocator ALLOCATOR = new UnpooledByteBufAllocator(false);

  private MqttPublishMessage createPublishMessage() {

    MqttFixedHeader mqttFixedHeader =
      new MqttFixedHeader(MqttMessageType.PUBLISH, false, MqttQoS.AT_LEAST_ONCE, true, 0);

    MqttPublishVariableHeader mqttPublishVariableHeader = new MqttPublishVariableHeader(MQTT_TOPIC, 1);

    ByteBuf payload =  ALLOCATOR.buffer();
    payload.writeBytes(MQTT_MESSAGE.getBytes(CharsetUtil.UTF_8));

    return new MqttPublishMessage(mqttFixedHeader, mqttPublishVariableHeader, payload);
  }

  private MqttMessage createConnectPacket(MqttClientOptions options) {
    MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.CONNECT,
      false,
      MqttQoS.AT_MOST_ONCE,
      false,
      0);

    MqttConnectVariableHeader variableHeader = new MqttConnectVariableHeader(
      PROTOCOL_NAME,
      PROTOCOL_VERSION,
      options.hasUsername(),
      options.hasPassword(),
      options.isWillRetain(),
      options.getWillQoS(),
      options.isWillFlag(),
      options.isCleanSession(),
      options.getKeepAliveInterval()
    );

    MqttConnectPayload payload = new MqttConnectPayload(
      options.getClientId() == null ? "" : options.getClientId(),
      options.getWillTopic(),
      options.getWillMessage() != null ? options.getWillMessage().getBytes() : null,
      options.hasUsername() ? options.getUsername() : null,
      options.hasPassword() ? options.getPassword().getBytes(StandardCharsets.UTF_8) : null
    );

    return MqttMessageFactory.newMessage(fixedHeader, variableHeader, payload);
  }
}
