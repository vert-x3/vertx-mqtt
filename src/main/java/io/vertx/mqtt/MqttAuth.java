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

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

/**
 * MQTT authentication information
 */
@DataObject
public class MqttAuth {

  private final String username;
  private final String password;

  /**
   * Constructor
   *
   * @param username MQTT client username
   * @param password MQTT client password
   */
  public MqttAuth(String username, String password) {
    this.username = username;
    this.password = password;
  }

  /**
   * Create instance from JSON
   *
   * @param json  the JSON
   */
  public MqttAuth(JsonObject json) {
    this.username = json.getString("username");
    this.password = json.getString("password");
  }

  /**
   * @return the username provided by the remote MQTT client
   */
  public String getUsername() {
    return this.username;
  }

  /**
   * @return the password provided by the remote MQTT client
   */
  public String getPassword() {
    return this.password;
  }

  /**
   * Convert instance in JSON
   *
   * @return  JSON representation
   */
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    json.put("username", this.username);
    json.put("password", this.password);
    return json;
  }
}
