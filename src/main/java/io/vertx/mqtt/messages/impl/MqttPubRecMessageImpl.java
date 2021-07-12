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

import io.netty.handler.codec.mqtt.MqttProperties;
import io.vertx.mqtt.messages.MqttPubRecMessage;
import io.vertx.mqtt.messages.codes.MqttPubRecReasonCode;

/**
 * Represents an MQTT PUBACK message
 */
public class MqttPubRecMessageImpl implements MqttPubRecMessage {

  private final int messageId;
  private final MqttPubRecReasonCode code;
  private final MqttProperties properties;

  /**
   * Constructor
   *
   * @param code  reason code from the disconnect request
   * @param properties MQTT properties of the message
   */
  public MqttPubRecMessageImpl(int messageId, MqttPubRecReasonCode code, MqttProperties properties) {
    this.messageId = messageId;
    this.code = code;
    this.properties = properties;
  }

  public int messageId() {
    return messageId;
  }

  public MqttPubRecReasonCode code() {
    return this.code;
  }

  public MqttProperties properties() {
    return this.properties;
  }
}
