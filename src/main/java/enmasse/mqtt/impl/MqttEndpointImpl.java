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
import io.netty.handler.codec.mqtt.*;
import io.vertx.core.Handler;
import io.vertx.core.net.impl.ConnectionBase;

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

    @Override
    public String clientIdentifier() {
        return this.clientIdentifier;
    }

    @Override
    public MqttAuth auth() {
        return this.auth;
    }

    @Override
    public MqttWill will() {
        return this.will;
    }

    @Override
    public boolean isCleanSession() {
        return this.isCleanSession;
    }

    @Override
    public int protocolVersion() { return this.protocolVersion; }

    @Override
    public MqttEndpoint writeConnack(MqttConnectReturnCode connectReturnCode, boolean sessionPresent) {

        MqttFixedHeader fixedHeader =
                new MqttFixedHeader(MqttMessageType.CONNACK, false, MqttQoS.AT_MOST_ONCE, false, 0);
        MqttConnAckVariableHeader variableHeader =
                new MqttConnAckVariableHeader(connectReturnCode, sessionPresent);

        MqttMessage connack = MqttMessageFactory.newMessage(fixedHeader, variableHeader, null);

        this.write(connack);

        return this;
    }

    @Override
    public MqttEndpoint subscribeHandler(Handler<MqttSubscribeMessage> handler) {

        synchronized (this.conn) {
            this.checkClosed();
            this.subscribeHandler = handler;
            return this;
        }
    }

    @Override
    public MqttEndpoint writeSuback(Iterable<Integer> grantedQoSLevels) {

        MqttFixedHeader fixedHeader =
                new MqttFixedHeader(MqttMessageType.SUBACK, false, MqttQoS.AT_MOST_ONCE, false, 0);
        MqttMessageIdVariableHeader variableHeader =
                MqttMessageIdVariableHeader.from(this.nextMessageId());

        MqttSubAckPayload payload = new MqttSubAckPayload(grantedQoSLevels);

        MqttMessage suback = MqttMessageFactory.newMessage(fixedHeader, variableHeader, payload);

        this.write(suback);

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

    @Override
    public void end() {

    }

    @Override
    public void end(MqttMessage mqttMessage) {

    }

    @Override
    public MqttEndpoint drainHandler(Handler<Void> handler) {
        return null;
    }

    @Override
    public MqttEndpoint setWriteQueueMaxSize(int i) {
        return null;
    }

    @Override
    public boolean writeQueueFull() {
        return false;
    }

    @Override
    public MqttEndpoint write(MqttMessage mqttMessage) {

        synchronized (this.conn) {
            this.checkClosed();
            this.conn.writeToChannel(mqttMessage);
            return this;
        }
    }

    @Override
    public MqttEndpoint endHandler(Handler<Void> handler) {
        return null;
    }

    @Override
    public MqttEndpoint resume() {
        return null;
    }

    @Override
    public MqttEndpoint pause() {
        return null;
    }

    @Override
    public MqttEndpoint handler(Handler<MqttMessage> handler) {
        return null;
    }

    @Override
    public MqttEndpoint exceptionHandler(Handler<Throwable> handler) {
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
