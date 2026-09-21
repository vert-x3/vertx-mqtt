package io.vertx.mqtt.messages;

import io.vertx.codegen.annotations.CacheReturn;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.mqtt.messages.impl.MqttUnsubAckMessageImpl;
import io.netty.handler.codec.mqtt.MqttProperties;

import java.util.List;

/**
 * Represents an MQTT UNSUBACK message
 */
@VertxGen
public interface MqttUnsubAckMessage extends MqttMessage {

  /**
   * Create a concrete instance of a Vert.x unsuback message
   *
   * @param messageId message identifier
   * @param reasonCodes  list of reason codes
   * @return
   */
  @GenIgnore
  static MqttUnsubAckMessage create(int messageId, List<Short> reasonCodes) {
    return new MqttUnsubAckMessageImpl(messageId, reasonCodes, MqttProperties.NO_PROPERTIES);
  }

  /**
   * Create a concrete instance of a Vert.x unsuback message
   *
   * @param messageId message identifier
   * @param reasonCodes  list of reason codes
   * @param properties MQTT properties
   * @return
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  static MqttUnsubAckMessage create(int messageId, List<Short> reasonCodes, MqttProperties properties) {
    return new MqttUnsubAckMessageImpl(messageId, reasonCodes, properties);
  }

  /**
   * @return  list of reason codes
   */
  @CacheReturn
  List<Short> reasonCodes();

  /**
   * @return MQTT properties
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  @CacheReturn
  MqttProperties properties();
}
