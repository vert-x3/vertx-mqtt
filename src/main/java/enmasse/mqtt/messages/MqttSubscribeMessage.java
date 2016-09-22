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

import enmasse.mqtt.MqttTopicSubscription;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.VertxGen;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Represents an MQTT SUBSCRIBE message
 */
@VertxGen
public interface MqttSubscribeMessage extends MqttMessage {

    @GenIgnore
    static MqttSubscribeMessage create(io.netty.handler.codec.mqtt.MqttSubscribeMessage msg) {

        return new MqttSubscribeMessage() {

            @Override
            public int messageId() {
                return msg.variableHeader().messageId();
            }

            @Override
            public List<MqttTopicSubscription> topicSubscriptions() {

                return msg.payload().topicSubscriptions().stream().map(ts -> {

                    return new MqttTopicSubscription() {

                        @Override
                        public String topicName() {
                            return ts.topicName();
                        }

                        @Override
                        public MqttQoS qualityOfService() {
                            return ts.qualityOfService();
                        }
                    };
                }).collect(Collectors.toList());
            }
        };
    }

    int messageId();

    List<MqttTopicSubscription> topicSubscriptions();

}
