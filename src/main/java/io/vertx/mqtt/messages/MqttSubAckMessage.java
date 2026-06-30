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

import io.vertx.codegen.annotations.CacheReturn;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.mqtt.messages.impl.MqttSubAckMessageImpl;
import io.netty.handler.codec.mqtt.MqttProperties;

import java.util.List;

/**
 * Represents an MQTT SUBACK message
 */
@VertxGen
public interface MqttSubAckMessage extends MqttMessage {

  /**
   * Create a concrete instance of a Vert.x suback message
   *
   * @param messageId message identifier
   * @param grantedQosLevels  list of granted QoS levels
   * @return
   */
  @GenIgnore
  static MqttSubAckMessage create(int messageId, List<Integer> grantedQosLevels) {
    return new MqttSubAckMessageImpl(messageId, grantedQosLevels, MqttProperties.NO_PROPERTIES);
  }

  /**
   * Create a concrete instance of a Vert.x suback message
   *
   * @param messageId message identifier
   * @param grantedQosLevels  list of granted QoS levels
   * @param properties MQTT properties
   * @return
   */
  @GenIgnore
  static MqttSubAckMessage create(int messageId, List<Integer> grantedQosLevels, MqttProperties properties) {
    return new MqttSubAckMessageImpl(messageId, grantedQosLevels, properties);
  }

  /**
   * @return  list of granted QoS levels
   */
  @CacheReturn
  List<Integer> grantedQoSLevels();

  /**
   * @return MQTT properties
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  @CacheReturn
  MqttProperties properties();
}
