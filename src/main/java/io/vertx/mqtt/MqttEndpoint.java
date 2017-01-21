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

package io.vertx.mqtt;

import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.codegen.annotations.CacheReturn;
import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.mqtt.messages.MqttPublishMessage;
import io.vertx.mqtt.messages.MqttSubscribeMessage;
import io.vertx.mqtt.messages.MqttUnsubscribeMessage;

import java.util.List;

/**
 * Represents an MQTT endpoint for point-to-point communication with the remote MQTT client
 */
@VertxGen
public interface MqttEndpoint {

  /**
   * Close the endpoint, so the connection with remote MQTT client
   */
  void close();

  /**
   * Client identifier as provided by the remote MQTT client
   *
   * @return
   */
  @CacheReturn
  String clientIdentifier();

  /**
   * Authentication information as provided by the remote MQTT client
   *
   * @return
   */
  @CacheReturn
  MqttAuth auth();

  /**
   * Will information as provided by the remote MQTT client
   *
   * @return
   */
  @CacheReturn
  MqttWill will();

  /**
   * Protocol version required by the remote MQTT client
   *
   * @return
   */
  @CacheReturn
  int protocolVersion();

  /**
   * Protocol name provided by the remote MQTT client
   *
   * @return
   */
  @CacheReturn
  String protocolName();

  /**
   * If clean session is requested by the remote MQTT client
   *
   * @return
   */
  @CacheReturn
  boolean isCleanSession();

  /**
   * Keep alive timeout (in seconds) specified by the remote MQTT client
   *
   * @return
   */
  @CacheReturn
  int keepAliveTimeSeconds();

  /**
   * Message identifier used for last published message
   *
   * @return
   */
  @CacheReturn
  int lastMessageId();

  /**
   * Enable/disable subscription/unsubscription requests auto acknowledge
   *
   * @param isSubscriptionAutoAck auto acknowledge status
   */
  void subscriptionAutoAck(boolean isSubscriptionAutoAck);

  /**
   * Return auto acknowledge status for subscription/unsubscription requests
   *
   * @return
   */
  boolean isSubscriptionAutoAck();

  /**
   * Enable/disable publishing (in/out) auto acknowledge
   *
   * @param isPublishAutoAck auto acknowledge status
   * @return  a reference to this, so the API can be used fluently
   */
  @Fluent
  MqttEndpoint publishAutoAck(boolean isPublishAutoAck);

  /**
   * @return  auto acknowledge status for publishing (in/out)
   */
  boolean isPublishAutoAck();

  /**
   * Enable/disable auto keep alive (sending ping response)
   *
   * @param isAutoKeepAlive auto keep alive
   * @return  a reference to this, so the API can be used fluently
   */
  @Fluent
  MqttEndpoint autoKeepAlive(boolean isAutoKeepAlive);

  /**
   * Return auto keep alive status (sending ping response)
   *
   * @return
   */
  boolean isAutoKeepAlive();

