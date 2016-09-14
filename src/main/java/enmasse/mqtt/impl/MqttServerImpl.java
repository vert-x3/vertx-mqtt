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

import enmasse.mqtt.MqttServer;
import enmasse.mqtt.MqttServerHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import io.vertx.core.Vertx;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;

/**
 * An MQTT server implementation
 */
public class MqttServerImpl implements MqttServer {

    private final VertxInternal vertx;
    private ServerBootstrap bootstrap;

    /**
     * Constructor for receiving a Vert.x instance
     * @param vertx     Vert.x instance
     */
    public MqttServerImpl(Vertx vertx) {

        this.vertx = (VertxInternal) vertx;
    }

    public MqttServer listen(int port, String host) {

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
                pipeline.addLast(new MqttServerHandler());
            }
        });

        // bind the server socket
        ChannelFuture bindFuture = this.bootstrap.bind(host, port);
        bindFuture.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture channelFuture) throws Exception {

            }
        });

        return this;
    }
}
