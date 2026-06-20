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
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.mqtt.MqttConnectMessage;
import io.netty.handler.codec.mqtt.MqttConnectPayload;
import io.netty.handler.codec.mqtt.MqttConnectVariableHeader;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import io.netty.handler.codec.mqtt.MqttFixedHeader;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageFactory;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttPubReplyMessageVariableHeader;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttPublishVariableHeader;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttVersion;
import io.netty.util.ReferenceCountUtil;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.unit.TestContext;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.messages.codes.MqttPubCompReasonCode;
import io.vertx.mqtt.messages.codes.MqttPubRecReasonCode;
import io.vertx.mqtt.messages.codes.MqttPubRelReasonCode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * MQTT 5 server testing about QoS 2 PUB reply reason codes and properties.
 */
public class Mqtt5ServerQos2PubReplyTest extends MqttServerBaseTest {

  private static final String MQTT_TOPIC = "/my_topic";
  private static final String MQTT_MESSAGE = "Hello Vert.x MQTT Server";
  private static final int CLIENT_PUBLISH_MESSAGE_ID = 7;
  private static final int SERVER_PUBLISH_MESSAGE_ID = 9;
  private static final String PUBREC_REASON = "pubrec-reason";
  private static final String PUBREL_REASON = "pubrel-reason";
  private static final String PUBCOMP_REASON = "pubcomp-reason";

  private TestMode testMode;

  @Before
  public void before(TestContext context) {
    this.setUp(context);
  }

  @After
  public void after(TestContext context) {
    this.tearDown(context);
  }

  @Test
  public void publishReceivedAndCompleteIncludeMqtt5ReasonCodeAndProperties(TestContext context) throws Exception {
    this.testMode = TestMode.CLIENT_PUBLISH;

    try (RawMqttClient client = RawMqttClient.connect()) {
      client.write(connectMessage("client-publish"));
      client.await(MqttMessageType.CONNACK);

      client.write(publishMessage(CLIENT_PUBLISH_MESSAGE_ID));

      MqttMessage pubrec = client.await(MqttMessageType.PUBREC);
      assertPubReply(context,
        pubrec,
        CLIENT_PUBLISH_MESSAGE_ID,
        MqttPubRecReasonCode.NO_MATCHING_SUBSCRIBERS.value(),
        PUBREC_REASON);

      client.write(pubReply(MqttMessageType.PUBREL,
        CLIENT_PUBLISH_MESSAGE_ID,
        MqttPubRelReasonCode.SUCCESS.value(),
        MqttProperties.NO_PROPERTIES));

      MqttMessage pubcomp = client.await(MqttMessageType.PUBCOMP);
      assertPubReply(context,
        pubcomp,
        CLIENT_PUBLISH_MESSAGE_ID,
        MqttPubCompReasonCode.PACKET_IDENTIFIER_NOT_FOUND.value(),
        PUBCOMP_REASON);
    }
  }

  @Test
  public void publishReleaseIncludesMqtt5ReasonCodeAndProperties(TestContext context) throws Exception {
    this.testMode = TestMode.SERVER_PUBLISH;

    try (RawMqttClient client = RawMqttClient.connect()) {
      client.write(connectMessage("server-publish"));
      client.await(MqttMessageType.CONNACK);

      MqttMessage publish = client.await(MqttMessageType.PUBLISH);
      try {
        context.assertEquals(SERVER_PUBLISH_MESSAGE_ID, ((MqttPublishMessage)publish).variableHeader().packetId());
      } finally {
        ReferenceCountUtil.release(publish);
      }

      client.write(pubReply(MqttMessageType.PUBREC,
        SERVER_PUBLISH_MESSAGE_ID,
        MqttPubRecReasonCode.SUCCESS.value(),
        MqttProperties.NO_PROPERTIES));

      MqttMessage pubrel = client.await(MqttMessageType.PUBREL);
      assertPubReply(context,
        pubrel,
        SERVER_PUBLISH_MESSAGE_ID,
        MqttPubRelReasonCode.PACKET_IDENTIFIER_NOT_FOUND.value(),
        PUBREL_REASON);

      client.write(pubReply(MqttMessageType.PUBCOMP,
        SERVER_PUBLISH_MESSAGE_ID,
        MqttPubCompReasonCode.SUCCESS.value(),
        MqttProperties.NO_PROPERTIES));
    }
  }

