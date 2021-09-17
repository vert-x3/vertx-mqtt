/*
 * Copyright 2021 Red Hat Inc.
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

package io.vertx.mqtt;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.mqtt.impl.MqttClientSessionImpl;
import io.vertx.mqtt.messages.MqttPublishMessage;
import io.vertx.mqtt.session.RequestedQoS;
import io.vertx.mqtt.session.SessionEvent;
import io.vertx.mqtt.session.SessionState;
import io.vertx.mqtt.session.SubscriptionEvent;

/**
 * An MQTT client session.
 */
@VertxGen
public interface MqttClientSession {

  /**
   * Create a new MQTT client session.
   * <p>
   * The session will initially be disconnected, and must be started using {@link #start()}.
   *
   * @param vertx Vert.x instance
   * @param options MQTT client session options
   * @return MQTT client session instance
   */
  static MqttClientSession create(Vertx vertx, MqttClientSessionOptions options) {
    return new MqttClientSessionImpl(vertx, options);
  }

  /**
   * Set the session state handler.
   *
   * @param sessionStateHandler The new handler, will overwrite the old one.
   * @return current MQTT client session instance
   */
  @Fluent
  MqttClientSession sessionStateHandler(Handler<SessionEvent> sessionStateHandler);

  /**
   * Set the subscription state handler.
   *
   * @param subscriptionStateHandler The new handler, will overwrite the old one.
   * @return current MQTT client session instance
   */
  @Fluent
  MqttClientSession subscriptionStateHandler(Handler<SubscriptionEvent> subscriptionStateHandler);

  /**
   * Set the publish complete handler.
   *
   * @param publishCompleteHandler The new handler, will overwrite the old one.
   * @return current MQTT client session instance
   * @see MqttClient#publishCompletionHandler(Handler)
   */
  @Fluent
  MqttClientSession publishCompletionHandler(Handler<Integer> publishCompleteHandler);

  /**
   * Set the publish completion expiration handler.
   *
   * @param publishCompletionExpirationHandler The new handler, will overwrite the old one.
   * @return current MQTT client session instance
   * @see MqttClient#publishCompletionExpirationHandler(Handler)
   */
  @Fluent
  MqttClientSession publishCompletionExpirationHandler(Handler<Integer> publishCompletionExpirationHandler);

  /**
   * Set the publish completion unknown packet id handler.
   *
   * @param publishCompletionUnknownPacketIdHandler The new handler, will overwrite the old one.
   * @return current MQTT client session instance
   * @see MqttClient#publishCompletionUnknownPacketIdHandler(Handler)
   */
  @Fluent
  MqttClientSession publishCompletionUnknownPacketIdHandler(Handler<Integer> publishCompletionUnknownPacketIdHandler);

  /**
   * Start the session. This will try to drive the connection to {@link SessionState#CONNECTED}.
   */
  void start();

  /**
   * Stop the session. This will try to drive the connection to {@link SessionState#DISCONNECTED}.
   */
  void stop();

  /**
   * Subscribes to the topics with related QoS levels
   *
   * @param topics topics and related QoS levels to subscribe to
   * @return current MQTT client session instance
   */
  @Fluent
  MqttClientSession subscribe(Map<String, RequestedQoS> topics);

  /**
   * Subscribes to a single topic with related QoS level.
   *
   * @param topic The topic to subscribe to.
   * @param qos The QoS to request from the server.
   * @return current MQTT client session instance
   */
  @Fluent
  default MqttClientSession subscribe(String topic, RequestedQoS qos) {
    return subscribe(Collections.singletonMap(topic, qos));
  }

  /**
   * Subscribes to a list of topics, with the same QoS.
   *
   * @param qos The QoS to use.
   * @param topics The topics to subscribe to.
   * @return current MQTT client session instance
   */
  @Fluent
  @GenIgnore
  default MqttClientSession subscribe(RequestedQoS qos, String... topics) {
    final Map<String, RequestedQoS> topicMap = new LinkedHashMap<>(topics.length);
    for (String topic : topics) {
      topicMap.put(topic, qos);
    }
    return subscribe(topicMap);
  }

  /**
   * Unsubscribe from receiving messages on given topics
   *
   * @param topics Topics you want to unsubscribe from
   * @return current MQTT client session instance
   */
  MqttClientSession unsubscribe(List<String> topics);

  /**
   * Unsubscribe from receiving messages on given topics
   *
   * @param topics Topics you want to unsubscribe from
   * @return current MQTT client session instance
   */
  @GenIgnore
  default MqttClientSession unsubscribe(String... topics) {
    return unsubscribe(Arrays.asList(topics));
  }

  /**
   * Sets handler which will be called each time server publish something to client
   *
   * @param messageHandler handler to call
   * @return current MQTT client session instance
   */
  @Fluent
  MqttClientSession messageHandler(Handler<MqttPublishMessage> messageHandler);

  /**
   * Sends the PUBLISH message to the remote MQTT server
   *
   * @param topic topic on which the message is published
   * @param payload message payload
   * @param qosLevel QoS level
   * @param isDup if the message is a duplicate
   * @param isRetain if the message needs to be retained
   * @return a {@code Future} completed after PUBLISH packet sent with packetid (not when QoS 0)
   */
  Future<Integer> publish(String topic, Buffer payload, MqttQoS qosLevel, boolean isDup, boolean isRetain);

  /**
   * Sends the PUBLISH message to the remote MQTT server
   *
   * @param topic topic on which the message is published
   * @param payload message payload
   * @param qosLevel QoS level
   * @return a {@code Future} completed after PUBLISH packet sent with packetid (not when QoS 0)
   */
  default Future<Integer> publish(String topic, Buffer payload, MqttQoS qosLevel) {
    return publish(topic, payload, qosLevel, false, false);
  }
}
