require 'vertx/buffer'
require 'vertx-mqtt-server/mqtt_message'
require 'vertx/util/utils.rb'
# Generated from io.vertx.mqtt.messages.MqttPublishMessage
module VertxMqttServer
  #  Represents an MQTT PUBLISH message
  class MqttPublishMessage
    include ::VertxMqttServer::MqttMessage
    # @private
    # @param j_del [::VertxMqttServer::MqttPublishMessage] the java delegate
    def initialize(j_del)
      @j_del = j_del
    end
    # @private
    # @return [::VertxMqttServer::MqttPublishMessage] the underlying java delegate
    def j_del
      @j_del
    end
    @@j_api_type = Object.new
    def @@j_api_type.accept?(obj)
      obj.class == MqttPublishMessage
    end
    def @@j_api_type.wrap(obj)
      MqttPublishMessage.new(obj)
    end
    def @@j_api_type.unwrap(obj)
      obj.j_del
    end
    def self.j_api_type
      @@j_api_type
    end
    def self.j_class
      Java::IoVertxMqttMessages::MqttPublishMessage.java_class
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
    # @return [:AT_MOST_ONCE,:AT_LEAST_ONCE,:EXACTLY_ONCE,:FAILURE] Quality of service level
    def qos_level
      if !block_given?
        if @cached_qos_level != nil
          return @cached_qos_level
        end
        return @cached_qos_level = @j_del.java_method(:qosLevel, []).call().name.intern
      end
      raise ArgumentError, "Invalid arguments when calling qos_level()"
    end
    # @return [true,false] If the message is a duplicate
    def dup?
      if !block_given?
        if @cached_is_dup != nil
          return @cached_is_dup
        end
        return @cached_is_dup = @j_del.java_method(:isDup, []).call()
      end
      raise ArgumentError, "Invalid arguments when calling dup?()"
    end
    # @return [true,false] If the message needs to be retained
    def retain?
      if !block_given?
        if @cached_is_retain != nil
          return @cached_is_retain
        end
        return @cached_is_retain = @j_del.java_method(:isRetain, []).call()
      end
      raise ArgumentError, "Invalid arguments when calling retain?()"
    end
    # @return [String] Topic on which the message was published
    def topic_name
      if !block_given?
        if @cached_topic_name != nil
          return @cached_topic_name
        end
        return @cached_topic_name = @j_del.java_method(:topicName, []).call()
      end
      raise ArgumentError, "Invalid arguments when calling topic_name()"
    end
    # @return [::Vertx::Buffer] Payload message
    def payload
      if !block_given?
        if @cached_payload != nil
          return @cached_payload
        end
        return @cached_payload = ::Vertx::Util::Utils.safe_create(@j_del.java_method(:payload, []).call(),::Vertx::Buffer)
      end
      raise ArgumentError, "Invalid arguments when calling payload()"
    end
  end
end
