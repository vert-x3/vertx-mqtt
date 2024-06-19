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

package io.vertx.mqtt.test.server;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.mqtt.MqttServer;
import io.vertx.mqtt.MqttServerOptions;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * MQTT server testing
 */
@RunWith(VertxUnitRunner.class)
public class MqttServerTest {

  private static final Logger log = LoggerFactory.getLogger(MqttServerTest.class);

  protected static final String MQTT_SERVER_HOST = "localhost";
  protected static final int MQTT_SERVER_PORT = 1883;

  private Vertx vertx;

  @Before
  public void before() {

    this.vertx = Vertx.vertx();
  }

  @After
  public void after(TestContext context) {

    this.vertx.close().onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void listenWithoutEndpointHandler(TestContext context) {
    MqttServer server = MqttServer.create(this.vertx, new MqttServerOptions().setHost(MQTT_SERVER_HOST).setPort(MQTT_SERVER_PORT));
    server.listen().onComplete(context.asyncAssertFailure(err -> {
      context.assertEquals(IllegalStateException.class, err.getClass());
    }));
  }

  @Test
  public void sharedServersRoundRobin(TestContext context) {

    int numServers = VertxOptions.DEFAULT_EVENT_LOOP_POOL_SIZE / 2- 1;
    int numConnections = numServers * 20;

    List<MqttServer> servers = new ArrayList<>();
    Set<MqttServer> connectedServers = ConcurrentHashMap.newKeySet();

    CountDownLatch latchListen = new CountDownLatch(numServers);
    CountDownLatch latchConns = new CountDownLatch(numConnections);
    Map<MqttServer, Integer> connectCount = new ConcurrentHashMap<>();

    try {

      for (int i = 0; i < numServers; i++) {

        MqttServer server = MqttServer.create(this.vertx, new MqttServerOptions().setHost(MQTT_SERVER_HOST).setPort(MQTT_SERVER_PORT));
        servers.add(server);

        server.endpointHandler(endpoint -> {

          connectedServers.add(server);

          Integer cnt = connectCount.get(server);
          int icnt = cnt == null ? 0 : cnt;
          icnt++;
          connectCount.put(server, icnt);

          endpoint.accept(false);
          latchConns.countDown();

        }).listen().onComplete(ar -> {

          if (ar.succeeded()) {
            log.info("MQTT server listening on port " + ar.result().actualPort());
            latchListen.countDown();
          } else {
            log.error("Error starting MQTT server", ar.cause());
          }
        });
      }

      context.assertTrue(latchListen.await(10, TimeUnit.SECONDS));

      // starting Eclipse Paho clients is synchronous
      for (int i = 0; i < numConnections; i++) {

        String clientId = String.format("client-%d", i);
        try {

          MemoryPersistence persistence = new MemoryPersistence();
          MqttClient client = new MqttClient(String.format("tcp://%s:%d", MQTT_SERVER_HOST, MQTT_SERVER_PORT), clientId, persistence);
          client.connect();
          log.info("Client connected " + clientId);

        } catch (MqttException e) {

          log.error("Error on connecting client " + clientId, e);
          context.assertTrue(false);
        }
      }
      context.assertTrue(latchConns.await(10, TimeUnit.SECONDS));

      context.assertEquals(numServers, connectedServers.size());
      for (MqttServer server : servers) {
        context.assertTrue(connectedServers.contains(server));
      }
      context.assertEquals(numServers, connectCount.size());
      for (int cnt : connectCount.values()) {
        context.assertEquals(numConnections / numServers, cnt);
      }

      CountDownLatch closeLatch = new CountDownLatch(numServers);

      for (MqttServer server : servers) {
        server.close().onComplete(context.asyncAssertSuccess(ar -> {
          closeLatch.countDown();
        }));
      }

      context.assertTrue(closeLatch.await(10, TimeUnit.SECONDS));

    } catch (InterruptedException e) {

    }
  }

}
