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

package enmasse.mqtt.messages;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.buffer.Buffer;

/**
 * Represents an MQTT PUBLISH message
 */
@VertxGen
public interface MqttPublishMessage extends MqttMessage {

    @GenIgnore
    static MqttPublishMessage create(io.netty.handler.codec.mqtt.MqttPublishMessage msg) {

        return new MqttPublishMessage() {

            @Override
            public int messageId() {
        return msg.variableHeader().messageId();
      }

            @Override
            public MqttQoS qosLevel() {
        return msg.fixedHeader().qosLevel();
      }

            @Override
            public Buffer payload() {
        return Buffer.buffer(msg.payload());
      }
        };
    }

    int messageId();

    MqttQoS qosLevel();

    Buffer payload();
}
