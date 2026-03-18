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

package io.vertx.mqtt.messages;

import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.vertx.codegen.annotations.CacheReturn;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.buffer.Buffer;
import io.vertx.mqtt.messages.impl.MqttConnAckMessageImpl;

import java.util.Map;

/**
 * Represents an MQTT CONNACK message.
 * <p>
 * MQTT 3.1.1: exposes {@link #code()} and {@link #isSessionPresent()}.
 * MQTT 5.0: additionally exposes all CONNACK properties via typed accessors.
 */
@VertxGen
public interface MqttConnAckMessage {

  /**
   * Create a concrete instance of a Vert.x connack message
   *
   * @param code             return code from the connection request
   * @param isSessionPresent whether an old session is present
   * @return  the connack message
   */
  static MqttConnAckMessage create(MqttConnectReturnCode code, boolean isSessionPresent) {
    return new MqttConnAckMessageImpl(code, isSessionPresent);
  }

  /**
   * Create a concrete instance of a Vert.x connack message with MQTT properties.
   *
   * @param code             return code from the connection request
   * @param isSessionPresent whether an old session is present
   * @param properties       MQTT properties (MQTT 5.0)
   * @return  the connack message
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  static MqttConnAckMessage create(MqttConnectReturnCode code, boolean isSessionPresent, MqttProperties properties) {
    return new MqttConnAckMessageImpl(code, isSessionPresent, properties);
  }

  // -------------------------------------------------------------------------
  // MQTT 3.1.1 fields
  // -------------------------------------------------------------------------

  /**
   * @return return code from the connection request
   */
  @CacheReturn
  MqttConnectReturnCode code();

  /**
   * @return whether an old session is present on the server
   */
  @CacheReturn
  boolean isSessionPresent();

  /**
   * Raw access to MQTT properties. Use the typed accessors below when possible.
   *
   * @return the raw MqttProperties object
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  @CacheReturn
  MqttProperties properties();

  // -------------------------------------------------------------------------
  // MQTT 5.0 properties — typed polyglot-friendly accessors
  // -------------------------------------------------------------------------

  /**
   * Session Expiry Interval in seconds assigned by the server. (MQTT 5.0)
   * <p>
   * If present, overrides the value sent by the client in the CONNECT packet.
   *
   * @return the session expiry interval, or {@code null} if not present
   */
  @Nullable
  Long sessionExpiryInterval();

  /**
   * Receive Maximum: the maximum number of QoS 1 and QoS 2 publications
   * the server is willing to process concurrently. (MQTT 5.0)
   *
   * @return the receive maximum, or {@code null} if not present
   */
  @Nullable
  Integer receiveMaximum();

  /**
   * Maximum QoS level the server supports. (MQTT 5.0)
   * <p>
   * 0 = QoS 0 only, 1 = QoS 0 and 1. If absent, QoS 2 is supported.
   *
   * @return 0 or 1, or {@code null} if absent (meaning QoS 2 is supported)
   */
  @Nullable
  Integer maximumQos();

  /**
   * Whether the server supports retained messages. (MQTT 5.0)
   *
   * @return {@code false} if the server does NOT support retain; {@code null} if absent (meaning retain IS supported)
   */
  @Nullable
  Boolean retainAvailable();

  /**
   * Whether the server supports Subscription Identifiers. (MQTT 5.0)
   * <p>
   * If the server sends {@code 0}, the client MUST NOT include a
   * {@code SUBSCRIPTION_IDENTIFIER} property in any SUBSCRIBE packet.
   * If absent, subscription identifiers are supported (default = 1).
   *
   * @return {@code false} if subscription identifiers are NOT supported;
   *         {@code null} if absent (meaning they ARE supported)
   */
  @Nullable
  Boolean subscriptionIdentifierAvailable();

  /**
   * Maximum packet size the server is willing to accept, in bytes. (MQTT 5.0)
   *
   * @return the maximum packet size, or {@code null} if not present (meaning no limit)
   */
  @Nullable
  Long maximumPacketSize();

  /**
   * Client Identifier assigned by the server. (MQTT 5.0)
   * <p>
   * Present only when the client connected with an empty ClientID and the
   * server assigned one.
   *
   * @return the assigned client identifier, or {@code null} if not present
   */
  @Nullable
  String assignedClientIdentifier();

  /**
   * Topic Alias Maximum: the highest value accepted by the server as a
   * Topic Alias. (MQTT 5.0)
   *
   * @return the topic alias maximum, or {@code null} if not present (meaning 0, i.e. no aliases)
   */
  @Nullable
  Integer topicAliasMaximum();

  /**
   * Human-readable reason string for the result of the connection attempt. (MQTT 5.0)
   *
   * @return the reason string, or {@code null} if not present
   */
  @Nullable
  String reasonString();

  /**
   * User Properties returned by the server in the CONNACK. (MQTT 5.0)
   * <p>
   * Note: MQTT 5.0 allows duplicate keys; duplicate values are silently
   * collapsed to the last value when using this {@code Map} representation.
   *
   * @return key-value user properties, or {@code null} if not present
   */
  @Nullable
  Map<String, String> userProperties();

  /**
   * Keep Alive interval (in seconds) assigned by the server. (MQTT 5.0)
   * <p>
   * If present, the client MUST use this value instead of the one it sent.
   *
   * @return the server keep alive, or {@code null} if the client-requested value should be used
   */
  @Nullable
  Integer serverKeepAlive();

  /**
   * Response Information used to construct the Response Topic. (MQTT 5.0)
   * <p>
   * Only returned if the client set Request Response Information = 1 in CONNECT.
   *
   * @return the response information string, or {@code null} if not present
   */
  @Nullable
  String responseInformation();

  /**
   * Server Reference: another server the client should use to reconnect. (MQTT 5.0)
   * <p>
   * Present when the server wants the client to use a different server.
   *
   * @return the server reference, or {@code null} if not present
   */
  @Nullable
  String serverReference();

  /**
   * Authentication Method used during enhanced authentication. (MQTT 5.0)
   *
   * @return the authentication method name, or {@code null} if not present
   */
  @Nullable
  String authenticationMethod();

  /**
   * Authentication Data used during enhanced authentication. (MQTT 5.0)
   *
   * @return the authentication data, or {@code null} if not present
   */
  @Nullable
  Buffer authenticationData();
}
