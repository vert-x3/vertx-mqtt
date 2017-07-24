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
import io.vertx.codegen.annotations.CacheReturn;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.mqtt.messages.impl.MqttConnAckMessageImpl;

/**
 * Represents an MQTT CONNACK message
 */
@VertxGen
public interface MqttConnAckMessage {

  /**
   * Create a concrete instance of a Vert.x connack message
   *
   * @param code  return code from the connection request
   * @param isSessionPresent  is an old session is present
   * @return
   */
  static MqttConnAckMessage create(MqttConnectReturnCode code, boolean isSessionPresent) {
    return new MqttConnAckMessageImpl(code, isSessionPresent);
  }

  /**
   * @return  return code from the connection request
   */
  @CacheReturn
  MqttConnectReturnCode code();

  /**
   * @return  is an old session is present
   */
  @CacheReturn
  boolean isSessionPresent();
}
