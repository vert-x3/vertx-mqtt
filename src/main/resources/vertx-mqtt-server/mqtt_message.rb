require 'vertx/util/utils.rb'
# Generated from io.vertx.mqtt.messages.MqttMessage
module VertxMqttServer
  module MqttMessage
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
  end
  class MqttMessageImpl
    include MqttMessage
    # @private
    # @param j_del [::VertxMqttServer::MqttMessage] the java delegate
    def initialize(j_del)
      @j_del = j_del
    end
    # @private
    # @return [::VertxMqttServer::MqttMessage] the underlying java delegate
    def j_del
      @j_del
    end
    @@j_api_type = Object.new
    def @@j_api_type.accept?(obj)
      obj.class == MqttMessage
    end
    def @@j_api_type.wrap(obj)
      MqttMessage.new(obj)
    end
    def @@j_api_type.unwrap(obj)
      obj.j_del
    end
    def self.j_api_type
      @@j_api_type
    end
    def self.j_class
      Java::IoVertxMqttMessages::MqttMessage.java_class
    end
  end
end
