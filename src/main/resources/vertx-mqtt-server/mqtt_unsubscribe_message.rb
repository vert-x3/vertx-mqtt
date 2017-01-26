require 'vertx-mqtt-server/mqtt_message'
require 'vertx/util/utils.rb'
# Generated from io.vertx.mqtt.messages.MqttUnsubscribeMessage
module VertxMqttServer
  #  Represents an MQTT UNSUBSCRIBE message
  class MqttUnsubscribeMessage
    include ::VertxMqttServer::MqttMessage
    # @private
    # @param j_del [::VertxMqttServer::MqttUnsubscribeMessage] the java delegate
    def initialize(j_del)
      @j_del = j_del
    end
    # @private
    # @return [::VertxMqttServer::MqttUnsubscribeMessage] the underlying java delegate
    def j_del
      @j_del
    end
    @@j_api_type = Object.new
    def @@j_api_type.accept?(obj)
      obj.class == MqttUnsubscribeMessage
    end
    def @@j_api_type.wrap(obj)
      MqttUnsubscribeMessage.new(obj)
    end
    def @@j_api_type.unwrap(obj)
      obj.j_del
    end
    def self.j_api_type
      @@j_api_type
    end
    def self.j_class
      Java::IoVertxMqttMessages::MqttUnsubscribeMessage.java_class
    end
    # @return [Fixnum] Message identifier
    def message_id
      if !block_given?
        if @cached_message_id != nil
          return @cached_message_id
        end
        return @cached_message_id = @j_del.java_method(:messageId, []).call()
      end
      raise ArgumentError, "Invalid arguments when calling message_id()"
    end
    # @return [Array<String>] List of topics to unsubscribe
    def topics
      if !block_given?
        if @cached_topics != nil
          return @cached_topics
        end
        return @cached_topics = @j_del.java_method(:topics, []).call().to_a.map { |elt| elt }
      end
      raise ArgumentError, "Invalid arguments when calling topics()"
    end
  end
end
