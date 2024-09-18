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

package io.vertx.mqtt.messages.impl;

import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.buffer.Buffer;
import io.vertx.mqtt.messages.MqttPublishMessage;

/**
 * Represents an MQTT PUBLISH message
 */
public class MqttPublishMessageImpl implements MqttPublishMessage {

  private final int messageId;
  private final MqttQoS qosLevel;
  private final boolean isDup;
  private final boolean isRetain;
  private final String topicName;
  private final Buffer payload;
  private final MqttProperties properties;

  private boolean isAcked;
  private MqttPubAckCallback ackCallback;

  /**
   * Constructor
   *
   * @param messageId  message identifier
   * @param qosLevel   quality of service level
   * @param isDup      if the message is a duplicate
   * @param isRetain   if the message needs to be retained
   * @param topicName  topic on which the message was published
   * @param payload    payload message
   * @param properties MQTT properties
   */
  public MqttPublishMessageImpl(int messageId, MqttQoS qosLevel, boolean isDup, boolean isRetain, String topicName, Buffer payload, MqttProperties properties) {
    this.messageId = messageId;
    this.qosLevel = qosLevel;
    this.isDup = isDup;
    this.isRetain = isRetain;
    this.topicName = topicName;
    this.payload = payload;
    this.properties = properties;
  }

  public int messageId() {
    return this.messageId;
  }

  public MqttQoS qosLevel() {
    return this.qosLevel;
  }

  public boolean isDup() {
    return this.isDup;
  }

  public boolean isRetain() {
    return this.isRetain;
  }

  public String topicName() {
    return this.topicName;
  }

  public Buffer payload() {
    return this.payload;
  }

  /**
   * Set the ack operation that will be execute on {@link MqttPublishMessage#ack()} invocation.
   * @param ackCallback
   */
  public void setAckCallback(MqttPubAckCallback ackCallback) {
    this.ackCallback = ackCallback;
  }

  @Override
  public void ack() {
    if (this.qosLevel == MqttQoS.AT_LEAST_ONCE || this.qosLevel == MqttQoS.EXACTLY_ONCE) {
      if (ackCallback == null) {
        throw new IllegalArgumentException("Callback not present. Check that Auto Ack is disabled.");
      } else if (isAcked) {
        throw new IllegalArgumentException("Ack of message " + messageId + " altready sent.");
      } else {
        isAcked = true;
        ackCallback.ack();
      }
    }
  }

  public MqttProperties properties() {
    return this.properties;
  }

  /**
   * Define the callback action to do when the manual ack in involved.
   */
  public interface MqttPubAckCallback {

    /**
     *  Send message's ack (PUBACK/PUBCOMP) to the broker.
     *  To use only when the AutoAck is false.
     */
    void ack();

  }

}