  /**
   * Set a disconnect handler on the MQTT endpoint. This handler is called when a DISCONNECT
   * message is received by the remote MQTT client
   *
   * @param handler the handler
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  MqttEndpoint disconnectHandler(Handler<Void> handler);

  /**
   * Set a subscribe handler on the MQTT endpoint. This handler is called when a SUBSCRIBE
   * message is received by the remote MQTT client
   *
   * @param handler the handler
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  MqttEndpoint subscribeHandler(Handler<MqttSubscribeMessage> handler);

  /**
   * Set a unsubscribe handler on the MQTT endpoint. This handler is called when a UNSUBSCRIBE
   * message is received by the remote MQTT client
   *
   * @param handler the handler
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  MqttEndpoint unsubscribeHandler(Handler<MqttUnsubscribeMessage> handler);

  /**
   * Set the publish handler on the MQTT endpoint. This handler is called when a PUBLISH
   * message is received by the remote MQTT client
   *
   * @param handler the handler
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  MqttEndpoint publishHandler(Handler<MqttPublishMessage> handler);

  /**
   * Set the puback handler on the MQTT endpoint. This handler is called when a PUBACK
   * message is received by the remote MQTT client
   *
   * @param handler the handler
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  MqttEndpoint publishAcknowledgeHandler(Handler<Integer> handler);

  /**
   * Set the pubrec handler on the MQTT endpoint. This handler is called when a PUBREC
   * message is received by the remote MQTT client
   *
   * @param handler the handler
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  MqttEndpoint publishReceivedHandler(Handler<Integer> handler);

  /**
   * Set the pubrel handler on the MQTT endpoint. This handler is called when a PUBREL
   * message is received by the remote MQTT client
   *
   * @param handler the handler
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  MqttEndpoint publishReleaseHandler(Handler<Integer> handler);

  /**
   * Set the pubcomp handler on the MQTT endpoint. This handler is called when a PUBCOMP
   * message is received by the remote MQTT client
   *
   * @param handler the handler
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  MqttEndpoint publishCompleteHandler(Handler<Integer> handler);

  /**
   * Set the pingreq handler on the MQTT endpoint. This handler is called when a PINGREQ
   * message is received by the remote MQTT client. In any case the endpoint sends the
   * PINGRESP internally after executing this handler.
   *
   * @param handler the handler
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  MqttEndpoint pingHandler(Handler<Void> handler);

  /**
   * Set a close handler. This will be called when the MQTT endpoint is closed
   *
   * @param handler the handler
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  MqttEndpoint closeHandler(Handler<Void> handler);

  /**
   * Sends the CONNACK message to the remote MQTT client with "connection accepted"
   * return code. See {@link #reject(MqttConnectReturnCode)} for refusing connection
   *
   * @param sessionPresent  if a previous session is present
   * @return  a reference to this, so the API can be used fluently
     */
  @Fluent
  MqttEndpoint accept(boolean sessionPresent);

  /**
   * Sends the CONNACK message to the remote MQTT client rejecting the connection
   * request with specified return code. See {@link #accept(boolean)} for accepting connection
   *
   * @param returnCode  the connect return code
   * @return  a reference to this, so the API can be used fluently
     */
  @Fluent
  MqttEndpoint reject(MqttConnectReturnCode returnCode);

  /**
   * Sends the SUBACK message to the remote MQTT client
   *
   * @param subscribeMessageId identifier of the SUBSCRIBE message to acknowledge
   * @param grantedQoSLevels   granted QoS levels for the requested topics
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  MqttEndpoint subscribeAcknowledge(int subscribeMessageId, List<Integer> grantedQoSLevels);

  /**
   * Sends the UNSUBACK message to the remote MQTT client
   *
   * @param unsubscribeMessageId identifier of the UNSUBSCRIBE message to acknowledge
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  MqttEndpoint unsubscribeAcknowledge(int unsubscribeMessageId);

  /**
   * Sends the PUBACK message to the remote MQTT client
   *
   * @param publishMessageId identifier of the PUBLISH message to acknowledge
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  MqttEndpoint publishAcknowledge(int publishMessageId);

  /**
   * Sends the PUBREC message to the remote MQTT client
   *
   * @param publishMessageId identifier of the PUBLISH message to acknowledge
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  MqttEndpoint publishReceived(int publishMessageId);

  /**
   * Sends the PUBREL message to the remote MQTT client
   *
   * @param publishMessageId identifier of the PUBLISH message to acknowledge
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  MqttEndpoint publishRelease(int publishMessageId);

  /**
   * Sends the PUBCOMP message to the remote MQTT client
   *
   * @param publishMessageId identifier of the PUBLISH message to acknowledge
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  MqttEndpoint publishComplete(int publishMessageId);

  /**
   * Sends the PUBLISH message to the remote MQTT client
   *
   * @param topic    topic on which the message is published
   * @param payload  message payload
   * @param qosLevel quality of service level
   * @param isDup    if the message is a duplicate
   * @param isRetain if the message needs to be retained
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  MqttEndpoint publish(String topic, Buffer payload, MqttQoS qosLevel, boolean isDup, boolean isRetain);

  /**
   * Sends the PINGRESP message to the remote MQTT client
   *
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  MqttEndpoint pong();
}
