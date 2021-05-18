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

package io.vertx.mqtt.impl;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.VertxException;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientSession;
import io.vertx.mqtt.MqttClientSessionOptions;
import io.vertx.mqtt.messages.MqttConnAckMessage;
import io.vertx.mqtt.messages.MqttPublishMessage;
import io.vertx.mqtt.messages.MqttSubAckMessage;

public class MqttClientSessionImpl implements MqttClientSession {

  private static final Logger log = LoggerFactory.getLogger(MqttClientSessionImpl.class);

  private final VertxInternal vertx;
  private final MqttClientSessionOptions options;

  // record the subscriptions
  private final Map<String, RequestedQoS> subscriptions = new HashMap<>();
  // record the pending subscribes
  private final Map<Integer, LinkedHashMap<String, RequestedQoS>> pendingSubscribes = new HashMap<>();
  // record the pending unsubscribes
  private final Map<Integer, List<String>> pendingUnsubscribes = new HashMap<>();

  // the current state
  private SessionState state = SessionState.DISCONNECTED;
  // drives to connection either to CONNECTED or DISCONNECTED
  private boolean running;

  // holds the actual MQTT client connection
  private MqttClient client;
  // an optional reconnect timer
  private Long reconnectTimer;

  private volatile Handler<MqttPublishMessage> messageHandler;
  private volatile Handler<SessionEvent> sessionStateHandler;
  private volatile Handler<SubscriptionEvent> subscriptionStateHandler;
  private volatile Handler<Integer> publishHandler;

  /**
   * Create a new instance, which is not started.
   * @param vertx The vert.x instance to use.
   * @param options The client session options.
   */
  public MqttClientSessionImpl(final Vertx vertx, final MqttClientSessionOptions options) {
    this.vertx = (VertxInternal) vertx;
    this.options = options;

    // validate options
    if (!this.options.isCleanSession()) {
      throw new IllegalArgumentException("MqttClientSessionImpl only works with cleanSession=true");
    }
  }

  @Override
  public void start() {
    this.vertx.runOnContext(x -> doStart());
  }

  @Override
  public void stop() {
    this.vertx.runOnContext(x -> doStop());
  }

  @Override
  public MqttClientSession subscribe(Map<String, RequestedQoS> topics) {
    final Map<String, RequestedQoS> finalTopics = new LinkedHashMap<>(topics);
    this.vertx.runOnContext(x -> doSubscribe(finalTopics));
    return this;
  }

  @Override
  public MqttClientSession unsubscribe(Collection<String> topics) {
    final Set<String> finalTopics = new HashSet<>(topics);
    this.vertx.runOnContext(x -> doUnsubscribe(finalTopics));
    return this;
  }

  private void doStart() {
    if (this.running) {
      // nothing to do
      return;
    }

    this.running = true;
    switch (this.state) {
      case DISCONNECTED:
        // initiate connection
        createConnection();
        break;
      case CONNECTING:
        // nothing to do
        break;
      case CONNECTED:
        // nothing to do
        break;
      case DISCONNECTING:
        // we do nothing here and wait until the disconnection advanced, which will then trigger a re-connect
        break;
    }
  }

  private void doStop() {
    if (!this.running) {
      // nothing to do
      return;
    }

    this.running = false;

    if (this.reconnectTimer != null) {
      // we have a re-connect scheduled, but stop right now.
      this.vertx.cancelTimer(this.reconnectTimer);
    }

    switch (this.state) {
      case CONNECTED:
        closeConnection((Throwable) null);
        break;
      case DISCONNECTED:
        // nothing to do
        break;
      case DISCONNECTING:
        // nothing do do
        break;
      case CONNECTING:
        // we do nothing here and wait, until the connection advanced, which will then trigger a disconnect
        break;
    }
  }

  @Override
  public MqttClientSession sessionStateHandler(Handler<SessionEvent> sessionStateHandler) {
    this.sessionStateHandler = sessionStateHandler;
    return this;
  }

