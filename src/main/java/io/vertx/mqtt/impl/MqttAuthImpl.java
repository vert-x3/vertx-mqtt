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

package io.vertx.mqtt.impl;

import io.vertx.mqtt.MqttAuth;

/**
 * MQTT authentication information
 */
public class MqttAuthImpl implements MqttAuth {

    private final String userName;
    private final String password;

    /**
     * Constructor
     * @param userName  MQTT client username
     * @param password  MQTT client password
     */
    MqttAuthImpl(String userName, String password) {
        this.userName = userName;
        this.password = password;
    }

    public String userName() {
        return this.userName;
    }

    public String password() { return this.password; }
}
