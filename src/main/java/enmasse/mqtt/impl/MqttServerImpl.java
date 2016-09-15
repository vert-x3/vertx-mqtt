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

package enmasse.mqtt.impl;

import enmasse.mqtt.MqttEndpoint;
import enmasse.mqtt.MqttEndpointStream;
import enmasse.mqtt.MqttServer;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageType;
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

import java.net.InetSocketAddress;

/**
 * An MQTT server implementation
 */
public class MqttServerImpl implements MqttServer {

    private static final Logger log = LoggerFactory.getLogger(MqttServerImpl.class);

    private final VertxInternal vertx;
    private ServerBootstrap bootstrap;
    private Channel serverChannel;

    private final MqttEndpointStreamImpl endpointStream = new MqttEndpointStreamImpl();

    private volatile int actualPort;

    /**
     * Constructor for receiving a Vert.x instance
     *
     * @param vertx     Vert.x instance
     */
    public MqttServerImpl(Vertx vertx) {

        this.vertx = (VertxInternal) vertx;
    }

    @Override
    public MqttServer listen(int port, String host) {
        return this.listen(port, host, null);
    }

    @Override
    public MqttServer endpointHandler(Handler<MqttEndpoint> handler) {
        this.endpointStream().handler(handler);
        return this;
    }

    @Override
    public MqttEndpointStream endpointStream() {
        return this.endpointStream;
    }

    @Override
    public Handler<MqttEndpoint> endpointHandler() {
        return this.endpointStream.handler();
    }

    @Override
    public MqttServer listen(int port, String host, Handler<AsyncResult<MqttServer>> listenHandler) {

        if (this.endpointStream.handler() == null) {
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
                pipeline.addLast(MqttEncoder.INSTANCE);
                pipeline.addLast(new MqttDecoder());
                pipeline.addLast(new MqttServerHandler(pipeline.channel()));
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
                            actualPort = ((InetSocketAddress)serverChannel.localAddress()).getPort();

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

    @Override
    public void close() {

        if (this.serverChannel != null) {
            this.serverChannel.close();
            this.serverChannel = null;
        }
    }

    @Override
    public int actualPort() {

        return this.actualPort;
    }

    /**
     * MQTT server handler for the underlying Netty channel pipeline
     */
    public class MqttServerHandler extends VertxHandler<MqttConnection> {

        private MqttConnection conn;
        private final Channel ch;

        public MqttServerHandler(Channel ch) {
            this.ch = ch;
        }

        @Override
        protected MqttConnection getConnection() {

            return this.conn;
        }

        @Override
        protected MqttConnection removeConnection() {

            return null;
        }

        @Override
        protected void channelRead(MqttConnection connection, ContextImpl context, ChannelHandlerContext chctx, Object msg) throws Exception {

            this.doMessageReceived(connection, chctx, msg);
        }

        @Override
        protected Object safeObject(Object msg, ByteBufAllocator allocator) throws Exception {

            // TODO build a safe MQTT message object from this one ?
            return msg;
        }

        private void doMessageReceived(MqttConnection connection, ChannelHandlerContext chctx, Object msg) throws Exception {

            if (msg instanceof MqttMessage) {

                MqttMessage mqttMessage = (MqttMessage) msg;

                switch (mqttMessage.fixedHeader().messageType()) {

                    case CONNECT:

                        if (this.conn == null) {

                            MqttConnection mqttConn = new MqttConnection(vertx, ch, vertx.getOrCreateContext(), null);
                            mqttConn.endpointHandler(endpointStream.handler());

                            MqttEndpointImpl endpoint = new MqttEndpointImpl(mqttConn);
                            mqttConn.handleEndpointConnect(endpoint);
                        }

                        break;
                }

            }

        }
    }
}
