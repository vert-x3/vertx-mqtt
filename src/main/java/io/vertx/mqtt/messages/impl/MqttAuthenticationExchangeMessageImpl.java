package io.vertx.mqtt.messages.impl;

import io.netty.handler.codec.mqtt.MqttProperties;
import io.vertx.core.buffer.Buffer;
import io.vertx.mqtt.messages.MqttAuthenticationExchangeMessage;
import io.vertx.mqtt.messages.codes.MqttAuthenticateReasonCode;

/**
 * Represents an MQTT AUTH message
 */
public class MqttAuthenticationExchangeMessageImpl implements MqttAuthenticationExchangeMessage {

  private final MqttAuthenticateReasonCode reasonCode;

  private final MqttProperties properties;

  public MqttAuthenticationExchangeMessageImpl(MqttAuthenticateReasonCode reasonCode, MqttProperties properties) {
    this.reasonCode = reasonCode;
    this.properties = properties;
  }

  @Override
  public MqttAuthenticateReasonCode reasonCode() {
    return reasonCode;
  }

  @Override
  @SuppressWarnings("rawtypes")
  public String authenticationMethod() {
    MqttProperties.MqttProperty prop =
      properties.getProperty(MqttProperties.MqttPropertyType.AUTHENTICATION_METHOD.value());
    return prop == null ? null : ((MqttProperties.StringProperty) prop).value();
  }

  @Override
  @SuppressWarnings("rawtypes")
  public Buffer authenticationData() {
    MqttProperties.MqttProperty prop =
      properties.getProperty(MqttProperties.MqttPropertyType.AUTHENTICATION_DATA.value());
    return prop == null ? null : Buffer.buffer(((MqttProperties.BinaryProperty) prop).value());
  }

  @Override
  public MqttProperties properties() {
    return properties;
  }
}
