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
import enmasse.mqtt.messages.MqttPublishMessage;
import enmasse.mqtt.messages.MqttSubscribeMessage;
import enmasse.mqtt.messages.MqttUnsubscribeMessage;
import io.netty.channel.Channel;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageIdVariableHeader;
import io.netty.handler.codec.mqtt.MqttPubAckMessage;
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
     * Handle the MQTT message received by the remote MQTT client
     *
     * @param msg
     */
    synchronized void handleMessage(MqttMessage msg) {

        switch (msg.fixedHeader().messageType()) {

            case SUBSCRIBE:

                io.netty.handler.codec.mqtt.MqttSubscribeMessage mqttSubscribeMessage = (io.netty.handler.codec.mqtt.MqttSubscribeMessage) msg;
                this.handleSubscribe(MqttSubscribeMessage.create(mqttSubscribeMessage));
                break;

            case UNSUBSCRIBE:

                io.netty.handler.codec.mqtt.MqttUnsubscribeMessage mqttUnsubscribeMessage = (io.netty.handler.codec.mqtt.MqttUnsubscribeMessage) msg;
                this.handleUnsubscribe(MqttUnsubscribeMessage.create(mqttUnsubscribeMessage));
                break;

            case PUBLISH:

                io.netty.handler.codec.mqtt.MqttPublishMessage mqttPublishMessage = (io.netty.handler.codec.mqtt.MqttPublishMessage) msg;
                this.handlePublish(MqttPublishMessage.create(mqttPublishMessage));
                break;

            case PUBACK:

                MqttPubAckMessage mqttPubackMessage = (MqttPubAckMessage) msg;
                this.handlePuback(mqttPubackMessage.variableHeader().messageId());
                break;

            case PUBREC:

                int pubrecMessageId = ((MqttMessageIdVariableHeader) msg.variableHeader()).messageId();
                this.handlePubrec(pubrecMessageId);
                break;

            case PUBREL:

                int pubrelMessageId = ((MqttMessageIdVariableHeader) msg.variableHeader()).messageId();
                this.handlePubrel(pubrelMessageId);
                break;

            case PUBCOMP:

                int pubcompMessageId = ((MqttMessageIdVariableHeader) msg.variableHeader()).messageId();
                this.handlePubcomp(pubcompMessageId);
                break;

            case PINGREQ:

                this.handlePingreq();
                break;

            case DISCONNECT:

                this.handleDisconnect();
                break;

            default:

                this.channel.pipeline().fireExceptionCaught(new Exception("Wrong message type"));
                break;
        }
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
    synchronized void handlePublish(MqttPublishMessage msg) {

        if (this.endpoint != null) {
            this.endpoint.handlePublish(msg);
        }
    }

    /**
     * Used for calling the puback handler when the remote MQTT client acknowledge a QoS 1 message with puback
     *
     * @param pubackMessageId   identifier of the message acknowledged by the remote MQTT client
     */
    synchronized void handlePuback(int pubackMessageId) {

        if (this.endpoint != null) {
            this.endpoint.handlePuback(pubackMessageId);
        }
    }

    /**
     * Used for calling the pubrec handler when the remote MQTT client acknowledge a QoS 2 message with pubrec
     *
     * @param pubrecMessageId   identifier of the message acknowledged by the remote MQTT client
     */
    synchronized void handlePubrec(int pubrecMessageId) {

        if (this.endpoint != null) {
            this.endpoint.handlePubrec(pubrecMessageId);
        }
    }

    /**
     * Used for calling the pubrel handler when the remote MQTT client acknowledge a QoS 2 message with pubrel
     *
     * @param pubrelMessageId   identifier of the message acknowledged by the remote MQTT client
     */
    synchronized void handlePubrel(int pubrelMessageId) {

        if (this.endpoint != null) {
            this.endpoint.handlePubrel(pubrelMessageId);
        }
    }

    /**
     * Used for calling the pubcomp handler when the remote MQTT client acknowledge a QoS 2 message with pubcomp
     *
     * @param pubcompMessageId   identifier of the message acknowledged by the remote MQTT client
     */
    synchronized void handlePubcomp(int pubcompMessageId) {

        if (this.endpoint != null) {
            this.endpoint.handlePubcomp(pubcompMessageId);
        }
    }

    /**
     * Used internally for handling the pinreq from the remote MQTT client
     */
    synchronized void handlePingreq() {

        if (this.endpoint != null) {
            this.endpoint.handlePingreq();
        }
    }

    /**
     * Used for calling the disconnect handler when the remote MQTT client disconnects
     */
    synchronized void handleDisconnect() {

        if (this.endpoint != null) {
            this.endpoint.handleDisconnect();
        }
    }
}
