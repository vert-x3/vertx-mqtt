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
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

/**
 * MQTT server testing about clients unsubscription
 */
@RunWith(VertxUnitRunner.class)
public class MqttUnsubscribeTest extends MqttBaseTest {

    private Async async;

    private static final String MQTT_TOPIC = "/my_topic";

    @Before
    public void before(TestContext context) {

        this.setUp(context);
    }

    @After
    public void after(TestContext context) {

        this.tearDown(context);
    }

    @Test
    public void unsubscribe(TestContext context) {

        this.async = context.async();

        try {
            MemoryPersistence persistence = new MemoryPersistence();
            MqttClient client = new MqttClient(String.format("tcp://%s:%d", MQTT_SERVER_HOST, MQTT_SERVER_PORT), "12345", persistence);
            client.connect();

            String[] topics = new String[] { MQTT_TOPIC };
            int[] qos = new int[] { 0 };
            client.subscribe(topics, qos);

            this.async.await();

            client.unsubscribe(topics);

            this.async.await();

            context.assertTrue(true);

        } catch (MqttException e) {

            context.assertTrue(false);
            e.printStackTrace();
        }
    }

    protected void endpointHandler(MqttEndpoint endpoint) {

        endpoint.subscribeHandler(subscribe -> {

            List<Integer> qos = new ArrayList<>();
            qos.add(subscribe.payload().topicSubscriptions().get(0).qualityOfService().value());
            endpoint.writeSuback(subscribe.variableHeader().messageId(), qos);

            this.async.complete();

        }).unsubscribeHandler(unsubscribe -> {

            endpoint.writeUnsuback(unsubscribe.variableHeader().messageId());

            this.async.complete();
        });

        endpoint.writeConnack(MqttConnectReturnCode.CONNECTION_ACCEPTED, false);
    }
}