  @Override
  protected void endpointHandler(MqttEndpoint endpoint, TestContext context) {
    if (this.testMode == TestMode.CLIENT_PUBLISH) {
      endpoint.publishHandler(message -> {
        endpoint.publishReceived(message.messageId(),
          MqttPubRecReasonCode.NO_MATCHING_SUBSCRIBERS,
          reasonProperties(PUBREC_REASON));
      }).publishReleaseMessageHandler(message -> {
        endpoint.publishComplete(message.messageId(),
          MqttPubCompReasonCode.PACKET_IDENTIFIER_NOT_FOUND,
          reasonProperties(PUBCOMP_REASON));
      });
    } else {
      endpoint.publishReceivedHandler(messageId -> {
        endpoint.publishRelease(messageId,
          MqttPubRelReasonCode.PACKET_IDENTIFIER_NOT_FOUND,
          reasonProperties(PUBREL_REASON));
      });
    }

    endpoint.accept(false);

    if (this.testMode == TestMode.SERVER_PUBLISH) {
      endpoint.publish(MQTT_TOPIC,
        Buffer.buffer(MQTT_MESSAGE),
        MqttQoS.EXACTLY_ONCE,
        false,
        false,
        SERVER_PUBLISH_MESSAGE_ID,
        MqttProperties.NO_PROPERTIES)
        .onComplete(context.asyncAssertSuccess());
    }
  }

  private static MqttConnectMessage connectMessage(String clientId) {
    MqttFixedHeader fixedHeader =
      new MqttFixedHeader(MqttMessageType.CONNECT, false, MqttQoS.AT_MOST_ONCE, false, 0);

    MqttConnectVariableHeader variableHeader =
      new MqttConnectVariableHeader(MqttVersion.MQTT_5.protocolName(),
        MqttVersion.MQTT_5.protocolLevel(),
        false,
        false,
        false,
        0,
        false,
        true,
        60,
        MqttProperties.NO_PROPERTIES);

    byte[] willMessage = null;
    byte[] password = null;
    MqttConnectPayload payload =
      new MqttConnectPayload(clientId, null, willMessage, null, password);

    return (MqttConnectMessage)MqttMessageFactory.newMessage(fixedHeader, variableHeader, payload);
  }

  private static MqttPublishMessage publishMessage(int messageId) {
    MqttFixedHeader fixedHeader =
      new MqttFixedHeader(MqttMessageType.PUBLISH, false, MqttQoS.EXACTLY_ONCE, false, 0);
    MqttPublishVariableHeader variableHeader =
      new MqttPublishVariableHeader(MQTT_TOPIC, messageId, MqttProperties.NO_PROPERTIES);
    ByteBuf payload = Unpooled.copiedBuffer(MQTT_MESSAGE.getBytes(StandardCharsets.UTF_8));
    return new MqttPublishMessage(fixedHeader, variableHeader, payload);
  }

  private static MqttMessage pubReply(MqttMessageType messageType, int messageId, byte reasonCode,
                                      MqttProperties properties) {
    MqttFixedHeader fixedHeader =
      new MqttFixedHeader(messageType, false, pubReplyQoS(messageType), false, 0);
    MqttPubReplyMessageVariableHeader variableHeader =
      new MqttPubReplyMessageVariableHeader(messageId, reasonCode, properties);
    return new MqttMessage(fixedHeader, variableHeader);
  }

  private static MqttQoS pubReplyQoS(MqttMessageType messageType) {
    return messageType == MqttMessageType.PUBREL ? MqttQoS.AT_LEAST_ONCE : MqttQoS.AT_MOST_ONCE;
  }

  private static MqttProperties reasonProperties(String reason) {
    MqttProperties properties = new MqttProperties();
    properties.add(new MqttProperties.StringProperty(MqttProperties.MqttPropertyType.REASON_STRING.value(), reason));
    return properties;
  }

