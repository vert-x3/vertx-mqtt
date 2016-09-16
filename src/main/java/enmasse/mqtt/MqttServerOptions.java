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

import io.vertx.core.net.NetServerOptions;

/**
 * Represents options used by the MQTT server
 */
public class MqttServerOptions extends NetServerOptions {

    public static final int DEFAULT_PORT = 1883; // Default port is 1883 for MQTT

    public MqttServerOptions() {
        super();
        // override the default port
        this.setPort(DEFAULT_PORT);
    }

    @Override
    public MqttServerOptions setPort(int port) {
        super.setPort(port);
        return this;
    }

    @Override
    public MqttServerOptions setHost(String host) {
        super.setHost(host);
        return this;
    }
}
