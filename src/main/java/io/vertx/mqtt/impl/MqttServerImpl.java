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

import io.vertx.mqtt.MqttServer;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.mqtt.MqttConnectMessage;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.impl.VertxHandler;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.MqttServerOptions;
import io.vertx.mqtt.messages.MqttPublishMessage;
import io.vertx.mqtt.messages.MqttSubscribeMessage;
import io.vertx.mqtt.messages.MqttUnsubscribeMessage;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An MQTT server implementation
 */
public class MqttServerImpl implements MqttServer {

  private static final Logger log = LoggerFactory.getLogger(MqttServerImpl.class);

  private final MqttServerOptions options;
  private final VertxInternal vertx;
  private ServerBootstrap bootstrap;
  private Channel serverChannel;
  private final Map<Channel, MqttConnection> connectionMap = new ConcurrentHashMap<>();
  private Handler<MqttEndpoint> handler;

  private volatile int actualPort;
  private boolean logEnabled;

  /**
   * Constructor
   *
   * @param vertx   Vert.x instance
   * @param options MQTT server options
   */
  public MqttServerImpl(Vertx vertx, MqttServerOptions options) {

    this.vertx = (VertxInternal) vertx;
    this.options = options;
    this.logEnabled = options.getLogActivity();
  }

  public MqttServerImpl endpointHandler(Handler<MqttEndpoint> handler) {
    this.handler = handler;
    return this;
  }

  public MqttServerImpl listen() {
    return this.listen(this.options.getPort(), this.options.getHost(), null);
  }

  public MqttServerImpl listen(int port, String host) {
    return this.listen(port, host, null);
  }

  public MqttServerImpl listen(int port, String host, Handler<AsyncResult<MqttServer>> listenHandler) {

    if (this.handler == null) {
      throw new IllegalStateException("Set the MQTT endpoint handler first");
    }

    if (this.bootstrap != null) {
      throw new IllegalStateException("The MQTT server is already started");
    }

    // get the current context as a Vert.x internal context
    ContextInternal context = vertx.getOrCreateContext();

    // the Vert.x internal context gives access to Netty's event loop used as child group
    EventLoop eventLoop = context.nettyEventLoop();

    // the acceptor group is used as parent group
    EventLoopGroup acceptorGroup = vertx.getAcceptorEventLoopGroup();

    // create and configure the Netty server bootstrap
    this.bootstrap = new ServerBootstrap();
    this.bootstrap.channel(NioServerSocketChannel.class);
    this.bootstrap.group(acceptorGroup, eventLoop);
    this.bootstrap.childHandler(new ChannelInitializer<Channel>() {

      @Override
      protected void initChannel(Channel channel) throws Exception {

        ChannelPipeline pipeline = channel.pipeline();
        if (logEnabled) {
          pipeline.addLast("logging", new LoggingHandler());
        }
        pipeline.addLast("mqttEncoder", MqttEncoder.INSTANCE);
        pipeline.addLast("mqttDecoder", new MqttDecoder());
        pipeline.addLast("mqttHandler", new MqttServerHandler(pipeline.channel()));
      }
    });

    // bind the server socket
    ChannelFuture bindFuture = this.bootstrap.bind(host, port);
    bindFuture.addListener(new ChannelFutureListener() {
      @Override
      public void operationComplete(ChannelFuture channelFuture) throws Exception {

        if (listenHandler != null) {

          // when we dispatch code to the Vert.x API we need to use executeFromIO
          context.executeFromIO(() -> {

            // callback the listen handler either with a success or a failure
            if (channelFuture.isSuccess()) {

              serverChannel = channelFuture.channel();
              actualPort = ((InetSocketAddress) serverChannel.localAddress()).getPort();

              listenHandler.handle(Future.succeededFuture(MqttServerImpl.this));

            } else {

              listenHandler.handle(Future.failedFuture(channelFuture.cause()));
            }

          });
        }
      }
    });

    return this;
  }

  public MqttServerImpl listen(int port) {
    return this.listen(port, "0.0.0.0", null);
  }

  public MqttServerImpl listen(int port, Handler<AsyncResult<MqttServer>> listenHandler) {
    return this.listen(port, "0.0.0.0", listenHandler);
  }

  public MqttServerImpl listen(Handler<AsyncResult<MqttServer>> listenHandler) {
    return this.listen(this.options.getPort(), this.options.getHost(), listenHandler);
  }

  public void close() {

    // close the server channel used for listening
    if (this.serverChannel != null) {
      this.serverChannel.close();
      this.serverChannel = null;
    }

    // close all remote MQTT client connections
    for (MqttConnection conn : this.connectionMap.values()) {
      conn.close();
    }
  }

  public void close(Handler<AsyncResult<Void>> completionHandler) {
    // TODO
  }

  public int actualPort() {
    return this.actualPort;
  }

  /**
   * MQTT server handler for the underlying Netty channel pipeline
   */
  public class MqttServerHandler extends VertxHandler<MqttConnection> {

    // connection and channel belong to the remote MQTT client
    private MqttConnection conn;
    private final Channel ch;
    private Map<Channel, MqttConnection> connectionMap;

