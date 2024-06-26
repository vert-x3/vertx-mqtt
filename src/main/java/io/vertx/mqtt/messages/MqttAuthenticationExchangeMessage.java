package io.vertx.mqtt.messages;

import io.netty.handler.codec.mqtt.MqttProperties;
import io.vertx.codegen.annotations.CacheReturn;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.buffer.Buffer;
import io.vertx.mqtt.messages.codes.MqttAuthenticateReasonCode;
import io.vertx.mqtt.messages.impl.MqttAuthenticationExchangeMessageImpl;

/**
 * Represents an MQTT AUTH message
 */
@VertxGen
public interface MqttAuthenticationExchangeMessage {

  /**
   * Create a concrete instance of a Vert.x auth message
   *
   * @param reasonCode authenticate reason code
   * @param properties mqtt properties.
   * @return Vert.x auth message
   */
  @GenIgnore
  static MqttAuthenticationExchangeMessage create(MqttAuthenticateReasonCode reasonCode, MqttProperties properties) {
    return new MqttAuthenticationExchangeMessageImpl(reasonCode, properties);
  }

  /**
   * @return authenticate reason code
   */
  MqttAuthenticateReasonCode reasonCode();

  /**
   * @return authenticate method
   */
  @CacheReturn
  String authenticationMethod();

  /**
   * @return authentication data
   */
  @CacheReturn
  Buffer authenticationData();

  /**
   * @return MQTT properties
   */
  @CacheReturn
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  MqttProperties properties();
}
