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

package io.vertx.mqtt.impl;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import io.netty.handler.logging.LoggingHandler;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.impl.NetServerBase;
import io.vertx.core.net.impl.SSLHelper;
import io.vertx.core.net.impl.VertxHandler;
import io.vertx.core.spi.metrics.TCPMetrics;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.MqttServer;
import io.vertx.mqtt.MqttServerOptions;
import io.vertx.mqtt.messages.MqttPublishMessage;
import io.vertx.mqtt.messages.MqttSubscribeMessage;
import io.vertx.mqtt.messages.MqttUnsubscribeMessage;

/**
 * An MQTT server implementation
 */
public class MqttServerImpl extends NetServerBase<MqttConnection> implements MqttServer {

  private Handler<MqttEndpoint> endpointHandler;
  private Handler<Throwable> exceptionHandler;

  private MqttServerOptions mqttServerOptions;

  public MqttServerImpl(Vertx vertx, MqttServerOptions options) {
    super((VertxInternal) vertx, options);
    this.mqttServerOptions = options;
  }

  @Override
  public MqttServer listen() {
    return listen(ar -> {});
  }

  @Override
  public MqttServer listen(int port, String host) {
    return listen(port, host, ar -> {});
  }

  @Override
  public MqttServer listen(int port) {
    return listen(port, ar -> {});
  }

  @Override
  public MqttServer listen(int port, Handler<AsyncResult<MqttServer>> listenHandler) {
    return listen(port, this.options.getHost(), listenHandler);
  }

  @Override
  public MqttServer listen(Handler<AsyncResult<MqttServer>> listenHandler) {
    return listen(this.options.getPort(), listenHandler);
  }

  @Override
  public MqttServer listen(int port, String host, Handler<AsyncResult<MqttServer>> listenHandler) {
    Handler<MqttEndpoint> h1 = endpointHandler;
    Handler<Throwable> h2 = exceptionHandler;
    Handler<MqttConnection> mqttConnectionHandler = c -> c.init(h1, h2);
    listen(mqttConnectionHandler, port, host, ar -> listenHandler.handle(ar.map(this)));
    return this;
  }

  @Override
  public synchronized MqttServer endpointHandler(Handler<MqttEndpoint> handler) {
    endpointHandler = handler;
    return this;
  }

  @Override
  public synchronized MqttServer exceptionHandler(Handler<Throwable> handler) {
    exceptionHandler = handler;
    return this;
  }

  @Override
  protected void handleMsgReceived(MqttConnection conn, Object msg) {
    // handling a Netty native MQTT message directly
    conn.handleMessage(msg);
  }

  @Override
  protected MqttConnection createConnection(VertxInternal vertx, Channel channel, ContextImpl context, SSLHelper helper, TCPMetrics metrics) {
    return new MqttConnection(vertx, channel, vertx.getOrCreateContext(), metrics, this.mqttServerOptions);
  }

  @Override
  protected void initChannel(ChannelPipeline pipeline) {

    if (sslHelper.isSSL()) {
      pipeline.addLast("ssl", sslHelper.createSslHandler(vertx));
    }
    if (logEnabled) {
      pipeline.addLast("logging", new LoggingHandler());
    }
    pipeline.addLast("mqttEncoder", MqttEncoder.INSTANCE);

    if (this.mqttServerOptions.getMaxMessageSize() > 0) {
      pipeline.addLast("mqttDecoder", new MqttDecoder(this.mqttServerOptions.getMaxMessageSize()));
    } else {
      // max message size not set, so the default from Netty MQTT codec is used
      pipeline.addLast("mqttDecoder", new MqttDecoder());
    }
  }

  @Override
  protected Object safeObject(Object msg, ByteBufAllocator allocator) {

    // some Netty native MQTT messages need a mapping to Vert.x ones (available for polyglotization)
    // and different byte buffer resources are allocated
    if (msg instanceof io.netty.handler.codec.mqtt.MqttMessage) {

      io.netty.handler.codec.mqtt.MqttMessage mqttMessage = (io.netty.handler.codec.mqtt.MqttMessage) msg;
      DecoderResult result = mqttMessage.decoderResult();
      if (result.isSuccess() && result.isFinished()) {
        switch (mqttMessage.fixedHeader().messageType()) {

          case SUBSCRIBE:

            io.netty.handler.codec.mqtt.MqttSubscribeMessage subscribe = (io.netty.handler.codec.mqtt.MqttSubscribeMessage) mqttMessage;

            return MqttSubscribeMessage.create(
              subscribe.variableHeader().messageId(),
              subscribe.payload().topicSubscriptions());

          case UNSUBSCRIBE:

            io.netty.handler.codec.mqtt.MqttUnsubscribeMessage unsubscribe = (io.netty.handler.codec.mqtt.MqttUnsubscribeMessage) mqttMessage;

            return MqttUnsubscribeMessage.create(
              unsubscribe.variableHeader().messageId(),
              unsubscribe.payload().topics());


          case PUBLISH:

            io.netty.handler.codec.mqtt.MqttPublishMessage publish = (io.netty.handler.codec.mqtt.MqttPublishMessage) mqttMessage;
            ByteBuf newBuf = VertxHandler.safeBuffer(publish.payload(), allocator);

            return MqttPublishMessage.create(
              publish.variableHeader().messageId(),
              publish.fixedHeader().qosLevel(),
              publish.fixedHeader().isDup(),
              publish.fixedHeader().isRetain(),
              publish.variableHeader().topicName(),
              newBuf);
        }
      }
    }

    // otherwise the original Netty message is returned
    return msg;
  }
}
