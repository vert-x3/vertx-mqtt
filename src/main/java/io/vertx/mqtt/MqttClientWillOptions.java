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
import io.vertx.codegen.json.annotations.JsonGen;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Options for the MQTT Last Will and Testament (LWT) message.
 * <p>
 * Supports both MQTT 3.1.1 (topic, payload, QoS, retain) and MQTT 5.0
 * additional properties (Will Delay Interval, Payload Format Indicator,
 * Content Type, Response Topic, Correlation Data, User Properties).
 */
@DataObject
@JsonGen(publicConverter = false)
public class MqttClientWillOptions {

  public static final int DEFAULT_WILL_QOS = 0;
  public static final boolean DEFAULT_WILL_RETAIN = false;

  // MQTT 3.1.1 fields
  private String topic;
  private Buffer messageBytes;
  private int qos = DEFAULT_WILL_QOS;
  private boolean retain = DEFAULT_WILL_RETAIN;

  // MQTT 5.0 only fields
  /** Will Delay Interval in seconds (0..4294967295). Null means not set. */
  private Long willDelayInterval;
  /**
   * Payload Format Indicator: 0 = unspecified bytes, 1 = UTF-8 encoded.
   * Null means not set.
   */
  private Integer payloadFormatIndicator;
  /** MIME type describing the content of the will payload. */
  private String contentType;
  /** Topic name for a request message. */
  private String responseTopic;
  /** Correlation data used to correlate a request message. */
  private Buffer correlationData;
  /**
   * User properties as key-value pairs. (MQTT 5.0 only)
   */
  private Map<String, String> userProperties;

  /** Default constructor. */
  public MqttClientWillOptions() {
  }

  /** Copy constructor. */
  public MqttClientWillOptions(MqttClientWillOptions other) {
    this.topic = other.topic;
    this.messageBytes = other.messageBytes;
    this.qos = other.qos;
    this.retain = other.retain;
    this.willDelayInterval = other.willDelayInterval;
    this.payloadFormatIndicator = other.payloadFormatIndicator;
    this.contentType = other.contentType;
    this.responseTopic = other.responseTopic;
    this.correlationData = other.correlationData;
    this.userProperties = other.userProperties != null ? new LinkedHashMap<>(other.userProperties) : null;
  }

  /** Create instance from JSON (delegates to generated converter). */
  public MqttClientWillOptions(JsonObject json) {
    MqttClientWillOptionsConverter.fromJson(json, this);
  }

