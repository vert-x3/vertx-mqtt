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

import io.vertx.mqtt.messages.MqttSubAckMessage;

import java.util.List;

/**
 * Represents an MQTT SUBACK message
 */
public class MqttSubAckMessageImpl implements MqttSubAckMessage {

  private final int messageId;
  private final List<Integer> grantedQoSLevels;

  /**
   * Constructor
   *
   * @param messageId message identifier
   * @param grantedQoSLevels  list of granted QoS levels
   */
  public MqttSubAckMessageImpl(int messageId, List<Integer> grantedQoSLevels) {
    this.messageId = messageId;
    this.grantedQoSLevels = grantedQoSLevels;
  }

  public int messageId() {
    return this.messageId;
  }

  public List<Integer> grantedQoSLevels() {
    return grantedQoSLevels;
  }
}
