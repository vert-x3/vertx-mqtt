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

import io.netty.handler.codec.mqtt.MqttProperties;
import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import static io.netty.handler.codec.mqtt.MqttProperties.MqttPropertyType.*;
/**
 * Will information from the remote MQTT client
 */
@DataObject
public class MqttWill {

  private final boolean isWillFlag;
  private final String willTopic;
  private final Buffer willMessage;
  private final int willQos;
  private final boolean isWillRetain;
  private final MqttProperties willProperties;

  /**
   * Constructor
   *
   * @param isWillFlag   indicates will message presence
   * @param willTopic    topic to publish the will
   * @param willMessage  payload of the will
   * @param willQos      qos level for the will
   * @param isWillRetain if the will message must be retained
   * @param willProperties MQTT properties of the last will message
   */
  public MqttWill(boolean isWillFlag, String willTopic, Buffer willMessage, int willQos, boolean isWillRetain, MqttProperties willProperties) {
    this.isWillFlag = isWillFlag;
    this.willTopic = willTopic;
    this.willMessage = willMessage;
    this.willQos = willQos;
    this.isWillRetain = isWillRetain;
    this.willProperties = willProperties;
  }

  /**
   * Create instance from JSON
   *
   * @param json the JSON
   */
  public MqttWill(JsonObject json) {
    this.isWillFlag = json.getBoolean("isWillFlag");
    this.willTopic = json.getString("willTopic");
    this.willMessage = json.getBuffer("willMessage");
    this.willQos = json.getInteger("willQos");
    this.isWillRetain = json.getBoolean("isWillRetain");
    this.willProperties = propertiesFromJson(json.getJsonArray("willProperties"));
  }

  /**
   * @return the will flag for indicating the will message presence
   */
  public boolean isWillFlag() {
    return this.isWillFlag;
  }

  /**
   * @return the topic for the will as provided by the remote MQTT client
   */
  public String getWillTopic() {
    return this.willTopic;
  }

  /**
   * @return the payload for the will as provided by the remote MQTT client
   */
  public Buffer getWillMessage() {
    return this.willMessage;
  }

  /**
   * @return the payload for the will as provided by the remote MQTT client
   */
  public byte[] getWillMessageBytes() {
    return this.willMessage != null ? this.willMessage.getBytes() : null;
  }

  /**
   * @return the QoS level for the will as provided by the remote MQTT client
   */
  public int getWillQos() {
    return this.willQos;
  }

  /**
   * @return true if the will must be retained as provided by the remote MQTT client
   */
  public boolean isWillRetain() {
    return this.isWillRetain;
  }

  /**
   * @return MQTT properties of the last will message
   */
  public MqttProperties getWillProperties()  {
    return this.willProperties;
  }

  /**
   * Convert instance in JSON
   *
   * @return JSON representation
   */
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    json.put("isWillFlag", this.isWillFlag);
    json.put("willTopic", this.willTopic);
    json.put("willMessage", this.willMessage);
    json.put("willQos", this.willQos);
    json.put("isWillRetain", this.isWillRetain);
    json.put("willProperties", propertiesToJson(this.willProperties));
    return json;
  }

  public static JsonArray propertiesToJson(MqttProperties properties) {
    JsonArray array = new JsonArray();
    for(MqttProperties.MqttProperty<?> prop: properties.listAll()) {
      array.add(propertyToJson(prop));
    }
    return array;
  }

  public static JsonObject propertyToJson(MqttProperties.MqttProperty<?> prop) {
    JsonObject obj = new JsonObject();
    if(prop instanceof MqttProperties.StringProperty ||
      prop instanceof MqttProperties.IntegerProperty) {
      obj.put("id", prop.propertyId());
      obj.put("val", prop.value());
    } else if(prop instanceof MqttProperties.BinaryProperty) {
      obj.put("id", prop.propertyId());
      String value = Base64.getEncoder().encodeToString(((MqttProperties.BinaryProperty) prop).value());
      obj.put("val", value);
    } else if(prop instanceof MqttProperties.UserProperties) {
      for(MqttProperties.StringPair kv: ((MqttProperties.UserProperties) prop).value()) {
        obj.put("id", prop.propertyId());
        obj.put("key", kv.key);
        obj.put("val", kv.value);
      }
    }
    return obj;
  }

  public static MqttProperties propertiesFromJson(JsonArray array) {
    MqttProperties props = new MqttProperties();
    for(Object item: array) {
      props.add(propertyFromJson((JsonObject) item));
    }
    return props;
  }

  public static MqttProperties.MqttProperty<?> propertyFromJson(JsonObject obj) {
    int id = obj.getInteger("id");

    MqttProperties.MqttPropertyType propType = MqttProperties.MqttPropertyType.valueOf(id);
    switch (propType) {
      case PAYLOAD_FORMAT_INDICATOR:
      case REQUEST_PROBLEM_INFORMATION:
      case REQUEST_RESPONSE_INFORMATION:
      case MAXIMUM_QOS:
      case RETAIN_AVAILABLE:
      case WILDCARD_SUBSCRIPTION_AVAILABLE:
      case SUBSCRIPTION_IDENTIFIER_AVAILABLE:
      case SHARED_SUBSCRIPTION_AVAILABLE:
      case SERVER_KEEP_ALIVE:
      case RECEIVE_MAXIMUM:
      case TOPIC_ALIAS_MAXIMUM:
      case TOPIC_ALIAS:
      case PUBLICATION_EXPIRY_INTERVAL:
      case SESSION_EXPIRY_INTERVAL:
      case WILL_DELAY_INTERVAL:
      case MAXIMUM_PACKET_SIZE:
      case SUBSCRIPTION_IDENTIFIER:
        return new MqttProperties.IntegerProperty(id, obj.getInteger("val"));
      case CONTENT_TYPE:
      case RESPONSE_TOPIC:
      case ASSIGNED_CLIENT_IDENTIFIER:
      case AUTHENTICATION_METHOD:
      case RESPONSE_INFORMATION:
      case SERVER_REFERENCE:
      case REASON_STRING:
        return new MqttProperties.StringProperty(id, obj.getString("val"));
      case CORRELATION_DATA:
      case AUTHENTICATION_DATA:
        return new MqttProperties.BinaryProperty(id, Base64.getDecoder().decode(obj.getString("val")));
      case USER_PROPERTY:
        String key = obj.getString("key");
        return new MqttProperties.UserProperty(key, obj.getString("val"));
      default:
        throw new IllegalArgumentException("Unsupported property type: " + propType);
    }
  }
}
