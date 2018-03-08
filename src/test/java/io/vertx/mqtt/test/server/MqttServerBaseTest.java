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
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.MqttServer;
import io.vertx.mqtt.MqttServerOptions;
import org.junit.runner.RunWith;

/**
 * Base class for MQTT server unit tests
 */
@RunWith(VertxUnitRunner.class)
public abstract class MqttServerBaseTest {

  private static final Logger log = LoggerFactory.getLogger(MqttServerBaseTest.class);

  protected static final String MQTT_SERVER_HOST = "localhost";
  protected static final int MQTT_SERVER_PORT = 1883;
  protected static final int MQTT_SERVER_TLS_PORT = 8883;

  protected Vertx vertx;
  protected MqttServer mqttServer;
  protected Throwable rejection;

  /**
   * Setup the needs for starting the MQTT server
   *
   * @param context TestContext instance
   * @param options MQTT server options
   */
  protected void setUp(TestContext context, MqttServerOptions options) {

    this.vertx = Vertx.vertx();
    if (options == null) {
      this.mqttServer = MqttServer.create(this.vertx);
    } else {
      this.mqttServer = MqttServer.create(this.vertx, options);
    }

    // be sure that all other tests will start only if the MQTT server starts correctly
    Async async = context.async();

    this.mqttServer.exceptionHandler(err -> {
      rejection = err;
    });

    this.mqttServer.endpointHandler(endpoint -> endpointHandler(endpoint, context)).listen(ar -> {

      if (ar.succeeded()) {
        log.info("MQTT server listening on port " + ar.result().actualPort());
        async.complete();
      } else {
        log.error("Error starting MQTT server", ar.cause());
        System.exit(1);
      }
    });

    async.awaitSuccess();
  }

  /**
   * Setup the needs for starting the MQTT server
   *
   * @param context TestContext instance
   */
  protected void setUp(TestContext context) {
    this.setUp(context, null);
  }

  /**
   * Teardown the stuff used for testing (i.e. MQTT server)
   *
   * @param context TestContext instance
   */
  protected void tearDown(TestContext context) {

    this.mqttServer.close();
    this.vertx.close();
  }

  protected void endpointHandler(MqttEndpoint endpoint, TestContext context) {

    endpoint.accept(false);
  }
}
