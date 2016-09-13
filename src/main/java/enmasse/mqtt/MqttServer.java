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

/**
 * An MQTT server
 */
public interface MqttServer {

    /**
     * Start the server listening for incoming connections on the port and host specified
     * @param port  the port to listen on
     * @param host  the host to listen on
     * @return  a reference to this, so the API can be used fluently
     */
    MqttServer listen(int port, String host);
}
