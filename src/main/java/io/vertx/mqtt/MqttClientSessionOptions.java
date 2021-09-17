/*
 * Copyright 2021 Red Hat Inc.
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

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;
import io.vertx.mqtt.session.ConstantReconnectDelayOptions;
import io.vertx.mqtt.session.ReconnectDelayOptions;

@DataObject(generateConverter = true)
public class MqttClientSessionOptions extends MqttClientOptions {

  private static final ReconnectDelayOptions DEFAULT_RECONNECT_DELAY = new ConstantReconnectDelayOptions();

  private String hostname = MqttClientOptions.DEFAULT_HOST;
  private int port = MqttClientOptions.DEFAULT_PORT;
  private ReconnectDelayOptions reconnectDelay = DEFAULT_RECONNECT_DELAY;

  /**
   * Default constructor
   */
  public MqttClientSessionOptions() {
    super();
  }

  /**
   * Create an instance of MqttClientSessionOptions from JSON
   *
   * @param json the JSON
   */
  public MqttClientSessionOptions(JsonObject json) {
    super(json);
    MqttClientSessionOptionsConverter.fromJson(json, this);
  }

  /**
   * Copy constructor
   *
   * @param other the options to copy
   */
  public MqttClientSessionOptions(MqttClientSessionOptions other) {
    super(other);
    this.hostname = other.hostname;
    this.port = other.port;
    this.reconnectDelay= other.reconnectDelay.copy();
  }

  public int getPort() {
    return this.port;
  }

  public MqttClientSessionOptions setPort(int port) {
    this.port = port;
    return this;
  }

  public String getHostname() {
    return this.hostname;
  }

  public MqttClientSessionOptions setHostname(String hostname) {
    this.hostname = hostname;
    return this;
  }

  public MqttClientSessionOptions setReconnectDelay(ReconnectDelayOptions reconnectDelay) {
    this.reconnectDelay = reconnectDelay;
    return this;
  }

  public ReconnectDelayOptions getReconnectDelay() {
    return this.reconnectDelay;
  }
}
