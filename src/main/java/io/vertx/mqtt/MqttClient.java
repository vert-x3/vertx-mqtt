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

import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttSubscriptionOption;
import io.netty.handler.codec.mqtt.MqttTopicSubscription;
import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.mqtt.impl.MqttClientImpl;
import io.vertx.mqtt.messages.MqttAuthenticationExchangeMessage;
import io.vertx.mqtt.messages.MqttConnAckMessage;
import io.vertx.mqtt.messages.MqttPublishMessage;
import io.vertx.mqtt.messages.MqttSubAckMessage;
import io.vertx.mqtt.messages.MqttUnsubAckMessage;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.vertx.mqtt.messages.codes.MqttDisconnectReasonCode;
import io.vertx.mqtt.messages.codes.MqttPubAckReasonCode;
import io.vertx.mqtt.messages.codes.MqttPubRecReasonCode;
import io.vertx.mqtt.messages.codes.MqttPubRelReasonCode;
import io.vertx.mqtt.messages.codes.MqttPubCompReasonCode;

import java.util.List;
import java.util.Map;

/**
 * An MQTT client
 */
@VertxGen
public interface MqttClient {

  /**
   * Return an MQTT client instance
   *
   * @param vertx Vert.x instance
   * @param options MQTT client options
   * @return  MQTT client instance
   */
  static MqttClient create(Vertx vertx, MqttClientOptions options) {
    return new MqttClientImpl(vertx, options);
  }

  /**
   * Return an MQTT client instance using the default options
   *
   * @param vertx Vert.x instance
   * @return  MQTT client instance
   */
  static MqttClient create(Vertx vertx) {
    return new MqttClientImpl(vertx, new MqttClientOptions());
  }

  /**
   * Connects to an MQTT server calling connectHandler after connection
   *
   * @param port  port of the MQTT server
   * @param host  hostname/ip address of the MQTT server
   * @return a future notified when the connect call ends
   */
  Future<MqttConnAckMessage> connect(int port, String host);

  /**
   * Connects to an MQTT server calling connectHandler after connection
   *
   * @param port  port of the MQTT server
   * @param host  hostname/ip address of the MQTT server
   * @param serverName  the SNI server name
   * @return a future notified when the connect call ends
   */
  Future<MqttConnAckMessage> connect(int port, String host, String serverName);
  
  /**
   * Connects to an MQTT server calling connectHandler after connection
   *
   * @param port  port of the MQTT server
   * @param host  hostname/ip address of the MQTT server
   * @param serverName  the SNI server name
   * @param userProperties Connect User Properties
   * @return a future notified when the connect call ends
   */
  Future<MqttConnAckMessage> connect(int port, String host, String serverName, Map<String, String> userProperties);

  /**
   * Disconnects from the MQTT server
   *
   * @return a {@code Future} of the asynchronous result
   */
  Future<Void> disconnect();

  /**
   * Disconnects from the MQTT server
   *
   * @param code reason code for the disconnect
   * @param properties MQTT properties
   * @return a {@code Future} of the asynchronous result
   */
  @GenIgnore
  Future<Void> disconnect(MqttDisconnectReasonCode code, MqttProperties properties);

  /**
   * Sends the PUBLISH message to the remote MQTT server
   *
   * @param topic    topic on which the message is published
   * @param payload  message payload
   * @param qosLevel QoS level
   * @param isDup    if the message is a duplicate
   * @param isRetain if the message needs to be retained
   * @return a {@code Future} completed after PUBLISH packet sent with packetid (not when QoS 0)
   */
  Future<Integer> publish(String topic, Buffer payload, MqttQoS qosLevel, boolean isDup, boolean isRetain);

  /**
   * Sends the PUBLISH message to the remote MQTT server with MQTT 5.0 properties
   *
   * @param topic      topic on which the message is published
   * @param payload    message payload
   * @param qosLevel   QoS level
   * @param isDup      if the message is a duplicate
   * @param isRetain   if the message needs to be retained
   * @param properties MQTT 5.0 properties (e.g. message expiry, content type, response topic, user properties)
   * @return a {@code Future} completed after PUBLISH packet sent with packetid (not when QoS 0)
   */
  @GenIgnore
  Future<Integer> publish(String topic, Buffer payload, MqttQoS qosLevel, boolean isDup, boolean isRetain, MqttProperties properties);