    /**
     * Constructor
     *
     * @param ch channel (netty) for the current connection
     */
    public MqttServerHandler(Channel ch) {

      this.connectionMap = MqttServerImpl.this.connectionMap;
      this.ch = ch;
    }

    @Override
    protected MqttConnection getConnection() {
      return this.conn;
    }

    @Override
    protected MqttConnection removeConnection() {

      this.connectionMap.remove(ch);
      MqttConnection conn = this.conn;
      this.conn = null;
      return conn;
    }

    @Override
    protected void channelRead(MqttConnection connection, ContextImpl context, ChannelHandlerContext chctx, Object msg) throws Exception {

      this.doMessageReceived(connection, chctx, msg);
    }

    @Override
    protected Object safeObject(Object msg, ByteBufAllocator allocator) throws Exception {

      // some Netty native MQTT messages need a mapping to Vert.x ones (available for polyglotization)
      // and different byte buffer resources are allocated
      if (msg instanceof io.netty.handler.codec.mqtt.MqttMessage) {

        io.netty.handler.codec.mqtt.MqttMessage mqttMessage = (io.netty.handler.codec.mqtt.MqttMessage) msg;

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
            ByteBuf newBuf = safeBuffer(publish.payload(), allocator);

            return MqttPublishMessage.create(
              publish.variableHeader().messageId(),
              publish.fixedHeader().qosLevel(),
              publish.fixedHeader().isDup(),
              publish.fixedHeader().isRetain(),
              newBuf);
        }
      }

      // otherwise the original Netty message is returned
      return msg;
    }

    /**
     * Execute some process on the received message from the remote MQTT client
     *
     * @param connection connection related to the remote
     * @param chctx      channel handler context
     * @param msg        received message
     * @throws Exception if some error occurs
     */
    private void doMessageReceived(MqttConnection connection, ChannelHandlerContext chctx, Object msg) throws Exception {

      // handling a Netty native MQTT message directly
      if (msg instanceof io.netty.handler.codec.mqtt.MqttMessage) {

        io.netty.handler.codec.mqtt.MqttMessage mqttMessage = (io.netty.handler.codec.mqtt.MqttMessage) msg;

        switch (mqttMessage.fixedHeader().messageType()) {

          case CONNECT:

            if (this.conn == null) {
              this.createConnAndHandle(this.ch, mqttMessage);
            }
            break;

          default:

            if (this.conn != null) {
              this.conn.handleMessage(mqttMessage);
            }
            break;
        }

        // or handling a Vert.x MQTT message (mapped from a Netty one)
      } else {

        if (this.conn != null) {
          this.conn.handleMessage(msg);
        }
      }

    }

    /**
     * Create the connection and the endpoint
     *
     * @param ch  channel belong to the remote MQTT client
     * @param msg received message
     */
    private void createConnAndHandle(Channel ch, MqttMessage msg) {

      MqttConnectMessage mqttConnectMessage = (MqttConnectMessage) msg;

      // create the connection providing the handler
      MqttConnection mqttConn = new MqttConnection(vertx, ch, vertx.getOrCreateContext(), null);
      mqttConn.endpointHandler(handler);

      // retrieve will information from CONNECT message
      MqttWillImpl will = mqttConnectMessage.variableHeader().isWillFlag() ?
        new MqttWillImpl(
          mqttConnectMessage.payload().willTopic(),
          mqttConnectMessage.payload().willMessage(),
          mqttConnectMessage.variableHeader().willQos(),
          mqttConnectMessage.variableHeader().isWillRetain()) : null;

      // retrieve authorization information from CONNECT message
      MqttAuthImpl auth = (mqttConnectMessage.variableHeader().hasUserName() &&
        mqttConnectMessage.variableHeader().hasPassword()) ?
        new MqttAuthImpl(
          mqttConnectMessage.payload().userName(),
          mqttConnectMessage.payload().password()) : null;

      // create the MQTT endpoint provided to the application handler
      MqttEndpointImpl endpoint =
        new MqttEndpointImpl(
          mqttConn,
          mqttConnectMessage.payload().clientIdentifier(),
          auth,
          will,
          mqttConnectMessage.variableHeader().isCleanSession(),
          mqttConnectMessage.variableHeader().version(),
          mqttConnectMessage.variableHeader().keepAliveTimeSeconds());

      mqttConn.handleConnect(endpoint);

      this.conn = mqttConn;
      this.connectionMap.put(ch, mqttConn);


      // keep alive == 0 means NO keep alive, no timeout to handle
      if (mqttConnectMessage.variableHeader().keepAliveTimeSeconds() != 0) {

        // the server waits for one and a half times the keep alive time period (MQTT spec)
        int timeout = mqttConnectMessage.variableHeader().keepAliveTimeSeconds() +
          mqttConnectMessage.variableHeader().keepAliveTimeSeconds() / 2;

        // modifying the channel pipeline for adding the idle state handler with previous timeout
        this.ch.pipeline().addBefore("mqttHandler", "idle", new IdleStateHandler(0, 0, timeout));
      }
    }
  }
}