  @Override
  public MqttClientSession subscriptionStateHandler(Handler<SubscriptionEvent> subscriptionStateHandler) {
    this.subscriptionStateHandler = subscriptionStateHandler;
    return this;
  }

  @Override
  public MqttClientSession publishHandler(Handler<Integer> publishHandler) {
    this.publishHandler = publishHandler;
    return this;
  }

  @Override
  public MqttClientSession messageHandler(Handler<MqttPublishMessage> messageHandler) {
    this.messageHandler = messageHandler;
    return this;
  }

  /**
   * Set the state of the session.
   *
   * @param sessionState The new state.
   * @param cause The optional cause, in case of an error.
   */
  private void setState(final SessionState sessionState, final Throwable cause) {

    if (log.isDebugEnabled()) {
      log.debug(String.format("setState - current: %s, next: %s", this.state, sessionState), cause);
    }

    // before announcing our state change

    switch (sessionState) {
      case CONNECTING:
        break;
      case CONNECTED:
        break;
      case DISCONNECTING:
        break;
      case DISCONNECTED:
        this.pendingUnsubscribes.clear();
        this.pendingSubscribes.clear();
        for (String topic : this.subscriptions.keySet()) {
          notifySubscriptionState(topic, SubscriptionState.UNSUBSCRIBED, null);
        }
        break;
    }

    // announce state change

    if (this.state != sessionState) {
      this.state = sessionState;
      Handler<SessionEvent> handler = this.sessionStateHandler;
      if (handler != null) {
        handler.handle(new SessionEvent(sessionState, cause));
      }
    }

    // after announcing out state change

    switch (this.state) {
      case CONNECTING:
        // we just wait for the outcome
        break;
      case CONNECTED:
        if (!this.running) {
          closeConnection((Throwable) null);
        }
        break;
      case DISCONNECTING:
        // we just wait for the outcome
        break;
      case DISCONNECTED:
        if (this.running) {
          scheduleReconnect();
        }
        break;
    }
  }

  private void notifySubscriptionState(final String topic, final SubscriptionState state, final Integer grantedQoS) {

    if (log.isDebugEnabled()) {
      log.debug(String.format("setSubscriptionState - topic: %s, state: %s, grantedQoS: %s", topic, state, grantedQoS));
    }

    Handler<SubscriptionEvent> handler = this.subscriptionStateHandler;
    if (handler != null) {
      handler.handle(new SubscriptionEvent(topic, state, grantedQoS));
    }

  }

  private void scheduleReconnect() {
    log.debug("Scheduling reconnect");

    if (this.reconnectTimer == null) {

      final Duration delay = nextDelay();
      if (log.isDebugEnabled()) {
        log.debug("Next delay: " + delay);
      }

      final long timer = vertx.setTimer(delay.toMillis(), x -> createConnection());
      if (log.isDebugEnabled()) {
        log.debug("Timer set: " + timer);
      }

      this.reconnectTimer = timer;
    }
  }

  /**
   * Calculate the next delay before trying to re-connect.
   *
   * @return The duration to wait.
   */
  private Duration nextDelay() {
    return Duration.ofSeconds(10);
  }

  /**
   * Initiates the connection.
   */
  private void createConnection() {
    log.debug("Creating connection");

    // clear reconnect timer
    this.reconnectTimer = null;

    // create client
    this.client = MqttClient.create(this.vertx, this.options);
    this.client.exceptionHandler(this::closeConnection);
    this.client.closeHandler(x -> connectionClosed());
    this.client.publishHandler(this::serverPublished);
    this.client.subscribeCompletionHandler(this::subscribeCompleted);
    this.client.unsubscribeCompletionHandler(this::unsubscribeCompleted);
    this.client.publishCompletionHandler(this::publishComplete);

    // change state
    setState(SessionState.CONNECTING, null);
    // start connection
    this.client.connect(this.options.getPort(), this.options.getHostname()).onComplete(this::connectCompleted);
  }

  /**
   * Initiates the connection shutdown.
   */
  private void closeConnection(Throwable cause) {
    log.debug("Closing connection", cause);

    setState(SessionState.DISCONNECTING, cause);
    this.client.disconnect().onComplete(this::disconnectCompleted);
  }

