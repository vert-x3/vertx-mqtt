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

import enmasse.mqtt.MqttAuth;
import enmasse.mqtt.MqttEndpoint;
import enmasse.mqtt.MqttWill;
import enmasse.mqtt.messages.MqttMessage;
import enmasse.mqtt.messages.MqttPublishMessage;
import enmasse.mqtt.messages.MqttSubscribeMessage;
import enmasse.mqtt.messages.MqttUnsubscribeMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.mqtt.*;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.impl.ConnectionBase;

import java.util.List;

/**
 * Represents an MQTT endpoint for point-to-point communication with the remote MQTT client
 */
public class MqttEndpointImpl implements MqttEndpoint {

    private static final int MAX_MESSAGE_ID = 65535;

    // connection to the remote MQTT client
    private final ConnectionBase conn;

    // information about connected remote MQTT client (from CONNECT message)
    private final String clientIdentifier;
    private final MqttAuthImpl auth;
    private final MqttWill will;
    private final boolean isCleanSession;
    private final int protocolVersion;

    // handler to call when a subscribe request comes in
    private Handler<MqttSubscribeMessage> subscribeHandler;
    // handler to call when a unsubscribe request comes in
    private Handler<MqttUnsubscribeMessage> unsubscribeHandler;
    // handler to call when a publish message comes in
    private Handler<MqttPublishMessage> publishHandler;
    // handler to call when a puback message comes in
    private Handler<Integer> pubackHandler;
    // handler to call when a pubrec message comes in
    private Handler<Integer> pubrecHandler;
    // handler to call when a pubrel message comes in
    private Handler<Integer> pubrelHandler;
    // handler to call when a pubcomp message comes in
    private Handler<Integer> pubcompHandler;
    // handler to call when a disconnect request comes in
    private Handler<Void> disconnectHandler;

    private boolean closed;
    // counter for the message identifier
    private int messageIdCounter;

    /**
     * Constructor
     *
     * @param conn  connection instance with the remote MQTT client
     * @param clientIdentifier  client identifier of the remote
     * @param auth  instance with the authentication information
     * @param will  instance with the will information
     * @param isCleanSession    if the sessione should be cleaned or not
     * @param protocolVersion   protocol version required by the client
     */
    public MqttEndpointImpl(ConnectionBase conn, String clientIdentifier, MqttAuthImpl auth, MqttWillImpl will, boolean isCleanSession, int protocolVersion) {
        this.conn = conn;
        this.clientIdentifier = clientIdentifier;
        this.auth = auth;
        this.will = will;
        this.isCleanSession = isCleanSession;
        this.protocolVersion = protocolVersion;
    }

    public String clientIdentifier() {
        return this.clientIdentifier;
    }

    public MqttAuth auth() {
        return this.auth;
    }

    public MqttWill will() {
        return this.will;
    }

    public boolean isCleanSession() {
        return this.isCleanSession;
    }

    public int protocolVersion() { return this.protocolVersion; }

    public MqttEndpointImpl writeConnack(MqttConnectReturnCode connectReturnCode, boolean sessionPresent) {

        MqttFixedHeader fixedHeader =
                new MqttFixedHeader(MqttMessageType.CONNACK, false, MqttQoS.AT_MOST_ONCE, false, 0);
        MqttConnAckVariableHeader variableHeader =
                new MqttConnAckVariableHeader(connectReturnCode, sessionPresent);

        io.netty.handler.codec.mqtt.MqttMessage connack = MqttMessageFactory.newMessage(fixedHeader, variableHeader, null);

        this.write(connack);

        // if a server sends a CONNACK packet containing a non zero return code it MUST then close the Network Connection (MQTT 3.1.1 spec)
        if (connectReturnCode != MqttConnectReturnCode.CONNECTION_ACCEPTED) {
            this.close();
        }

        return this;
    }

    public MqttEndpointImpl disconnectHandler(Handler<Void> handler) {

        synchronized (this.conn) {
            this.checkClosed();
            this.disconnectHandler = handler;
            return this;
        }
    }

    public MqttEndpointImpl subscribeHandler(Handler<MqttSubscribeMessage> handler) {

        synchronized (this.conn) {
            this.checkClosed();
            this.subscribeHandler = handler;
            return this;
        }
    }

    public MqttEndpointImpl unsubscribeHandler(Handler<MqttUnsubscribeMessage> handler) {

        synchronized (this.conn) {
            this.checkClosed();
            this.unsubscribeHandler = handler;
            return this;
        }
    }

    public MqttEndpointImpl publishHandler(Handler<MqttPublishMessage> handler) {

        synchronized (this.conn) {
            this.checkClosed();
            this.publishHandler = handler;
            return this;
        }
    }

    public MqttEndpointImpl pubackHandler(Handler<Integer> handler) {

        synchronized (this.conn) {
            this.checkClosed();
            this.pubackHandler = handler;
            return this;
        }
    }

