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
import io.vertx.codegen.annotations.CacheReturn;
import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

/**
 * Will information from the remote MQTT client
 */
@DataObject
public class MqttWill {

  private final boolean isWillFlag;
  private final String willTopic;
  private final String willMessage;
  private final int willQos;
  private final boolean isWillRetain;

  /**
   * Constructor
   *
   * @param isWillFlag  indicates will message presence
   * @param willTopic    topic to publish the will
   * @param willMessage  payload of the will
   * @param willQos      qos level for the will
   * @param isWillRetain if the will message must be retained
   */
  public MqttWill(boolean isWillFlag, String willTopic, String willMessage, int willQos, boolean isWillRetain) {
    this.isWillFlag = isWillFlag;
    this.willTopic = willTopic;
    this.willMessage = willMessage;
    this.willQos = willQos;
    this.isWillRetain = isWillRetain;
  }

  /**
   * Constructor
   *
   * @param isWillFlag  indicates will message presence
   * @param willTopic    topic to publish the will
   * @param willMessageBytes  payload of the will
   * @param willQos      qos level for the will
   * @param isWillRetain if the will message must be retained
   */
  public MqttWill(boolean isWillFlag, String willTopic, byte[] willMessageBytes, int willQos, boolean isWillRetain) {
    this.isWillFlag = isWillFlag;
    this.willTopic = willTopic;
    this.willMessage = (willMessageBytes == null ? null : new String(willMessageBytes, CharsetUtil.UTF_8));
    this.willQos = willQos;
    this.isWillRetain = isWillRetain;
  }

  /**
   * Create instance from JSON
   *
   * @param json  the JSON
   */
  public MqttWill(JsonObject json) {
    this.isWillFlag = json.getBoolean("isWillFlag");
    this.willTopic = json.getString("willTopic");
    this.willMessage = json.getString("willMessage");
    this.willQos = json.getInteger("willMessage");
    this.isWillRetain = json.getBoolean("isWillRetain");
  }

  /**
   * @return the will flag for indicating the will message presence
   */
  @CacheReturn
  public boolean isWillFlag() {
    return this.isWillFlag;
  }

  /**
   * @return the topic for the will as provided by the remote MQTT client
   */
  @CacheReturn
  public String willTopic() {
    return this.willTopic;
  }

  /**
   * @return the payload for the will as provided by the remote MQTT client
   */
  @CacheReturn
  public String willMessage() {
    return this.willMessage;
  }

  /**
   * @return the QoS level for the will as provided by the remote MQTT client
   */
  @CacheReturn
  public int willQos() {
    return this.willQos;
  }

  /**
   * @return true if the will must be retained as provided by the remote MQTT client
   */
  @CacheReturn
  public boolean isWillRetain() {
    return this.isWillRetain;
  }

  /**
   * Convert instance in JSON
   *
   * @return  JSON representation
   */
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    json.put("isWillFlag", this.isWillFlag);
    json.put("willTopic", this.willTopic);
    json.put("willMessage", this.willMessage);
    json.put("willQos", this.willQos);
    json.put("isWillRetain", this.isWillRetain);
    return json;
  }
}
