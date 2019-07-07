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
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
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

  /**
   * Example for handling client connection
   * @param vertx
   */
  public void example1(Vertx vertx) {

    MqttServer mqttServer = MqttServer.create(vertx);
    mqttServer.endpointHandler(endpoint -> {

      // shows main connect info
      System.out.println("MQTT client [" + endpoint.clientIdentifier() + "] request to connect, clean session = " + endpoint.isCleanSession());

      if (endpoint.auth() != null) {
        System.out.println("[username = " + endpoint.auth().getUsername() + ", password = " + endpoint.auth().getPassword() + "]");
      }
      if (endpoint.will() != null) {
        System.out.println("[will topic = " + endpoint.will().getWillTopic() + " msg = " + new String(endpoint.will().getWillMessageBytes()) +
          " QoS = " + endpoint.will().getWillQos() + " isRetain = " + endpoint.will().isWillRetain() + "]");
      }

      System.out.println("[keep alive timeout = " + endpoint.keepAliveTimeSeconds() + "]");

      // accept connection from the remote client
      endpoint.accept(false);

    })
      .listen(ar -> {

        if (ar.succeeded()) {

          System.out.println("MQTT server is listening on port " + ar.result().actualPort());
        } else {

          System.out.println("Error on starting the server");
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

      System.out.println("Received disconnect from client");
    });
  }

  /**
   * Example for handling client connection using SSL/TLS
   * @param vertx
   */
  public void example3(Vertx vertx) {

    MqttServerOptions options = new MqttServerOptions()
      .setPort(8883)
      .setKeyCertOptions(new PemKeyCertOptions()
        .setKeyPath("./src/test/resources/tls/server-key.pem")
        .setCertPath("./src/test/resources/tls/server-cert.pem"))
      .setSsl(true);

    MqttServer mqttServer = MqttServer.create(vertx, options);
    mqttServer.endpointHandler(endpoint -> {

      // shows main connect info
      System.out.println("MQTT client [" + endpoint.clientIdentifier() + "] request to connect, clean session = " + endpoint.isCleanSession());

      if (endpoint.auth() != null) {
        System.out.println("[username = " + endpoint.auth().getUsername() + ", password = " + endpoint.auth().getPassword() + "]");
      }
      if (endpoint.will() != null) {
        System.out.println("[will topic = " + endpoint.will().getWillTopic() + " msg = " + new String(endpoint.will().getWillMessageBytes()) +
          " QoS = " + endpoint.will().getWillQos() + " isRetain = " + endpoint.will().isWillRetain() + "]");
      }

      System.out.println("[keep alive timeout = " + endpoint.keepAliveTimeSeconds() + "]");

      // accept connection from the remote client
      endpoint.accept(false);

    })
      .listen(ar -> {

        if (ar.succeeded()) {

          System.out.println("MQTT server is listening on port " + ar.result().actualPort());
        } else {

          System.out.println("Error on starting the server");
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

      List<MqttQoS> grantedQosLevels = new ArrayList<>();
      for (MqttTopicSubscription s: subscribe.topicSubscriptions()) {
        System.out.println("Subscription for " + s.topicName() + " with QoS " + s.qualityOfService());
        grantedQosLevels.add(s.qualityOfService());
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
        System.out.println("Unsubscription for " + t);
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

      System.out.println("Just received message [" + message.payload().toString(Charset.defaultCharset()) + "] with QoS [" + message.qosLevel() + "]");

      if (message.qosLevel() == MqttQoS.AT_LEAST_ONCE) {
        endpoint.publishAcknowledge(message.messageId());
      } else if (message.qosLevel() == MqttQoS.EXACTLY_ONCE) {
        endpoint.publishReceived(message.messageId());
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

      System.out.println("Received ack for message = " +  messageId);

    }).publishReceivedHandler(messageId -> {

      endpoint.publishRelease(messageId);

    }).publishCompletionHandler(messageId -> {

      System.out.println("Received ack for message = " +  messageId);
    });
  }

  /**
   * Example for being notified by client keep alive
   * @param endpoint
   */
  public void example8(MqttEndpoint endpoint) {

    // handling ping from client
    endpoint.pingHandler(v -> {

      System.out.println("Ping received from client");
    });
  }

  /**
   * Example for closing the server
   * @param mqttServer
   */
  public void example9(MqttServer mqttServer) {

    mqttServer.close(v -> {

      System.out.println("MQTT server closed");
    });
  }

  /**
   * Example for scaling (sharing MQTT servers)
   * @param vertx
   */
  public void example10(Vertx vertx) {

    for (int i = 0; i < 10; i++) {

      MqttServer mqttServer = MqttServer.create(vertx);
      mqttServer.endpointHandler(endpoint -> {
        // handling endpoint
      })
        .listen(ar -> {

          // handling start listening
        });

    }
  }

  /**
   * Example for scaling (sharing MQTT servers)
   * @param vertx
   */
  public void example11(Vertx vertx) {

    DeploymentOptions options = new DeploymentOptions().setInstances(10);
    vertx.deployVerticle("com.mycompany.MyVerticle", options);
  }
}
