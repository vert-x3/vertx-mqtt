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

package io.vertx.mqtt.messages.impl;

import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttProperties.MqttPropertyType;
import io.vertx.core.buffer.Buffer;
import io.vertx.mqtt.messages.MqttConnAckMessage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents an MQTT CONNACK message
 */
public class MqttConnAckMessageImpl implements MqttConnAckMessage {

  private final MqttConnectReturnCode code;
  private final boolean isSessionPresent;
  private final MqttProperties properties;

  /**
   * Constructor
   *
   * @param code  return code from the connection request
   * @param isSessionPresent  is an old session is present
   */
  public MqttConnAckMessageImpl(MqttConnectReturnCode code, boolean isSessionPresent) {
    this(code, isSessionPresent, MqttProperties.NO_PROPERTIES);
  }

  /**
   * Constructor
   *
   * @param code  return code from the connection request
   * @param isSessionPresent  is an old session is present
   * @param properties MQTT properties
   */
  public MqttConnAckMessageImpl(MqttConnectReturnCode code, boolean isSessionPresent, MqttProperties properties) {
    this.code = code;
    this.isSessionPresent = isSessionPresent;
    this.properties = properties != null ? properties : MqttProperties.NO_PROPERTIES;
  }

  // -------------------------------------------------------------------------
  // MQTT 3.1.1
  // -------------------------------------------------------------------------

  @Override
  public MqttConnectReturnCode code() {
    return this.code;
  }

  @Override
  public boolean isSessionPresent() {
    return this.isSessionPresent;
  }

  @Override
  public MqttProperties properties() {
    return this.properties;
  }

  // -------------------------------------------------------------------------
  // MQTT 5.0 typed accessors
  // -------------------------------------------------------------------------

  @Override
  public Long sessionExpiryInterval() {
    MqttProperties.MqttProperty prop = properties.getProperty(MqttPropertyType.SESSION_EXPIRY_INTERVAL.value());
    if (prop instanceof MqttProperties.IntegerProperty) {
      // Treat as unsigned 32-bit
      return ((MqttProperties.IntegerProperty) prop).value().longValue() & 0xFFFFFFFFL;
    }
    return null;
  }

  @Override
  public Integer receiveMaximum() {
    return intProp(MqttPropertyType.RECEIVE_MAXIMUM);
  }

  @Override
  public Integer maximumQos() {
    return intProp(MqttPropertyType.MAXIMUM_QOS);
  }

  @Override
  public Boolean retainAvailable() {
    Integer v = intProp(MqttPropertyType.RETAIN_AVAILABLE);
    return v != null ? v != 0 : null;
  }

  @Override
  public Long maximumPacketSize() {
    MqttProperties.MqttProperty prop = properties.getProperty(MqttPropertyType.MAXIMUM_PACKET_SIZE.value());
    if (prop instanceof MqttProperties.IntegerProperty) {
      return ((MqttProperties.IntegerProperty) prop).value().longValue() & 0xFFFFFFFFL;
    }
    return null;
  }

  @Override
  public String assignedClientIdentifier() {
    return stringProp(MqttPropertyType.ASSIGNED_CLIENT_IDENTIFIER);
  }

  @Override
  public Integer topicAliasMaximum() {
    return intProp(MqttPropertyType.TOPIC_ALIAS_MAXIMUM);
  }

  @Override
  public String reasonString() {
    return stringProp(MqttPropertyType.REASON_STRING);
  }

  @Override
  public Map<String, String> userProperties() {
    MqttProperties.MqttProperty raw = properties.getProperty(MqttPropertyType.USER_PROPERTY.value());
    if (!(raw instanceof MqttProperties.UserProperties)) {
      return null;
    }
    List<MqttProperties.StringPair> pairs = ((MqttProperties.UserProperties) raw).value();
    if (pairs == null || pairs.isEmpty()) {
      return null;
    }
    Map<String, String> result = new LinkedHashMap<>();
    for (MqttProperties.StringPair pair : pairs) {
      result.put(pair.key, pair.value);
    }
    return result;
  }

  @Override
  public Integer serverKeepAlive() {
    return intProp(MqttPropertyType.SERVER_KEEP_ALIVE);
  }

  @Override
  public String responseInformation() {
    return stringProp(MqttPropertyType.RESPONSE_INFORMATION);
  }

  @Override
  public String serverReference() {
    return stringProp(MqttPropertyType.SERVER_REFERENCE);
  }

  @Override
  public String authenticationMethod() {
    return stringProp(MqttPropertyType.AUTHENTICATION_METHOD);
  }

  @Override
  public Buffer authenticationData() {
    MqttProperties.MqttProperty prop = properties.getProperty(MqttPropertyType.AUTHENTICATION_DATA.value());
    if (prop instanceof MqttProperties.BinaryProperty) {
      byte[] bytes = ((MqttProperties.BinaryProperty) prop).value();
      return bytes != null ? Buffer.buffer(bytes) : null;
    }
    return null;
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private Integer intProp(MqttPropertyType type) {
    MqttProperties.MqttProperty prop = properties.getProperty(type.value());
    if (prop instanceof MqttProperties.IntegerProperty) {
      return ((MqttProperties.IntegerProperty) prop).value();
    }
    return null;
  }

  private String stringProp(MqttPropertyType type) {
    MqttProperties.MqttProperty prop = properties.getProperty(type.value());
    if (prop instanceof MqttProperties.StringProperty) {
      return ((MqttProperties.StringProperty) prop).value();
    }
    return null;
  }
}
