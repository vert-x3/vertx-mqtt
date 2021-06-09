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

import io.netty.handler.codec.mqtt.MqttProperties;
import io.vertx.codegen.annotations.CacheReturn;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.mqtt.messages.codes.MqttPubAckReasonCode;
import io.vertx.mqtt.messages.impl.MqttPubAckMessageImpl;

/**
 * Represents an MQTT PUBACK message
 */
@VertxGen
public interface MqttPubAckMessage {

  /**
   * Create a concrete instance of a Vert.x puback message
   *
   * @param messageId message Id
   * @param code  return code from the puback
   * @param properties MQTT properties of the puback message
   * @return
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  static MqttPubAckMessage create(int messageId, MqttPubAckReasonCode code, MqttProperties properties) {
    return new MqttPubAckMessageImpl(messageId, code, properties);
  }

  @CacheReturn
  int messageId();

  /**
   * @return  reason code from the puback request
   */
  @CacheReturn
  MqttPubAckReasonCode code();

  /**
   * @return MQTT properties
   */
  @CacheReturn
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  MqttProperties properties();
}