    public MqttEndpointImpl pubrecHandler(Handler<Integer> handler) {

        synchronized (this.conn) {
            this.checkClosed();
            this.pubrecHandler = handler;
            return this;
        }
    }

    public MqttEndpointImpl pubrelHandler(Handler<Integer> handler) {

        synchronized (this.conn) {
            this.checkClosed();
            this.pubrelHandler = handler;
            return this;
        }
    }

    public MqttEndpointImpl pubcompHandler(Handler<Integer> handler) {

        synchronized (this.conn) {
            this.checkClosed();
            this.pubcompHandler = handler;
            return this;
        }
    }

    public MqttEndpointImpl writeSuback(int subscribeMessageId, List<Integer> grantedQoSLevels) {

        MqttFixedHeader fixedHeader =
                new MqttFixedHeader(MqttMessageType.SUBACK, false, MqttQoS.AT_MOST_ONCE, false, 0);
        MqttMessageIdVariableHeader variableHeader =
                MqttMessageIdVariableHeader.from(subscribeMessageId);

        MqttSubAckPayload payload = new MqttSubAckPayload(grantedQoSLevels);

        io.netty.handler.codec.mqtt.MqttMessage suback = MqttMessageFactory.newMessage(fixedHeader, variableHeader, payload);

        this.write(suback);

        return this;
    }

    public MqttEndpointImpl writeUnsuback(int unsubscribeMessageId) {

        MqttFixedHeader fixedHeader =
                new MqttFixedHeader(MqttMessageType.UNSUBACK, false, MqttQoS.AT_MOST_ONCE, false , 0);
        MqttMessageIdVariableHeader variableHeader =
                MqttMessageIdVariableHeader.from(unsubscribeMessageId);

        io.netty.handler.codec.mqtt.MqttMessage unsuback = MqttMessageFactory.newMessage(fixedHeader, variableHeader, null);

        this.write(unsuback);

        return this;
    }

    public MqttEndpointImpl writePuback(int publishMessageId) {

        MqttFixedHeader fixedHeader =
                new MqttFixedHeader(MqttMessageType.PUBACK, false, MqttQoS.AT_MOST_ONCE, false , 0);
        MqttMessageIdVariableHeader variableHeader =
                MqttMessageIdVariableHeader.from(publishMessageId);

        io.netty.handler.codec.mqtt.MqttMessage puback = MqttMessageFactory.newMessage(fixedHeader, variableHeader, null);

        this.write(puback);

        return this;
    }

    public MqttEndpointImpl writePubrec(int publishMessageId) {

        MqttFixedHeader fixedHeader =
                new MqttFixedHeader(MqttMessageType.PUBREC, false, MqttQoS.AT_MOST_ONCE, false, 0);
        MqttMessageIdVariableHeader variableHeader =
                MqttMessageIdVariableHeader.from(publishMessageId);

        io.netty.handler.codec.mqtt.MqttMessage pubrec = MqttMessageFactory.newMessage(fixedHeader, variableHeader, null);

        this.write(pubrec);

        return this;
    }

    public MqttEndpointImpl writePubrel(int publishMessageId) {

        MqttFixedHeader fixedHeader =
                new MqttFixedHeader(MqttMessageType.PUBREL, false, MqttQoS.AT_MOST_ONCE, false, 0);
        MqttMessageIdVariableHeader variableHeader =
                MqttMessageIdVariableHeader.from(publishMessageId);

        io.netty.handler.codec.mqtt.MqttMessage pubrel = MqttMessageFactory.newMessage(fixedHeader, variableHeader, null);

        this.write(pubrel);

        return this;
    }

    public MqttEndpointImpl writePubcomp(int publishMessageId) {

        MqttFixedHeader fixedHeader =
                new MqttFixedHeader(MqttMessageType.PUBCOMP, false, MqttQoS.AT_MOST_ONCE, false, 0);
        MqttMessageIdVariableHeader variableHeader =
                MqttMessageIdVariableHeader.from(publishMessageId);

        io.netty.handler.codec.mqtt.MqttMessage pubcomp = MqttMessageFactory.newMessage(fixedHeader, variableHeader, null);

        this.write(pubcomp);

        return this;
    }

    public MqttEndpointImpl writePublish(String topic, Buffer payload, MqttQoS qosLevel, boolean isDup, boolean isRetain) {

        MqttFixedHeader fixedHeader =
                new MqttFixedHeader(MqttMessageType.PUBLISH, isDup, qosLevel, isRetain, 0);
        MqttPublishVariableHeader variableHeader =
                new MqttPublishVariableHeader(topic, this.nextMessageId());

        ByteBuf buf = Unpooled.copiedBuffer(payload.getBytes());

        io.netty.handler.codec.mqtt.MqttMessage publish = MqttMessageFactory.newMessage(fixedHeader, variableHeader, buf);

        this.write(publish);

        return this;
    }

