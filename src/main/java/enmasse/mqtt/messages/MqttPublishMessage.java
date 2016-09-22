package enmasse.mqtt.messages;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.buffer.Buffer;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@VertxGen
public interface MqttPublishMessage extends MqttMessage {

  @GenIgnore
  static MqttPublishMessage create(io.netty.handler.codec.mqtt.MqttPublishMessage msg) {
    return new MqttPublishMessage() {
      @Override
      public int messageId() {
        return msg.variableHeader().messageId();
      }
      @Override
      public MqttQoS qosLevel() {
        return msg.fixedHeader().qosLevel();
      }
      @Override
      public Buffer payload() {
        return Buffer.buffer(msg.payload());
      }
    };
  }

  int messageId();

  MqttQoS qosLevel();

  Buffer payload();

}
