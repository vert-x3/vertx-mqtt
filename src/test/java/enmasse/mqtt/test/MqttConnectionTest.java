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
import enmasse.mqtt.MqttServer;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

/**
 * MQTT server testing about clients connection
 */
@RunWith(VertxUnitRunner.class)
public class MqttConnectionTest {

    private Vertx vertx;
    private MqttServer mqttServer;

    private static final String MQTT_SERVER_HOST = "localhost";
    private static final int MQTT_SERVER_PORT = 1883;

    @Rule
    public TestName name = new TestName();

    @Before
    public void before(TestContext context) {

        this.vertx = Vertx.vertx();
        this.mqttServer = MqttServer.create(this.vertx);

        this.mqttServer.endpointHandler(this::endpointHandler).listen(ar -> {
            if (ar.succeeded()) {
                System.out.println("MQTT server listening on port " + ar.result().actualPort());
            } else {
                System.out.println("Error starting MQTT server");
            }
        });
    }

    @After
    public void after(TestContext context) {

        this.mqttServer.close();
        this.vertx.close();
    }

    @Test
    public void connectionAccepted(TestContext context) {

        try {
            MemoryPersistence persistence = new MemoryPersistence();
            MqttClient client = new MqttClient(String.format("tcp://%s:%d", MQTT_SERVER_HOST, MQTT_SERVER_PORT), "12345", persistence);
            client.connect();
            context.assertTrue(true);
        } catch (MqttException e) {
            context.assertTrue(false);
            e.printStackTrace();
        }
    }

    private void endpointHandler(MqttEndpoint endpoint) {

        TestMethods method = TestMethods.valueOf(name.getMethodName());

        switch (method) {

            case connectionAccepted:
                endpoint.writeConnack(MqttConnectReturnCode.CONNECTION_ACCEPTED, false);
                break;
        }
    }

    private enum TestMethods {
        connectionAccepted
    }
}
