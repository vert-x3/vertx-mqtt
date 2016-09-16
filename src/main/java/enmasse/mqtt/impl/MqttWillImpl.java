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

import enmasse.mqtt.MqttWill;

/**
 * Will information from the remote MQTT client
 */
public class MqttWillImpl implements MqttWill {

    private final String willTopic;
    private final String willMessage;
    private final int willQos;
    private final boolean isWillRetain;

    /**
     * Constructor
     *
     * @param willTopic topic to publish the will
     * @param willMessage   payload of the will
     * @param willQos   qos level for the will
     * @param isWillRetain  if the will message must be retained
     */
    MqttWillImpl(String willTopic, String willMessage, int willQos, boolean isWillRetain) {
        this.willTopic = willTopic;
        this.willMessage = willMessage;
        this.willQos = willQos;
        this.isWillRetain = isWillRetain;
    }

    @Override
    public String willTopic() {
        return this.willTopic;
    }

    @Override
    public String willMessage() {
        return this.willMessage;
    }

    @Override
    public int willQos() {
        return this.willQos;
    }

    @Override
    public boolean isWillRetain() {
        return this.isWillRetain;
    }
}
