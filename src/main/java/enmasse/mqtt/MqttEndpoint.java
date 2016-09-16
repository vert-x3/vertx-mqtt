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

package enmasse.mqtt;

import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttSubscribeMessage;
import io.vertx.core.Handler;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;

/**
 * Represents an MQTT endpoint
 */
public interface MqttEndpoint extends ReadStream<MqttMessage>, WriteStream<MqttMessage> {

    @Override
    MqttEndpoint exceptionHandler(Handler<Throwable> handler);

    @Override
    MqttEndpoint handler(Handler<MqttMessage> handler);

    @Override
    MqttEndpoint pause();

    @Override
    MqttEndpoint resume();

    @Override
    MqttEndpoint endHandler(Handler<Void> handler);

    @Override
    MqttEndpoint write(MqttMessage mqttMessage);

    @Override
    MqttEndpoint setWriteQueueMaxSize(int i);

    @Override
    MqttEndpoint drainHandler(Handler<Void> handler);

    @Override
    void end();

    /**
     * Client identifier as provided by the MQTT remote client
     * @return
     */
    String clientIdentifier();

    /**
     * Authentication information as provided by the MQTT remote client
     * @return
     */
    MqttAuth auth();

    /**
     * Will information as provided by the MQTT remote client
     * @return
     */
    MqttWill will();

    /**
     * If clean session is requested by the MQTT remote client
     * @return
     */
    boolean isCleanSession();

    /**
     * Sends the CONNACK message to the MQTT remote client
     *
     * @param connectReturnCode     the connect return code
     * @param sessionPresent        if a previous session is present
     * @return  a reference to this, so the API can be used fluently
     */
    MqttEndpoint writeConnack(MqttConnectReturnCode connectReturnCode, boolean sessionPresent);

    /**
     * Set a subscribe handler on the MQTT endpoint. This handler is called when a SUBSCRIBE
     * message is received by the MQTT remote client
     *
     * @param handler   the handler
     * @return  a reference to this, so the API can be used fluently
     */
    MqttEndpoint subscribeHandler(Handler<MqttSubscribeMessage> handler);

    /**
     * Sends the SUBACK message to the MQTT remote client
     *
     * @param grantedQoSLevels  granted QoS levels for the requested topics
     * @return  a reference to this, so the API can be used fluently
     */
    MqttEndpoint writeSuback(Iterable<Integer> grantedQoSLevels);
}