  /**
   * Sets a handler which will be called each time the publishing of a message has been completed.
   * <p>
   * For a message that has been published using
   * <ul>
   * <li>QoS 0 this means that the client has successfully sent the corresponding PUBLISH packet,</li>
   * <li>QoS 1 this means that a corresponding PUBACK has been received from the server,</li>
   * <li>QoS 2 this means that a corresponding PUBCOMP has been received from the server.</li>
   * </ul>
   *
   * @param publishCompletionHandler handler called with the packetId
   * @return current MQTT client instance
   */
  @Fluent
  MqttClient publishCompletionHandler(Handler<Integer> publishCompletionHandler);

  /**
   * Sets a handler which will be called when the client does not receive a PUBACK or
   * PUBREC/PUBCOMP for a message published using QoS 1 or 2 respectively.
   * <p>
   * The time to wait for an acknowledgement message can be configured using
   * {@link MqttClientOptions#setAckTimeout(int)}.
   * If the client receives a PUBACK/PUBREC/PUBCOMP for a message after its completion
   * has expired, the handler registered using {@link #publishCompletionUnknownPacketIdHandler(Handler)}
   * will be invoked.
   * <p>
   * Note that this behavior is outside the scope of the MQTT 3.1.1 specification. The client's default
   * behavior is therefore to wait forever for the server's corresponding acknowledgement.
   *
   * @param publishCompletionExpirationHandler the handler to call with the ID of the expired packet
   * @return current MQTT client instance
   */
  @Fluent
  MqttClient publishCompletionExpirationHandler(Handler<Integer> publishCompletionExpirationHandler);

  /**
   * Sends PUBACK packet to server
   *
   * @param publishMessageId identifier of the PUBLISH message to acknowledge
   * @param reasonCode       reason code
   * @param properties       MQTT properties
   * @return a {@code Future} completed after PUBACK packet sent
   */
  @GenIgnore
  Future<Void> publishAcknowledge(int publishMessageId, MqttPubAckReasonCode reasonCode, MqttProperties properties);

  /**
   * Sends PUBREC packet to server
   *
   * @param publishMessageId identifier of the PUBLISH message to acknowledge
   * @param reasonCode       reason code
   * @param properties       MQTT properties
   * @return a {@code Future} completed after PUBREC packet sent
   */
  @GenIgnore
  Future<Void> publishReceived(int publishMessageId, MqttPubRecReasonCode reasonCode, MqttProperties properties);

  /**
   * Sends PUBREL packet to server
   *
   * @param publishMessageId identifier of the PUBLISH message to acknowledge
   * @param reasonCode       reason code
   * @param properties       MQTT properties
   * @return a {@code Future} completed after PUBREL packet sent
   */
  @GenIgnore
  Future<Void> publishRelease(int publishMessageId, MqttPubRelReasonCode reasonCode, MqttProperties properties);

  /**
   * Sends PUBCOMP packet to server
   *
   * @param publishMessageId identifier of the PUBLISH message to acknowledge
   * @param reasonCode       reason code
   * @param properties       MQTT properties
   * @return a {@code Future} completed after PUBCOMP packet sent
   */
  @GenIgnore
  Future<Void> publishComplete(int publishMessageId, MqttPubCompReasonCode reasonCode, MqttProperties properties);

  /**
   * Sets a handler which will be called when the client receives a PUBACK/PUBREC/PUBCOMP with an unknown
   * packet ID.
   *
   * @param publishCompletionPhantomHandler the handler to call with the unknown packet ID
   * @return current MQTT client instance
   */
  @Fluent
  MqttClient publishCompletionUnknownPacketIdHandler(Handler<Integer> publishCompletionPhantomHandler);

  /**
   * Sets handler which will be called each time server publish something to client
   *
   * @param publishHandler handler to call
   * @return current MQTT client instance
   */
  @Fluent
  MqttClient publishHandler(Handler<MqttPublishMessage> publishHandler);

  /**
   * Sets handler which will be called after SUBACK packet receiving
   *
   * @param subscribeCompletionHandler handler to call. List inside is a granted QoS array
   * @return current MQTT client instance
   */
  @Fluent
  MqttClient subscribeCompletionHandler(Handler<MqttSubAckMessage> subscribeCompletionHandler);

  /**
   * Subscribes to the topic with a specified QoS level
   *
   * @param topic topic you subscribe on
   * @param qos   QoS level
   * @return a {@code Future} completed after SUBSCRIBE packet sent with packetid
   */
  Future<Integer> subscribe(String topic, int qos);

  /**
   * Subscribes to the topics with related QoS levels
   *
   * @param topics topics and related QoS levels to subscribe to
   * @return a {@code Future} completed after SUBSCRIBE packet sent with packetid
   */
  Future<Integer> subscribe(Map<String, Integer> topics);

