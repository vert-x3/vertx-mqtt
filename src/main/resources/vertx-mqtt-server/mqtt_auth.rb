require 'vertx/util/utils.rb'
# Generated from io.vertx.mqtt.MqttAuth
module VertxMqttServer
  #  MQTT authentication information
  class MqttAuth
    # @private
    # @param j_del [::VertxMqttServer::MqttAuth] the java delegate
    def initialize(j_del)
      @j_del = j_del
    end
    # @private
    # @return [::VertxMqttServer::MqttAuth] the underlying java delegate
    def j_del
      @j_del
    end
    @@j_api_type = Object.new
    def @@j_api_type.accept?(obj)
      obj.class == MqttAuth
    end
    def @@j_api_type.wrap(obj)
      MqttAuth.new(obj)
    end
    def @@j_api_type.unwrap(obj)
      obj.j_del
    end
    def self.j_api_type
      @@j_api_type
    end
    def self.j_class
      Java::IoVertxMqtt::MqttAuth.java_class
    end
    # @return [String] Username provided by the remote MQTT client
    def user_name
      if !block_given?
        return @j_del.java_method(:userName, []).call()
      end
      raise ArgumentError, "Invalid arguments when calling user_name()"
    end
    # @return [String] Password provided by the remote MQTT client
    def password
      if !block_given?
        return @j_del.java_method(:password, []).call()
      end
      raise ArgumentError, "Invalid arguments when calling password()"
    end
  end
end