  /**
   * Gets called when the connect call was processed.
   *
   * @param result The outcome of the connect call.
   */
  private void connectCompleted(AsyncResult<MqttConnAckMessage> result) {

    if (log.isDebugEnabled()) {
      log.debug(String.format("Connect completed - result: %s, cause: %s", result.result(), result.cause()));
    }

    if (result.failed() || result.result() == null) {
      // this will include CONACKs with error codes
      setState(SessionState.DISCONNECTED, result.cause());
      return;
    }

    setState(SessionState.CONNECTED, null);

    if (!this.subscriptions.isEmpty()) {
      requestSubscribe(new LinkedHashMap<>(this.subscriptions));
    }
  }

  /**
   * Gets called when the disconnect call was processed.
   *
   * @param result The outcome of the disconnect call.
   */
  private void disconnectCompleted(AsyncResult<?> result) {

    if (log.isDebugEnabled()) {
      log.debug(String.format("Disconnect completed - result: %s, cause: %s", result.result(), result.cause()));
    }

    connectionClosed(result.cause());
  }

  /**
   * Gets called internally when the only reasonable action is to just disconnect.
   * <p>
   * If the session is still running, then it will trigger a re-connect.
   *
   * @param reason The reason message.
   */
  private void closeConnection(final String reason) {
    closeConnection(new VertxException(reason).fillInStackTrace());
  }

  /**
   * Gets called when the connection just dropped.
   */
  private void connectionClosed() {
    if (this.state != SessionState.DISCONNECTING) {
      // this came unexpected
      connectionClosed(new VertxException("Connection closed"));
    }
  }

  /**
   * Called to clean up the after a connection was closed.
   *
   * @param cause The cause of the connection closure.
   */
  private void connectionClosed(final Throwable cause) {
    log.info("Connection closed", cause);

    if (this.client != null) {
      this.client.exceptionHandler(null);
      this.client.publishHandler(null);
      this.client.closeHandler(null);
      this.client.subscribeCompletionHandler(null);
      this.client.publishCompletionHandler(null);
      this.client = null;
    }
    setState(SessionState.DISCONNECTED, cause);
  }

  /**
   * Gets called when the server published a message for us.
   *
   * @param message The published message.
   */
  private void serverPublished(MqttPublishMessage message) {
    if (log.isDebugEnabled()) {
      log.debug("Server published: " + message);
    }

    Handler<MqttPublishMessage> publishHandler = this.messageHandler;
    if (publishHandler != null) {
      publishHandler.handle(message);
    }
  }

  /**
   * Perform subscribing.
   *
   * @param topics The topics to subscribe to.
   */
  private void doSubscribe(Map<String, RequestedQoS> topics) {
    final LinkedHashMap<String, RequestedQoS> subscriptions = new LinkedHashMap<>(topics.size());

    for (Map.Entry<String, RequestedQoS> entry : topics.entrySet()) {
      this.subscriptions.compute(entry.getKey(), (key, current) -> {
        if (current != entry.getValue()) {
          subscriptions.put(entry.getKey(), entry.getValue());
        }
        return entry.getValue();
      });
    }

    if (log.isDebugEnabled()) {
      log.debug("Requesting subscribe: " + subscriptions);
    }
    requestSubscribe(subscriptions);
  }

  /**
   * Perform unsubscribing.
   *
   * @param topics The topics to unsubscribe from.
   */
  private void doUnsubscribe(Set<String> topics) {
    final List<String> topicsToSend = new ArrayList<>(topics.size());
    for (String topic : topics) {
      if (this.subscriptions.remove(topic) != null) {
        topicsToSend.add(topic);
      }
    }

    if (log.isDebugEnabled()) {
      log.debug("Requesting unsubscribe: " + topicsToSend);
    }
    requestUnsubscribe(topicsToSend);
  }

