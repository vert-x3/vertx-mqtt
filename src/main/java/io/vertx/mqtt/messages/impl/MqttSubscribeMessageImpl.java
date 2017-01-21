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

import io.vertx.mqtt.MqttTopicSubscription;
import io.vertx.mqtt.impl.MqttTopicSubscriptionImpl;
import io.vertx.mqtt.messages.MqttSubscribeMessage;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Represents an MQTT SUBSCRIBE message
 */
public class MqttSubscribeMessageImpl implements MqttSubscribeMessage {

  private final int messageId;
  private final List<MqttTopicSubscription> topicSubscriptions;

  /**
   * Constructor
   *
   * @param messageId          message identifier
   * @param topicSubscriptions list with topics and related quality of service levels (from Netty)
   */
  public MqttSubscribeMessageImpl(int messageId, List<io.netty.handler.codec.mqtt.MqttTopicSubscription> topicSubscriptions) {

    this.messageId = messageId;
    this.topicSubscriptions = topicSubscriptions.stream().map(ts -> {

      return new MqttTopicSubscriptionImpl(ts.topicName(), ts.qualityOfService());

    }).collect(Collectors.toList());
  }

  public int messageId() {
    return this.messageId;
  }

  public List<MqttTopicSubscription> topicSubscriptions() {
    return this.topicSubscriptions;
  }
}