  private static void assertPubReply(TestContext context, MqttMessage message, int expectedMessageId,
                                     byte expectedReasonCode, String expectedReason) {
    try {
      MqttPubReplyMessageVariableHeader variableHeader =
        (MqttPubReplyMessageVariableHeader)message.variableHeader();

      context.assertEquals(expectedMessageId, variableHeader.messageId());
      context.assertEquals(Byte.toUnsignedInt(expectedReasonCode), Byte.toUnsignedInt(variableHeader.reasonCode()));

      MqttProperties.MqttProperty<?> reasonProperty =
        variableHeader.properties().getProperty(MqttProperties.MqttPropertyType.REASON_STRING.value());
      context.assertNotNull(reasonProperty);
      context.assertEquals(expectedReason, reasonProperty.value());
    } finally {
      ReferenceCountUtil.release(message);
    }
  }

  private enum TestMode {
    CLIENT_PUBLISH,
    SERVER_PUBLISH
  }

  private static class RawMqttClient implements AutoCloseable {

    private final EventLoopGroup group;
    private final Channel channel;
    private final BlockingQueue<MqttMessage> messages;
    private final BlockingQueue<Throwable> failures;

    private RawMqttClient(EventLoopGroup group, Channel channel, BlockingQueue<MqttMessage> messages,
                          BlockingQueue<Throwable> failures) {
      this.group = group;
      this.channel = channel;
      this.messages = messages;
      this.failures = failures;
    }

    static RawMqttClient connect() throws InterruptedException {
      EventLoopGroup group = new NioEventLoopGroup();
      BlockingQueue<MqttMessage> messages = new LinkedBlockingQueue<>();
      BlockingQueue<Throwable> failures = new LinkedBlockingQueue<>();

      Bootstrap bootstrap = new Bootstrap();
      bootstrap
        .group(group)
        .channel(NioSocketChannel.class)
        .handler(new ChannelInitializer<SocketChannel>() {
          @Override
          protected void initChannel(SocketChannel ch) {
            ChannelPipeline pipeline = ch.pipeline();
            pipeline.addLast("mqttDecoder", new MqttDecoder());
            pipeline.addLast("mqttEncoder", MqttEncoder.INSTANCE);
            pipeline.addLast("messageHandler", new ChannelInboundHandlerAdapter() {
              @Override
              public void channelRead(ChannelHandlerContext ctx, Object msg) {
                if (msg instanceof MqttMessage) {
                  messages.add((MqttMessage)msg);
                } else {
                  ReferenceCountUtil.release(msg);
                }
              }

              @Override
              public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                failures.add(cause);
                ctx.close();
              }
            });
          }
        });

      try {
        ChannelFuture connectFuture = bootstrap.connect(MQTT_SERVER_HOST, MQTT_SERVER_PORT).sync();
        return new RawMqttClient(group, connectFuture.channel(), messages, failures);
      } catch (InterruptedException | RuntimeException e) {
        group.shutdownGracefully().awaitUninterruptibly();
        throw e;
      }
    }

    void write(MqttMessage message) throws InterruptedException {
      this.channel.writeAndFlush(message).sync();
    }

    MqttMessage await(MqttMessageType messageType) throws InterruptedException {
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
      while (System.nanoTime() < deadline) {
        Throwable failure = this.failures.poll();
        if (failure != null) {
          throw new AssertionError(failure);
        }
        MqttMessage message = this.messages.poll(100, TimeUnit.MILLISECONDS);
        if (message != null) {
          if (message.fixedHeader().messageType() == messageType) {
            return message;
          }
          ReferenceCountUtil.release(message);
        }
      }
      throw new AssertionError("Timed out waiting for " + messageType);
    }

    @Override
    public void close() throws InterruptedException {
      try {
        this.channel.close().sync();
      } finally {
        releaseQueuedMessages();
        this.group.shutdownGracefully().sync();
      }
    }

    private void releaseQueuedMessages() {
      MqttMessage message;
      while ((message = this.messages.poll()) != null) {
        ReferenceCountUtil.release(message);
      }
    }
  }
}
