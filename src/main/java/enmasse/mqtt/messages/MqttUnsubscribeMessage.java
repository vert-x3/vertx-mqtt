package enmasse.mqtt.messages;

import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.VertxGen;

import java.util.List;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@VertxGen
public interface MqttUnsubscribeMessage extends MqttMessage {

  @GenIgnore
  static MqttUnsubscribeMessage create(io.netty.handler.codec.mqtt.MqttUnsubscribeMessage msg) {
    return new MqttUnsubscribeMessage() {
      @Override
      public int messageId() {
        return msg.variableHeader().messageId();
      }
      @Override
      public List<String> topics() {
        return msg.payload().topics();
      }
    };
  }

  int messageId();

  List<String> topics();

}