  /** Convert instance to JSON (delegates to generated converter). */
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    MqttClientWillOptionsConverter.toJson(this, json);
    return json;
  }

  // -------------------------------------------------------------------------
  // MQTT 3.1.1 getters / setters
  // -------------------------------------------------------------------------

  /**
   * @return topic on which the will message will be published
   */
  public String getTopic() {
    return topic;
  }

  /**
   * Set the topic on which the will message will be published.
   *
   * @param topic will topic
   * @return this options instance
   */
  public MqttClientWillOptions setTopic(String topic) {
    this.topic = topic;
    return this;
  }

  /**
   * @return will message payload bytes
   */
  public Buffer getMessageBytes() {
    return messageBytes;
  }

  /**
   * Set the will message payload.
   *
   * @param messageBytes will payload
   * @return this options instance
   */
  public MqttClientWillOptions setMessageBytes(Buffer messageBytes) {
    this.messageBytes = messageBytes;
    return this;
  }

  /**
   * @return QoS level for the will message (0, 1 or 2)
   */
  public int getQos() {
    return qos;
  }

  /**
   * Set the QoS level for the will message.
   *
   * @param qos QoS level (0, 1 or 2)
   * @return this options instance
   */
  public MqttClientWillOptions setQos(int qos) {
    this.qos = qos;
    return this;
  }

  /**
   * @return whether the will message must be retained
   */
  public boolean isRetain() {
    return retain;
  }

  /**
   * Set whether the will message must be retained.
   *
   * @param retain true to retain the will message
   * @return this options instance
   */
  public MqttClientWillOptions setRetain(boolean retain) {
    this.retain = retain;
    return this;
  }

  // -------------------------------------------------------------------------
  // MQTT 5.0 getters / setters
  // -------------------------------------------------------------------------

  /**
   * @return Will Delay Interval in seconds, or null if not set (MQTT 5.0)
   */
  public Long getWillDelayInterval() {
    return willDelayInterval;
  }

  /**
   * Set the Will Delay Interval.
   * <p>
   * The broker delays publishing the will message until this interval (in seconds)
   * elapses after the network connection is closed, or until the session ends.
   * If null, the broker publishes the will message immediately. (MQTT 5.0 only)
   *
   * @param willDelayInterval delay in seconds (0..4294967295), or null to unset
   * @return this options instance
   */
  public MqttClientWillOptions setWillDelayInterval(Long willDelayInterval) {
    if (willDelayInterval != null && (willDelayInterval < 0L || willDelayInterval > 0xFFFFFFFFL)) {
      throw new IllegalArgumentException("Invalid Will Delay Interval: " + willDelayInterval);
    }
    this.willDelayInterval = willDelayInterval;
    return this;
  }

  /**
   * @return Payload Format Indicator (0=bytes, 1=UTF-8), or null if not set (MQTT 5.0)
   */
  public Integer getPayloadFormatIndicator() {
    return payloadFormatIndicator;
  }

  /**
   * Set the Payload Format Indicator.
   * <p>
   * 0 means the will payload is unspecified bytes; 1 means it is UTF-8 encoded.
   * (MQTT 5.0 only)
   *
   * @param payloadFormatIndicator 0 or 1, or null to unset
   * @return this options instance
   */
  public MqttClientWillOptions setPayloadFormatIndicator(Integer payloadFormatIndicator) {
    if (payloadFormatIndicator != null && (payloadFormatIndicator < 0 || payloadFormatIndicator > 1)) {
      throw new IllegalArgumentException("Payload Format Indicator must be 0 or 1");
    }
    this.payloadFormatIndicator = payloadFormatIndicator;
    return this;
  }

  /**
   * @return Content Type (MIME type) of the will payload, or null if not set (MQTT 5.0)
   */
  public String getContentType() {
    return contentType;
  }

  /**
   * Set the Content Type (MIME type) that describes the will payload. (MQTT 5.0 only)
   *
   * @param contentType MIME type string, or null to unset
   * @return this options instance
   */
  public MqttClientWillOptions setContentType(String contentType) {
    this.contentType = contentType;
    return this;
  }

  /**
   * @return Response Topic for request/response pattern, or null if not set (MQTT 5.0)
   */
  public String getResponseTopic() {
    return responseTopic;
  }

  /**
   * Set the Response Topic used in a request/response pattern. (MQTT 5.0 only)
   *
   * @param responseTopic topic name, or null to unset
   * @return this options instance
   */
  public MqttClientWillOptions setResponseTopic(String responseTopic) {
    this.responseTopic = responseTopic;
    return this;
  }

  /**
   * @return Correlation Data used to correlate a request, or null if not set (MQTT 5.0)
   */
  public Buffer getCorrelationData() {
    return correlationData;
  }

  /**
   * Set the Correlation Data used to correlate a request/response. (MQTT 5.0 only)
   *
   * @param correlationData binary data, or null to unset
   * @return this options instance
   */
  public MqttClientWillOptions setCorrelationData(Buffer correlationData) {
    this.correlationData = correlationData;
    return this;
  }

  /**
   * @return User Properties as key-value pairs, or null if not set (MQTT 5.0)
   */
  public Map<String, String> getUserProperties() {
    return userProperties;
  }

  /**
   * Set the User Properties. (MQTT 5.0 only)
   *
   * @param userProperties map of key-value pairs, or null to unset
   * @return this options instance
   */
  public MqttClientWillOptions setUserProperties(Map<String, String> userProperties) {
    this.userProperties = userProperties;
    return this;
  }

  /**
   * Add a single User Property. (MQTT 5.0 only)
   *
   * @param key   property key
   * @param value property value
   * @return this options instance
   */
  public MqttClientWillOptions addUserProperty(String key, String value) {
    if (this.userProperties == null) {
      this.userProperties = new LinkedHashMap<>();
    }
    this.userProperties.put(key, value);
    return this;
  }

  @Override
  public String toString() {
    return "MqttClientWillOptions{" +
        ", topic='" + topic + '\'' +
        ", messageBytes=" + messageBytes +
        ", qos=" + qos +
        ", retain=" + retain +
        ", willDelayInterval=" + willDelayInterval +
        ", payloadFormatIndicator=" + payloadFormatIndicator +
        ", contentType='" + contentType + '\'' +
        ", responseTopic='" + responseTopic + '\'' +
        ", correlationData=" + correlationData +
        ", userProperties=" + userProperties +
        '}';
  }
}
