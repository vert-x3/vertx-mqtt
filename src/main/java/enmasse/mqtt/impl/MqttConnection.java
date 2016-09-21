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
import io.netty.channel.Channel;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttSubscribeMessage;
import io.netty.handler.codec.mqtt.MqttUnsubscribeMessage;
import io.vertx.core.Handler;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.impl.ConnectionBase;
import io.vertx.core.spi.metrics.NetworkMetrics;

/**
 * Represents an MQTT connection with a remote client
 */
public class MqttConnection extends ConnectionBase {

    // handler to call when a remote MQTT client connects and establishes a connection
    private Handler<MqttEndpoint> endpointHandler;
    // endpoint for handling point-to-point communication with the remote MQTT client
    private MqttEndpointImpl endpoint;

    /**
     * Constructor
     *
     * @param vertx     Vert.x instance
     * @param channel   Channel (netty) used for communication with MQTT remote client
     * @param context   Vert.x context
     * @param metrics   metricss
     */
    public MqttConnection(VertxInternal vertx, Channel channel, ContextImpl context, NetworkMetrics metrics) {
        super(vertx, channel, context, metrics);
    }

    @Override
    protected Object metric() {
        return null;
    }

    @Override
    protected void handleInterestedOpsChanged() {

    }

    synchronized void endpointHandler(Handler<MqttEndpoint> handler) {
        this.endpointHandler = handler;
    }

    /**
     * Used for calling the endpoint handler when a connection is established with a remote MQTT client
     *
     * @param endpoint  the local endpoint for MQTT point-to-point communication with remote
     */
    synchronized void handleConnect(MqttEndpointImpl endpoint) {

        if (this.endpointHandler != null) {
            this.endpointHandler.handle(endpoint);
            this.endpoint = endpoint;
        }
    }

    /**
     * Used for calling the subscribe handler when the remote MQTT client subscribes to topics
     *
     * @param msg   message with subscribe information
     */
    synchronized void handleSubscribe(MqttSubscribeMessage msg) {

        if (this.endpoint != null) {
            this.endpoint.handleSubscribe(msg);
        }
    }

    /**
     * Used for calling the unsubscribe handler when the remote MQTT client unsubscribe to topics
     *
     * @param msg   message with unsubscribe information
     */
    synchronized void handleUnsubscribe(MqttUnsubscribeMessage msg) {

        if (this.endpoint != null) {
            this.endpoint.handleUnsubscribe(msg);
        }
    }

    /**
     * Used for calling the publish handler when the remote MQTT client publishes a message
     *
     * @param msg   published message
     */
    synchronized  void handlePublish(MqttPublishMessage msg) {

        if (this.endpoint != null) {
            this.endpoint.handlePublish(msg);
        }
    }

    /**
     * Used for calling the disconnect handler when the remote MQTT client disconnects
     */
    synchronized void handleDisconnect() {

        if (this.endpoint != null) {
            this.endpoint.handlerDisconnect();
        }
    }
}
