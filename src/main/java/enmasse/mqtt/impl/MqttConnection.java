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
import io.netty.channel.Channel;
import io.vertx.core.Handler;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.impl.ConnectionBase;
import io.vertx.core.spi.metrics.NetworkMetrics;

/**
 * Represents an MQTT connection
 */
public class MqttConnection extends ConnectionBase {

    private Handler<MqttEndpoint> endpointHandler;

    public MqttConnection(VertxInternal vertx, Channel channel, ContextImpl context, NetworkMetrics metrics) {
        super(vertx, channel, context, metrics);
    }

    @Override
    protected Object metric() {
        return null;
    }

    @Override
    protected void handleInterestedOpsChanged() {

    }

    synchronized void endpointHandler(Handler<MqttEndpoint> handler) {
        this.endpointHandler = handler;
    }
}
