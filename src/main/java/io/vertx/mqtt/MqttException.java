/*
 * Copyright 2017 Red Hat Inc.
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

/**
 * Exception raised with a specific reason code
 */
public class MqttException extends Throwable {
  
  private static final long serialVersionUID = -6330343007516479948L;
 
  public final static int MQTT_INVALID_TOPIC_NAME = 0;
  public final static int MQTT_INVALID_TOPIC_FILTER = 1;
  public final static int MQTT_INFLIGHT_QUEUE_FULL = 2;
  public final static int MQTT_QOS_UNSUPPORTED = 3;
  public final static int MQTT_SUBSCRIPTION_IDENTIFIERS_NOT_SUPPORTED = 4;

  private final int code;

  /**
   * Constructor
   *
   * @param code reason code for the exception
   */
  public MqttException(int code) {
    this.code = code;
  }

  /**
   * Constructor
   *
   * @param code reason code for the exception
   * @param message detailed message for the exception
   */
  public MqttException(int code, String message) {
    super(message);
    this.code = code;
  }

  /**
   * @return reason code for the exception
   */
  public int code() {
    return this.code;
  }
}
