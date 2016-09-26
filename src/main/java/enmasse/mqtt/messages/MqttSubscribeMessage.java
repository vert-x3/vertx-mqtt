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
import io.vertx.codegen.annotations.VertxGen;

import java.util.List;

/**
 * Represents an MQTT SUBSCRIBE message
 */
@VertxGen
public interface MqttSubscribeMessage extends MqttMessage {

    int messageId();

    /**
     * List with topics and related quolity of service levels
     * @return
     */
    List<MqttTopicSubscription> topicSubscriptions();
}
