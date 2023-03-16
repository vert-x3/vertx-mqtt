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
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.compression.ZlibCodecFactory;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketServerExtensionHandler;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketServerExtensionHandshaker;
import io.netty.handler.codec.http.websocketx.extensions.compression.DeflateFrameServerExtensionHandshaker;
import io.netty.handler.codec.http.websocketx.extensions.compression.PerMessageDeflateServerExtensionHandshaker;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.ReferenceCountUtil;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.impl.NetSocketInternal;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.net.NetServer;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.MqttServer;
import io.vertx.mqtt.MqttServerOptions;

import java.util.ArrayList;
import java.util.List;

import static io.vertx.mqtt.MqttServerOptions.MQTT_SUBPROTOCOL_CSV_LIST;

/**
 * An MQTT server implementation
 */
public class MqttServerImpl implements MqttServer {

  private static final Logger log = LoggerFactory.getLogger(MqttServerImpl.class);

  private final VertxInternal vertx;
  private final NetServer server;
  private Handler<MqttEndpoint> endpointHandler;
  private Handler<Throwable> exceptionHandler;

  private MqttServerOptions options;

  public MqttServerImpl(Vertx vertx, MqttServerOptions options) {
    this.vertx = (VertxInternal) vertx;
    this.server = vertx.createNetServer(options);
    this.options = options;
  }

  @Override
  public Future<MqttServer> listen() {
    return listen(this.options.getPort());
  }

  @Override
  public Future<MqttServer> listen(int port, String host) {
    Handler<MqttEndpoint> h1 = endpointHandler;
    Handler<Throwable> h2 = exceptionHandler;
    if (h1 == null) {
      return vertx.getOrCreateContext().failedFuture(new IllegalStateException("Please set handler before server is listening"));
    }
    server.connectHandler(so -> {
      NetSocketInternal soi = (NetSocketInternal) so;
      ChannelPipeline pipeline = soi.channelHandlerContext().pipeline();

      initChannel(pipeline);
      MqttServerConnection conn = new MqttServerConnection(soi, h1, h2, options);

      soi.eventHandler(evt -> {
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
          synchronized (conn) {
            conn.handleHandshakeComplete((WebSocketServerProtocolHandler.HandshakeComplete) evt);
          }
        }
        ReferenceCountUtil.release(evt);
      });

      soi.messageHandler(msg -> {
        synchronized (conn) {
          conn.handleMessage(msg);
        }
      });
    });
    return server.listen(port, host).map(this);
  }

  @Override
  public Future<MqttServer> listen(int port) {
    return listen(port, this.options.getHost());
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
  public int actualPort() {
    return server.actualPort();
  }

  @Override
  public Future<Void> close() {
    return server.close();
  }

  static class WebSocketFrameToByteBufDecoder extends MessageToMessageDecoder<BinaryWebSocketFrame> {

    @Override
    protected void decode(ChannelHandlerContext chc, BinaryWebSocketFrame frame, List<Object> out)
      throws Exception {
      // convert the frame to a ByteBuf
      ByteBuf bb = frame.content();
      bb.retain();
      out.add(bb);
    }
  }

  static class ByteBufToWebSocketFrameEncoder extends MessageToMessageEncoder<ByteBuf> {

    @Override
    protected void encode(ChannelHandlerContext chc, ByteBuf bb, List<Object> out) throws Exception {
      // convert the ByteBuf to a WebSocketFrame
      BinaryWebSocketFrame result = new BinaryWebSocketFrame();
      result.content().writeBytes(bb);
      out.add(result);
    }
  }

  private void initChannel(ChannelPipeline pipeline) {

    pipeline.addBefore("handler", "mqttEncoder", MqttEncoder.INSTANCE);
    pipeline.addBefore("handler", "mqttDecoder", new MqttDecoder(this.options.getMaxMessageSize(), this.options.getMaxClientIdLength()));
    // adding the idle state handler for timeout on CONNECT packet
    pipeline.addBefore("handler", "idle", new IdleStateHandler(this.options.timeoutOnConnect(), 0, 0));
    pipeline.addBefore("handler", "timeoutOnConnect", new ChannelDuplexHandler() {

      @Override
      public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {

        if (evt instanceof IdleStateEvent) {
          IdleStateEvent e = (IdleStateEvent) evt;
          if (e.state() == IdleState.READER_IDLE) {
            // as MQTT 3.1.1 describes, if no packet is sent after a "reasonable" time (here CONNECT timeout)
            // the connection is closed
            ctx.channel().close();
          }
        }

        super.userEventTriggered(ctx, evt);
      }
    });

    if (options.isUseWebSocket()) {
      int maxFrameSize = options.getWebSocketMaxFrameSize();

      pipeline.addBefore("mqttEncoder", "httpServerCodec", new HttpServerCodec());
      pipeline.addAfter("httpServerCodec", "aggregator", new HttpObjectAggregator(maxFrameSize));

      List<WebSocketServerExtensionHandshaker> extensionHandshakers = createExtensionHandshakers();

      if (!extensionHandshakers.isEmpty()) {
        WebSocketServerExtensionHandler extensionHandler = new WebSocketServerExtensionHandler(
          extensionHandshakers.toArray(new WebSocketServerExtensionHandshaker[0]));
        pipeline.addAfter("aggregator", "webSocketExtensionHandler", extensionHandler);
      }

      pipeline.addAfter("webSocketExtensionHandler", "webSocketHandler",
        new WebSocketServerProtocolHandler("/mqtt", MQTT_SUBPROTOCOL_CSV_LIST, !extensionHandshakers.isEmpty(), maxFrameSize, 10000L));

      pipeline.addAfter("webSocketHandler", "bytebuf2wsEncoder", new ByteBufToWebSocketFrameEncoder());
      pipeline.addAfter("bytebuf2wsEncoder", "ws2bytebufDecoder", new WebSocketFrameToByteBufDecoder());
    }
  }

  private List<WebSocketServerExtensionHandshaker> createExtensionHandshakers() {
    ArrayList<WebSocketServerExtensionHandshaker> extensionHandshakers = new ArrayList<>();

    if (options.isPerFrameWebSocketCompressionSupported()) {
      extensionHandshakers.add(new DeflateFrameServerExtensionHandshaker(options.getWebSocketCompressionLevel()));
    }
    if (options.isPerMessageWebSocketCompressionSupported()) {
      extensionHandshakers.add(new PerMessageDeflateServerExtensionHandshaker(options.getWebSocketCompressionLevel(),
        ZlibCodecFactory.isSupportingWindowSizeAndMemLevel(), PerMessageDeflateServerExtensionHandshaker.MAX_WINDOW_SIZE,
        options.isWebSocketAllowServerNoContext(), options.isWebSocketPreferredClientNoContext()));
    }
    return extensionHandshakers;
  }
}
