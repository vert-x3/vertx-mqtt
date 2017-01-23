require 'vertx/util/utils.rb'
# Generated from io.vertx.mqtt.MqttTopicSubscription
module VertxMqttServer
  #  Represents a subscription to a topic
  class MqttTopicSubscription
    # @private
    # @param j_del [::VertxMqttServer::MqttTopicSubscription] the java delegate
    def initialize(j_del)
      @j_del = j_del
    end
    # @private
    # @return [::VertxMqttServer::MqttTopicSubscription] the underlying java delegate
    def j_del
      @j_del
    end
    @@j_api_type = Object.new
    def @@j_api_type.accept?(obj)
      obj.class == MqttTopicSubscription
    end
    def @@j_api_type.wrap(obj)
      MqttTopicSubscription.new(obj)
    end
    def @@j_api_type.unwrap(obj)
      obj.j_del
    end
    def self.j_api_type
      @@j_api_type
    end
    def self.j_class
      Java::IoVertxMqtt::MqttTopicSubscription.java_class
    end
    # @return [String] Subscription topic name
    def topic_name
      if !block_given?
        if @cached_topic_name != nil
          return @cached_topic_name
        end
        return @cached_topic_name = @j_del.java_method(:topicName, []).call()
      end
      raise ArgumentError, "Invalid arguments when calling topic_name()"
    end
    # @return [:AT_MOST_ONCE,:AT_LEAST_ONCE,:EXACTLY_ONCE,:FAILURE] Quality of Service level for the subscription
    def quality_of_service
      if !block_given?
        if @cached_quality_of_service != nil
          return @cached_quality_of_service
        end
        return @cached_quality_of_service = @j_del.java_method(:qualityOfService, []).call().name.intern
      end
      raise ArgumentError, "Invalid arguments when calling quality_of_service()"
    end
  end
end
