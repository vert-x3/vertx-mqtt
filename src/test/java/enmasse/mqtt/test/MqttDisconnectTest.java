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

package enmasse.mqtt.test;

import enmasse.mqtt.MqttEndpoint;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.vertx.ext.unit.TestContext;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * MQTT server testing about clients disconnections
 */
public class MqttDisconnectTest extends MqttBaseTest {

    @Before
    public void before(TestContext context) {

        this.setUp(context);
    }

    @After
    public void after(TestContext context) {

        this.tearDown(context);
    }

    @Test
    public void disconnect(TestContext context) {

        try {
            MemoryPersistence persistence = new MemoryPersistence();
            MqttClient client = new MqttClient(String.format("tcp://%s:%d", MQTT_SERVER_HOST, MQTT_SERVER_PORT), "12345", persistence);
            client.connect();
            client.disconnect();
            context.assertTrue(true);
        } catch (MqttException e) {
            context.assertTrue(false);
            e.printStackTrace();
        }
    }

    @Override
    protected void endpointHandler(MqttEndpoint endpoint) {

        endpoint.disconnectHandler(v -> {

            System.out.println("MQTT remote client disconnected");

        });

        endpoint.writeConnack(MqttConnectReturnCode.CONNECTION_ACCEPTED, false);
    }
}
