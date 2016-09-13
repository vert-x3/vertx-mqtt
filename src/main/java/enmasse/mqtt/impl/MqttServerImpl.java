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
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;

/**
 * An MQTT server implementation
 */
public class MqttServerImpl implements MqttServer {

    public MqttServer listen(int port, String host) {

        EventLoopGroup group = new NioEventLoopGroup();

        try {

            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(group);
            bootstrap.channel(NioServerSocketChannel.class);
            bootstrap.childHandler(new ChannelInitializer<Channel>() {

                protected void initChannel(Channel channel) throws Exception {

                    channel.pipeline().addLast(MqttEncoder.INSTANCE);
                    channel.pipeline().addLast(new MqttDecoder());
                    channel.pipeline().addLast(new MqttServerHandler());
                }
            });


            ChannelFuture f = bootstrap.bind(host, port).sync();
            f.channel().closeFuture().sync();

        } catch (Throwable e) {

            e.printStackTrace();

        } finally {

            try {
                group.shutdownGracefully().sync();
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }

        return this;
    }
}
