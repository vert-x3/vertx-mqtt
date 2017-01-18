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

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.docgen.Source;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.MqttServer;
import io.vertx.mqtt.MqttServerOptions;
import io.vertx.mqtt.MqttTopicSubscription;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

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

  /**
   * Example for handling client connection using SSL/TLS
   * @param vertx
   */
  public void example3(Vertx vertx) {

    PemKeyCertOptions pemKeyCertOptions = new PemKeyCertOptions()
      .setKeyPath("./src/test/resources/tls/server-key.pem")
      .setCertPath("./src/test/resources/tls/server-cert.pem");

    MqttServerOptions options = new MqttServerOptions()
      .setPort(MqttServerOptions.DEFAULT_TLS_PORT)
      .setKeyCertOptions(pemKeyCertOptions)
      .setSsl(true);

    MqttServer mqttServer = MqttServer.create(vertx, options);
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
   * Example for handling client subscription request
   * @param endpoint
   */
  public void example4(MqttEndpoint endpoint) {

    // handling requests for subscriptions
    endpoint.subscribeHandler(subscribe -> {

      List<Integer> grantedQosLevels = new ArrayList<>();
      for (MqttTopicSubscription s: subscribe.topicSubscriptions()) {
        log.info("Subscription for " + s.topicName() + " with QoS " + s.qualityOfService());
        grantedQosLevels.add(s.qualityOfService().value());
      }
      // ack the subscriptions request
      endpoint.subscribeAcknowledge(subscribe.messageId(), grantedQosLevels);

    });
  }

  /**
   * Example for handling client unsubscription request
   * @param endpoint
   */
  public void example5(MqttEndpoint endpoint) {

    // handling requests for unsubscriptions
    endpoint.unsubscribeHandler(unsubscribe -> {

      for (String t: unsubscribe.topics()) {
        log.info("Unsubscription for " + t);
      }
      // ack the subscriptions request
      endpoint.unsubscribeAcknowledge(unsubscribe.messageId());
    });
  }

  /**
   * Example for handling client published message
   * @param endpoint
   */
  public void example6(MqttEndpoint endpoint) {

    // handling incoming published messages
    endpoint.publishHandler(message -> {

      log.info("Just received message [" + message.payload().toString(Charset.defaultCharset()) + "] with QoS [" + message.qosLevel() + "]");

      if (message.qosLevel() == MqttQoS.AT_LEAST_ONCE) {
        endpoint.publishAcknowledge(message.messageId());
      } else if (message.qosLevel() == MqttQoS.EXACTLY_ONCE) {
        endpoint.publishRelease(message.messageId());
      }

    }).publishReleaseHandler(messageId -> {

      endpoint.publishComplete(messageId);
    });
  }

  /**
   * Example for handling publish message to the client
   * @param endpoint
   */
  public void example7(MqttEndpoint endpoint) {

    // just as example, publish a message with QoS level 2
    endpoint.publish("my_topic",
      Buffer.buffer("Hello from the Vert.x MQTT server"),
      MqttQoS.EXACTLY_ONCE,
      false,
      false);

    // specifing handlers for handling QoS 1 and 2
    endpoint.publishAcknowledgeHandler(messageId -> {

      log.info("Received ack for message = " +  messageId);

    }).publishReceivedHandler(messageId -> {

      endpoint.publishRelease(messageId);

    }).publishCompleteHandler(messageId -> {

      log.info("Received ack for message = " +  messageId);
    });
  }

  /**
   * Example for being notified by client keep alive
   * @param endpoint
   */
  public void example8(MqttEndpoint endpoint) {

    // handling ping from client
    endpoint.pingHandler(v -> {

      log.info("Ping received from client");
    });
  }

  /**
   * Example for closing the server
   * @param mqttServer
   */
  public void example9(MqttServer mqttServer) {

    mqttServer.close(v -> {

      log.info("MQTT server closed");
    });
  }
}