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

package io.vertx.mqtt.messages;

import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.VertxGen;

import java.util.List;

/**
 * Represents an MQTT UNSUBSCRIBE message
 */
@VertxGen
public interface MqttUnsubscribeMessage extends MqttMessage {

    /**
     * Create a concrete instance of a Vert.x unsubscribe message
     *
     * @param messageId message identifier
     * @param topics    list of topics to unsubscribe
     */
    @GenIgnore
    static MqttUnsubscribeMessageImpl create(int messageId, List<String> topics) {

        return new MqttUnsubscribeMessageImpl(messageId, topics);
    }

    /**
     * List of topics to unsubscribe
     * @return
     */
    List<String> topics();
}
