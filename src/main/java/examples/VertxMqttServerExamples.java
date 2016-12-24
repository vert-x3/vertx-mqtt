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

package examples;

import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.docgen.Source;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.MqttServer;

@Source
public class VertxMqttServerExamples {

  private static final Logger log = LoggerFactory.getLogger(VertxMqttServerExamples.class);

  /**
   * Example for handling client connection
   * @param vertx
   */
  public void example1(Vertx vertx) {

    MqttServer mqttServer = MqttServer.create(vertx);
    mqttServer.endpointHandler(endpoint -> {

      // shows main connect info
      log.info("MQTT client [" + endpoint.clientIdentifier() + "] request to connect, clean session = " + endpoint.isCleanSession());

      if (endpoint.auth() != null) {
        log.info("[username = " + endpoint.auth().userName() + ", password = " + endpoint.auth().password() + "]");
      }
      if (endpoint.will() != null) {
        log.info("[will topic = " + endpoint.will().willTopic() + " msg = " + endpoint.will().willMessage() +
          " QoS = " + endpoint.will().willQos() + " isRetain = " + endpoint.will().isWillRetain() + "]");
      }

      log.info("[keep alive timeout = " + endpoint.keepAliveTimeSeconds() + "]");

      // accept connection from the remote client
      endpoint.accept(false);

    })
      .listen(ar -> {

        if (ar.succeeded()) {

          log.info("MQTT server is listening on port " + ar.result().actualPort());
        } else {

          log.info("Error on starting the server");
          ar.cause().printStackTrace();
        }
      });
  }

  /**
   * Example for handling client disconnection
   * @param endpoint
   */
  public void example2(MqttEndpoint endpoint) {

    // handling disconnect message
    endpoint.disconnectHandler(v -> {

      log.info("Received disconnect from client");
    });
  }
}
