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
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.mqtt.impl.MqttClientSessionImpl;
import io.vertx.mqtt.messages.MqttPublishMessage;

/**
 * An MQTT client session.
 */
@VertxGen
public interface MqttClientSession {

  /**
   * The state of the session.
   */
  enum SessionState {
    /**
     * The session is disconnected.
     * <p>
     * A re-connect timer may be pending.
     */
    DISCONNECTED,
    /**
     * The session started to connect.
     * <p>
     * This may include re-subscribing to any topics after the connect call was successful.
     */
    CONNECTING,
    /**
     * The session is connected.
     */
    CONNECTED,
    /**
     * The session is in the process of an orderly disconnect.
     */
    DISCONNECTING,
  }

  /**
   * The state of a subscription.
   * <p>
   * Subscriptions established when a new topic gets added, or the connection was established. If the subscribe call
   * returns an error for the subscription, the state will remain {@link #FAILED} and it will not try to re-subscribe
   * while the connection is active.
   * <p>
   * When the session (connection) disconnects, all subscriptions will automatically be reset to {@link #UNSUBSCRIBED}.
   */
  enum SubscriptionState {
    /**
     * The topic is not subscribed.
     */
    UNSUBSCRIBED,
    /**
     * The topic is in the process of subscribing.
     */
    SUBSCRIBING,
    /**
     * The topic is subscribed.
     */
    SUBSCRIBED,
    /**
     * The topic could not be subscribed.
     */
    FAILED,
  }

  /**
   * The requested QoS level.
   * <p>
   * NOTE: This is missing QoS 2, as this mode is not properly supported by the session.
   */
  enum RequestedQoS {
    QOS_0(0),
    QOS_1(1);

    private final int value;

    RequestedQoS(int value) {
      this.value = value;
    }

    public int toInteger() {
      return this.value;
    }
  }

  /**
   * An event of a session state change.
   */
  class SessionEvent {

    private final SessionState sessionState;
    private final Throwable cause;

    public SessionEvent(final SessionState sessionState, final Throwable reason) {
      this.sessionState = sessionState;
      this.cause = reason;
    }

    /**
     * The new state of the session.
     *
     * @return The state.
     */
    public SessionState getSessionState() {
      return this.sessionState;
    }

    /**
     * The (optional) cause of change.
     *
     * @return The throwable that causes the state change, or {@code null}, if there was none.
     */
    public Throwable getCause() {
      return this.cause;
    }
  }

  /**
   * An event of a subscription state change.
   */
  class SubscriptionEvent {
    private final String topic;
    private final SubscriptionState subscriptionState;
    private final Integer qos;

    public SubscriptionEvent(final String topic, final SubscriptionState subscriptionState, final Integer qos) {
      this.topic = topic;
      this.subscriptionState = subscriptionState;
      this.qos = qos;
    }

    /**
     * The granted QoS level from the server.
     *
     * @return When the state changed to {@link SubscriptionState#SUBSCRIBED}, it contains the QoS level granted by
     *   the server. Otherwise it will be {@code null}.
     */
    public Integer getQos() {
      return this.qos;
    }

    /**
     * The new subscription state.
     *
     * @return The state.
     */
    public SubscriptionState getSubscriptionState() {
      return this.subscriptionState;
    }

    /**
     * The name of the topic this change refers to.
     *
     * @return The topic name.
     */
    public String getTopic() {
      return this.topic;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      SubscriptionEvent that = (SubscriptionEvent) o;
      return topic.equals(that.topic) && subscriptionState == that.subscriptionState && Objects.equals(qos, that.qos);
    }

    @Override
    public int hashCode() {
      return Objects.hash(topic, subscriptionState, qos);
    }

    @Override
    public String toString() {
      return new StringJoiner(", ", SubscriptionEvent.class.getSimpleName() + "[", "]")
        .add("topic='" + topic + "'")
        .add("subscriptionState=" + subscriptionState)
        .add("qos=" + qos)
        .toString();
    }
  }

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
  MqttClientSession unsubscribe(Collection<String> topics);

  /**
   * Unsubscribe from receiving messages on given topics
   *
   * @param topics Topics you want to unsubscribe from
   * @return current MQTT client session instance
   */
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
