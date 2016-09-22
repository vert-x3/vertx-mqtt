package enmasse.mqtt.messages;

import enmasse.mqtt.MqttTopicSubscription;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.VertxGen;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@VertxGen
public interface MqttSubscribeMessage extends MqttMessage {

  @GenIgnore
  static MqttSubscribeMessage create(io.netty.handler.codec.mqtt.MqttSubscribeMessage msg) {
    return new MqttSubscribeMessage() {
      @Override
      public int messageId() {
        return msg.variableHeader().messageId();
      }
      @Override
      public List<MqttTopicSubscription> topicSubscriptions() {
        return msg.payload().topicSubscriptions().stream().map(ts -> {
          return new MqttTopicSubscription() {
            @Override
            public String topicName() {
              return ts.topicName();
            }
            @Override
            public MqttQoS qualityOfService() {
              return ts.qualityOfService();
            }
          };
        }).collect(Collectors.toList());
      }
    };
  }

  int messageId();

  List<MqttTopicSubscription> topicSubscriptions();

}