    /**
     * Used for calling the subscribe handler when the remote MQTT client subscribes to topics
     *
     * @param msg   message with subscribe information
     */
    public void handleSubscribe(MqttSubscribeMessage msg) {

        synchronized (this.conn) {
            if (this.subscribeHandler != null) {
                this.subscribeHandler.handle(msg);
            }
        }
    }

    /**
     * Used for calling the unsubscribe handler when the remote MQTT client unsubscribes to topics
     *
     * @param msg   message with unsubscribe information
     */
    public void handleUnsubscribe(MqttUnsubscribeMessage msg) {

        synchronized (this.conn) {
            if (this.unsubscribeHandler != null) {
                this.unsubscribeHandler.handle(msg);
            }
        }
    }

    /**
     * Used for calling the publish handler when the remote MQTT client publishes a message
     *
     * @param msg   published message
     */
    public void handlePublish(MqttPublishMessage msg) {

        synchronized (this.conn) {
            if (this.publishHandler != null) {
                this.publishHandler.handle(msg);
            }
        }
    }

    /**
     * Used for calling the puback handler when the remote MQTT client acknowledge a QoS 1 message with puback
     *
     * @param pubackMessageId   identifier of the message acknowledged by the remote MQTT client
     */
    public void handlePuback(int pubackMessageId) {

        synchronized (this.conn) {
            if (this.pubackHandler != null) {
                this.pubackHandler.handle(pubackMessageId);
            }
        }
    }

    /**
     * Used for calling the pubrec handler when the remote MQTT client acknowledge a QoS 2 message with pubrec
     *
     * @param pubrecMessageId   identifier of the message acknowledged by the remote MQTT client
     */
    public void handlePubrec(int pubrecMessageId) {

        synchronized (this.conn) {
            if (this.pubrecHandler != null) {
                this.pubrecHandler.handle(pubrecMessageId);
            }
        }
    }

    /**
     * Used for calling the pubrel handler when the remote MQTT client acknowledge a QoS 2 message with pubrel
     *
     * @param pubrelMessageId   identifier of the message acknowledged by the remote MQTT client
     */
    public void handlePubrel(int pubrelMessageId) {

        synchronized (this.conn) {
            if (this.pubrelHandler != null) {
                this.pubrelHandler.handle(pubrelMessageId);
            }
        }
    }

    /**
     * Used for calling the pubcomp handler when the remote MQTT client acknowledge a QoS 2 message with pubcomp
     *
     * @param pubcompMessageId   identifier of the message acknowledged by the remote MQTT client
     */
    public void handlePubcomp(int pubcompMessageId) {

        synchronized (this.conn) {
            if (this.pubcompHandler != null) {
                this.pubcompHandler.handle(pubcompMessageId);
            }
        }
    }

    /**
     * Used for calling the disconnect handler when the remote MQTT client disconnects
     */
    public void handlerDisconnect() {

        synchronized (this.conn) {
            if (this.disconnectHandler != null) {
                this.disconnectHandler.handle(null);

                // if client didn't close the connection, the sever SHOULD close it (MQTT spec)
                this.close();
            }
        }
    }

    public void end() { this.close(); }

    public void close() {

        synchronized (this.conn) {
            checkClosed();
            this.conn.close();

            this.closed = true;
        }
    }

    public void end(MqttMessage mqttMessage) {

    }

    public MqttEndpointImpl drainHandler(Handler<Void> handler) {
        return null;
    }

    public MqttEndpointImpl setWriteQueueMaxSize(int i) {
        return null;
    }

    public boolean writeQueueFull() {
        return false;
    }

    public MqttEndpointImpl write(io.netty.handler.codec.mqtt.MqttMessage mqttMessage) {

        synchronized (this.conn) {
            this.checkClosed();
            this.conn.writeToChannel(mqttMessage);
            return this;
        }
    }

    public MqttEndpointImpl write(MqttMessage mqttMessage) {
        throw new UnsupportedOperationException("todo");
    }

    public MqttEndpointImpl endHandler(Handler<Void> handler) {
        return null;
    }

    public MqttEndpointImpl resume() {
        return null;
    }

    public MqttEndpointImpl pause() {
        return null;
    }

    public MqttEndpointImpl handler(Handler<MqttMessage> handler) {
        return null;
    }

    public MqttEndpointImpl exceptionHandler(Handler<Throwable> handler) {
        return null;
    }

    /**
     * Check if the MQTT endpoint is closed
     */
    private void checkClosed() {

        if (this.closed) {
            throw new IllegalStateException("MQTT endpoint is closed");
        }
    }

    /**
     * Update and return the next message identifier
     *
     * @return  message identifier
     */
    private int nextMessageId() {

        // if 0 or MAX_MESSAGE_ID, it becomes 1 (first valid messageId)
        this.messageIdCounter = ((this.messageIdCounter % MAX_MESSAGE_ID) != 0) ? this.messageIdCounter + 1 : 1;
        return this.messageIdCounter;
    }
}