  /**
   * Subscribes to the topics with related QoS levels
   *
   * @param topics topics and related QoS levels to subscribe to
   * @param properties MQTT properties
   * @return a {@code Future} completed after SUBSCRIBE packet sent with packetid
   */
  @GenIgnore
  Future<Integer> subscribe(Map<String, Integer> topics, MqttProperties properties);

  /**
   * Subscribes to a list of topics with MQTT 5.0 subscription options (No Local,
   * Retain As Published, Retain Handling) and optional properties.
   * Each {@link MqttTopicSubscription} carries the topic filter and a
   * {@link MqttSubscriptionOption} that encodes QoS plus the v5 options.
   *
   * @param subscriptions list of topic subscriptions with options
   * @param properties    MQTT properties (e.g. Subscription Identifier)
   * @return a {@code Future} completed after SUBSCRIBE packet sent with packetid
   */
  @GenIgnore
  Future<Integer> subscribe(List<MqttTopicSubscription> subscriptions, MqttProperties properties);


  /**
   * Sets handler which will be called after UNSUBACK packet receiving
   *
   * @param unsubscribeCompletionHandler handler to call with the packetid
   * @return current MQTT client instance
   */
  @Fluent
  MqttClient unsubscribeCompletionHandler(Handler<Integer> unsubscribeCompletionHandler);

  /**
   * Sets handler which will be called after UNSUBACK packet receiving
   *
   * @param unsubscribeCompletionMessageHandler handler to call with the unsubscribe message
   * @return current MQTT client instance
   */
  @Fluent
  MqttClient unsubscribeCompletionMessageHandler(Handler<MqttUnsubAckMessage> unsubscribeCompletionMessageHandler);

  /**
   * Unsubscribe from receiving messages on given topic
   *
   * @param topic Topic you want to unsubscribe from
   * @return a {@code Future} completed after UNSUBSCRIBE packet sent with packetid
   */
  Future<Integer> unsubscribe(String topic);

  /**
   * Unsubscribe from receiving messages on given list of topic
   *
   * @param topics list of topics you want to unsubscribe from
   * @return a {@code Future} completed after UNSUBSCRIBE packet sent with packetid
   */
  Future<Integer> unsubscribe(List<String> topics);

  /**
   * Unsubscribe from receiving messages on given list of topic
   *
   * @param topics list of topics you want to unsubscribe from
   * @param properties MQTT properties
   * @return a {@code Future} completed after UNSUBSCRIBE packet sent with packetid
   */
  @GenIgnore
  Future<Integer> unsubscribe(List<String> topics, MqttProperties properties);

  /**
   * Sets handler which will be called after AUTH packet receiving
   *
   * @param authenticationExchangeHandler handler to call
   * @return current MQTT client instance
   */
  @Fluent
  MqttClient authenticationExchangeHandler(Handler<MqttAuthenticationExchangeMessage> authenticationExchangeHandler);

  /**
   * It is used for Enhanced Authentication and is able to carry an authentication method and authentication data.
   *
   * @param message authentication exchange message
   * @return a {@code Future} completed after AUTH packet sent
   */
  Future<Void> authenticationExchange(MqttAuthenticationExchangeMessage message);

  /**
   * Sets handler which will be called after PINGRESP packet receiving
   *
   * @param pingResponseHandler handler to call
   * @return current MQTT client instance
   */
  @Fluent
  MqttClient pingResponseHandler(Handler<Void> pingResponseHandler);

  /**
   * Set an exception handler for the client, that will be called when an error happens
   * in internal netty structures.
   *
   * {@code io.netty.handler.codec.DecoderException} can be one of the cause
   *
   * @param handler the exception handler
   * @return current MQTT client instance
   */
  @Fluent
  MqttClient exceptionHandler(Handler<Throwable> handler);

  /**
   * Set a handler that will be called when the connection with server is closed
   *
   * @param closeHandler handler to call
   * @return current MQTT client instance
   */
  @Fluent
  MqttClient closeHandler(Handler<Void> closeHandler);

  /**
   * This method is needed by the client in order to avoid server closes the
   * connection due to the keep alive timeout if client has no messages to send
   *
   * @return current MQTT client instance
   */
  @Fluent
  MqttClient ping();

  /**
   * Pause the reading channel, so no new byte are read from the server.
   * Available after connection is established.
   * <p>
   * This simply delegates to {@link io.vertx.core.net.NetSocket#pause()}.
   */
  void pause();


  /**
   * Resume the reading channel. see {@link #pause()}
   * Available after connection is established.
   * <p>
   * This simply delegates to {@link io.vertx.core.net.NetSocket#resume()}.
   */
  void resume();

  /**
   * @return the client identifier
   */
  String clientId();

  /**
   * @return if the connection between client and remote server is established/open
   */
  boolean isConnected();
}