  /**
   * Request to subscribe from the server.
   *
   * @param topics The topics to subscribe to, including the requested QoS.
   */
  private void requestSubscribe(LinkedHashMap<String, RequestedQoS> topics) {
    if (topics.isEmpty() || this.client == null) {
      // nothing to do
      return;
    }

    this.client
      .subscribe(topics.entrySet()
        .stream().collect(Collectors.toMap(
          Map.Entry::getKey,
          e -> e.getValue().toInteger()
        )))
      .onComplete(result -> subscribeSent(result, topics));
  }

  /**
   * Request to unsubscribe from the server.
   *
   * @param topics The topic to unsubscribe from.
   */
  private void requestUnsubscribe(List<String> topics) {
    if (topics.isEmpty() || this.client == null) {
      // nothing to do
      return;
    }

    this.client
      .unsubscribe(topics)
      .onComplete(result -> unsubscribeSent(result, topics));
  }

  /**
   * Called when the subscribe call was sent.
   *
   * @param result The result of sending the request, contains the packet id.
   */
  private void subscribeSent(AsyncResult<Integer> result, LinkedHashMap<String, RequestedQoS> topics) {
    if (result.failed() || result.result() == null) {
      // failed
      for (String topic : topics.keySet()) {
        notifySubscriptionState(topic, SubscriptionState.UNSUBSCRIBED, null);
      }
    } else {
      // record request
      for (String topic : topics.keySet()) {
        notifySubscriptionState(topic, SubscriptionState.SUBSCRIBING, null);
      }
      this.pendingSubscribes.put(result.result(), topics);
    }
  }

  /**
   * Called when the unsubscribe call was sent.
   *
   * @param result The result of sending the request, contains the packet id.
   */
  private void unsubscribeSent(AsyncResult<Integer> result, List<String> topics) {
    if (result.failed() || result.result() == null) {
      closeConnection(String.format("Failed to send unsubscribe request: %s", result.cause()));
    } else {
      this.pendingUnsubscribes.put(result.result(), topics);
    }
  }

  /**
   * Called when the server processed the request to subscribe.
   *
   * @param ack The acknowledge message.
   */
  private void subscribeCompleted(MqttSubAckMessage ack) {
    LinkedHashMap<String, RequestedQoS> request = this.pendingSubscribes.remove(ack.messageId());
    if (request == null) {
      closeConnection(String.format("Unexpected subscription ack response - messageId: %s", ack.messageId()));
      return;
    }
    if (request.size() != ack.grantedQoSLevels().size()) {
      closeConnection(String.format("Mismatch of topics on subscription ack - expected: %d, actual: %d", request.size(), ack.grantedQoSLevels().size()));
      return;
    }

    int idx = 0;
    for (String topic : request.keySet()) {
      Integer grantedQoS = ack.grantedQoSLevels().get(idx);
      notifySubscriptionState(topic, SubscriptionState.SUBSCRIBED, grantedQoS);
      idx += 1;
    }
  }

  /**
   * Called when the server processed the request to unsubscribe.
   *
   * @param messageId The ID of the message that completed.
   */
  private void unsubscribeCompleted(Integer messageId) {
    List<String> request = this.pendingUnsubscribes.remove(messageId);
    for (String topic : request) {
      notifySubscriptionState(topic, SubscriptionState.UNSUBSCRIBED, null);
    }
  }

  @Override
  public Future<Integer> publish(String topic, Buffer payload, MqttQoS qosLevel, boolean isDup, boolean isRetain) {
    Promise<Integer> future = Promise.promise();
    this.vertx
      .runOnContext(x -> doPublish(topic, payload, qosLevel, isDup, isRetain)
        .onComplete(future));
    return future.future();
  }

  private Future<Integer> doPublish(String topic, Buffer payload, MqttQoS qosLevel, boolean isDup, boolean isRetain) {
    if (this.client != null) {
      return this.client.publish(topic, payload, qosLevel, isDup, isRetain);
    } else {
      return Future.failedFuture("Session is not connected");
    }
  }

  private void publishComplete(Integer messageId) {
    Handler<Integer> handler = this.publishHandler;
    if (handler != null ) {
      handler.handle(messageId);
    }
  }

}
