require 'vertx/util/utils.rb'
# Generated from io.vertx.mqtt.MqttWill
module VertxMqttServer
  #  Will information from the remote MQTT client
  class MqttWill
    # @private
    # @param j_del [::VertxMqttServer::MqttWill] the java delegate
    def initialize(j_del)
      @j_del = j_del
    end
    # @private
    # @return [::VertxMqttServer::MqttWill] the underlying java delegate
    def j_del
      @j_del
    end
    @@j_api_type = Object.new
    def @@j_api_type.accept?(obj)
      obj.class == MqttWill
    end
    def @@j_api_type.wrap(obj)
      MqttWill.new(obj)
    end
    def @@j_api_type.unwrap(obj)
      obj.j_del
    end
    def self.j_api_type
      @@j_api_type
    end
    def self.j_class
      Java::IoVertxMqtt::MqttWill.java_class
    end
    # @return [true,false] the will flag for indicating the will message presence
    def will_flag?
      if !block_given?
        if @cached_is_will_flag != nil
          return @cached_is_will_flag
        end
        return @cached_is_will_flag = @j_del.java_method(:isWillFlag, []).call()
      end
      raise ArgumentError, "Invalid arguments when calling will_flag?()"
    end
    # @return [String] the topic for the will as provided by the remote MQTT client
    def will_topic
      if !block_given?
        if @cached_will_topic != nil
          return @cached_will_topic
        end
        return @cached_will_topic = @j_del.java_method(:willTopic, []).call()
      end
      raise ArgumentError, "Invalid arguments when calling will_topic()"
    end
    # @return [String] the payload for the will as provided by the remote MQTT client
    def will_message
      if !block_given?
        if @cached_will_message != nil
          return @cached_will_message
        end
        return @cached_will_message = @j_del.java_method(:willMessage, []).call()
      end
      raise ArgumentError, "Invalid arguments when calling will_message()"
    end
    # @return [Fixnum] the QoS level for the will as provided by the remote MQTT client
    def will_qos
      if !block_given?
        if @cached_will_qos != nil
          return @cached_will_qos
        end
        return @cached_will_qos = @j_del.java_method(:willQos, []).call()
      end
      raise ArgumentError, "Invalid arguments when calling will_qos()"
    end
    # @return [true,false] true if the will must be retained as provided by the remote MQTT client
    def will_retain?
      if !block_given?
        if @cached_is_will_retain != nil
          return @cached_is_will_retain
        end
        return @cached_is_will_retain = @j_del.java_method(:isWillRetain, []).call()
      end
      raise ArgumentError, "Invalid arguments when calling will_retain?()"
    end
  end
end
