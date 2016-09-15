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

import io.vertx.core.Handler;
import io.vertx.core.streams.ReadStream;

/**
 * A stream used for notifying an MQTT client connection to the MQTT server
 */
public interface MqttEndpointStream extends ReadStream<MqttEndpoint> {

    @Override
    MqttEndpointStream exceptionHandler(Handler<Throwable> handler);

    @Override
    MqttEndpointStream handler(Handler<MqttEndpoint> handler);

    @Override
    MqttEndpointStream pause();

    @Override
    MqttEndpointStream resume();

    @Override
    MqttEndpointStream endHandler(Handler<Void> handler);
}
