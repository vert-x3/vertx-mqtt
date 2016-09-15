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
import io.netty.handler.codec.mqtt.MqttMessage;
import io.vertx.core.Handler;

/**
 * Represents an MQTT endpoint
 */
public class MqttEndpointImpl implements MqttEndpoint {

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
        return null;
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
}
