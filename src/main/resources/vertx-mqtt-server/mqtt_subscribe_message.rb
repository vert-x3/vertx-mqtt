require 'vertx-mqtt-server/mqtt_topic_subscription'
require 'vertx-mqtt-server/mqtt_message'
require 'vertx/util/utils.rb'
# Generated from io.vertx.mqtt.messages.MqttSubscribeMessage
module VertxMqttServer
  #  Represents an MQTT SUBSCRIBE message
  class MqttSubscribeMessage
    include ::VertxMqttServer::MqttMessage
    # @private
    # @param j_del [::VertxMqttServer::MqttSubscribeMessage] the java delegate
    def initialize(j_del)
      @j_del = j_del
    end
    # @private
    # @return [::VertxMqttServer::MqttSubscribeMessage] the underlying java delegate
    def j_del
      @j_del
    end
    @@j_api_type = Object.new
    def @@j_api_type.accept?(obj)
      obj.class == MqttSubscribeMessage
    end
    def @@j_api_type.wrap(obj)
      MqttSubscribeMessage.new(obj)
    end
    def @@j_api_type.unwrap(obj)
      obj.j_del
    end
    def self.j_api_type
      @@j_api_type
    end
    def self.j_class
      Java::IoVertxMqttMessages::MqttSubscribeMessage.java_class
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
    # @return [Array<::VertxMqttServer::MqttTopicSubscription>] List with topics and related quolity of service levels
    def topic_subscriptions
      if !block_given?
        if @cached_topic_subscriptions != nil
          return @cached_topic_subscriptions
        end
        return @cached_topic_subscriptions = @j_del.java_method(:topicSubscriptions, []).call().to_a.map { |elt| ::Vertx::Util::Utils.safe_create(elt,::VertxMqttServer::MqttTopicSubscription) }
      end
      raise ArgumentError, "Invalid arguments when calling topic_subscriptions()"
    end
  end
end
