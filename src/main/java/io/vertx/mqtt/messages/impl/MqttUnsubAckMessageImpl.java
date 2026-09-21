package io.vertx.mqtt.messages.impl;

import io.vertx.mqtt.messages.MqttUnsubAckMessage;
import io.netty.handler.codec.mqtt.MqttProperties;

import java.util.List;

/**
 * Represents an MQTT UNSUBACK message
 */
public class MqttUnsubAckMessageImpl implements MqttUnsubAckMessage {

  private final int messageId;
  private final List<Short> reasonCodes;
  private final MqttProperties properties;

  /**
   * Constructor for MqttUnsubAckMessageImpl
   * @param messageId the message identifier
   * @param reasonCodes the list of reason codes for the UNSUBACK message
   * @param properties the MQTT properties associated with the UNSUBACK message
   */
  public MqttUnsubAckMessageImpl(int messageId, List<Short> reasonCodes, MqttProperties properties) {
    this.messageId = messageId;
    this.reasonCodes = reasonCodes;
    this.properties = properties;
  }

  @Override
  public int messageId() {
    return this.messageId;
  }

  @Override
  public List<Short> reasonCodes() {
    return this.reasonCodes;
  }

  @Override
  public MqttProperties properties() {
    return this.properties;
  }
}
