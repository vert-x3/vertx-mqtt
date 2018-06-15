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

import io.netty.util.CharsetUtil;
import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

/**
 * MQTT authentication information
 */
@DataObject
public class MqttAuth {

  private final String userName;
  private final String password;

  /**
   * Constructor
   *
   * @param userName MQTT client username
   * @param password MQTT client password
   */
  public MqttAuth(String userName, String password) {
    this.userName = userName;
    this.password = password;
  }

  /**
   * Constructor
   *
   * @param userName MQTT client username
   * @param passwordBytes MQTT client password
   */
  public MqttAuth(String userName, byte[] passwordBytes) {
    this.userName = userName;
    this.password = (passwordBytes  == null ? null :  new String(passwordBytes, CharsetUtil.UTF_8));;
  }
  /**
   * Create instance from JSON
   *
   * @param json  the JSON
   */
  public MqttAuth(JsonObject json) {
    this.userName = json.getString("userName");
    this.password = json.getString("password");
  }

  /**
   * @return the username provided by the remote MQTT client
   */
  public String userName() {
    return this.userName;
  }

  /**
   * @return the password provided by the remote MQTT client
   */
  public String password() {
    return this.password;
  }

  /**
   * Convert instance in JSON
   *
   * @return  JSON representation
   */
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    json.put("userName", this.userName);
    json.put("password", this.password);
    return json;
  }
}
