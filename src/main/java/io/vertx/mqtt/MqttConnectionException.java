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

/**
 * Exception raised when a connection request fails at MQTT level
 */
public class MqttConnectionException extends Throwable {

  private final MqttConnectReturnCode code;

  /**
   * @return Return code from the CONNACK message
   */
  public MqttConnectReturnCode code() {
    return this.code;
  }

  /**
   * Constructor
   *
   * @param code  return code from the CONNACK message
   */
  public MqttConnectionException(MqttConnectReturnCode code) {
    super(String.format("Connection failed: %s", code));
    this.code = code;
  }
}
