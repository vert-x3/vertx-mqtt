require 'vertx-mqtt-server/mqtt_unsubscribe_message'
require 'vertx/buffer'
require 'vertx-mqtt-server/mqtt_subscribe_message'
require 'vertx-mqtt-server/mqtt_auth'
require 'vertx-mqtt-server/mqtt_will'
require 'vertx-mqtt-server/mqtt_publish_message'
require 'vertx/util/utils.rb'
# Generated from io.vertx.mqtt.MqttEndpoint
module VertxMqttServer
  #  Represents an MQTT endpoint for point-to-point communication with the remote MQTT client
  class MqttEndpoint
    # @private
    # @param j_del [::VertxMqttServer::MqttEndpoint] the java delegate
    def initialize(j_del)
      @j_del = j_del
    end
    # @private
    # @return [::VertxMqttServer::MqttEndpoint] the underlying java delegate
    def j_del
      @j_del
    end
    @@j_api_type = Object.new
    def @@j_api_type.accept?(obj)
      obj.class == MqttEndpoint
    end
    def @@j_api_type.wrap(obj)
      MqttEndpoint.new(obj)
    end
    def @@j_api_type.unwrap(obj)
      obj.j_del
    end
    def self.j_api_type
      @@j_api_type
    end
    def self.j_class
      Java::IoVertxMqtt::MqttEndpoint.java_class
    end
    #  Close the endpoint, so the connection with remote MQTT client
    # @return [void]
    def close
      if !block_given?
        return @j_del.java_method(:close, []).call()
      end
      raise ArgumentError, "Invalid arguments when calling close()"
    end
    # @return [String] the client identifier as provided by the remote MQTT client
    def client_identifier
      if !block_given?
        if @cached_client_identifier != nil
          return @cached_client_identifier
        end
        return @cached_client_identifier = @j_del.java_method(:clientIdentifier, []).call()
      end
      raise ArgumentError, "Invalid arguments when calling client_identifier()"
    end
    # @return [::VertxMqttServer::MqttAuth] the Authentication information as provided by the remote MQTT client
    def auth
      if !block_given?
        if @cached_auth != nil
          return @cached_auth
        end
        return @cached_auth = ::Vertx::Util::Utils.safe_create(@j_del.java_method(:auth, []).call(),::VertxMqttServer::MqttAuth)
      end
      raise ArgumentError, "Invalid arguments when calling auth()"
    end
    # @return [::VertxMqttServer::MqttWill] the Will information as provided by the remote MQTT client
    def will
      if !block_given?
        if @cached_will != nil
          return @cached_will
        end
        return @cached_will = ::Vertx::Util::Utils.safe_create(@j_del.java_method(:will, []).call(),::VertxMqttServer::MqttWill)
      end
      raise ArgumentError, "Invalid arguments when calling will()"
    end
    # @return [Fixnum] the protocol version required by the remote MQTT client
    def protocol_version
      if !block_given?
        if @cached_protocol_version != nil
          return @cached_protocol_version
        end
        return @cached_protocol_version = @j_del.java_method(:protocolVersion, []).call()
      end
      raise ArgumentError, "Invalid arguments when calling protocol_version()"
    end
    # @return [String] the protocol name provided by the remote MQTT client
    def protocol_name
      if !block_given?
        if @cached_protocol_name != nil
          return @cached_protocol_name
        end
        return @cached_protocol_name = @j_del.java_method(:protocolName, []).call()
      end
      raise ArgumentError, "Invalid arguments when calling protocol_name()"
    end
    # @return [true,false] true when clean session is requested by the remote MQTT client
    def clean_session?
      if !block_given?
        if @cached_is_clean_session != nil
          return @cached_is_clean_session
        end
        return @cached_is_clean_session = @j_del.java_method(:isCleanSession, []).call()
      end
      raise ArgumentError, "Invalid arguments when calling clean_session?()"
    end
    # @return [Fixnum] the keep alive timeout (in seconds) specified by the remote MQTT client
    def keep_alive_time_seconds
      if !block_given?
        if @cached_keep_alive_time_seconds != nil
          return @cached_keep_alive_time_seconds
        end
        return @cached_keep_alive_time_seconds = @j_del.java_method(:keepAliveTimeSeconds, []).call()
      end
      raise ArgumentError, "Invalid arguments when calling keep_alive_time_seconds()"
    end
    # @return [Fixnum] the message identifier used for last published message
    def last_message_id
      if !block_given?
        if @cached_last_message_id != nil
          return @cached_last_message_id
        end
        return @cached_last_message_id = @j_del.java_method(:lastMessageId, []).call()
      end
      raise ArgumentError, "Invalid arguments when calling last_message_id()"
    end
    #  Enable/disable subscription/unsubscription requests auto acknowledge
    # @param [true,false] isSubscriptionAutoAck auto acknowledge status
    # @return [void]
    def subscription_auto_ack(isSubscriptionAutoAck=nil)
      if (isSubscriptionAutoAck.class == TrueClass || isSubscriptionAutoAck.class == FalseClass) && !block_given?
        return @j_del.java_method(:subscriptionAutoAck, [Java::boolean.java_class]).call(isSubscriptionAutoAck)
      end
      raise ArgumentError, "Invalid arguments when calling subscription_auto_ack(#{isSubscriptionAutoAck})"
    end
    # @return [true,false] true when auto acknowledge status for subscription/unsubscription requests
    def subscription_auto_ack?
      if !block_given?
        return @j_del.java_method(:isSubscriptionAutoAck, []).call()
      end
      raise ArgumentError, "Invalid arguments when calling subscription_auto_ack?()"
    end
    #  Enable/disable publishing (in/out) auto acknowledge
    # @param [true,false] isPublishAutoAck auto acknowledge status
    # @return [self]
    def publish_auto_ack(isPublishAutoAck=nil)
      if (isPublishAutoAck.class == TrueClass || isPublishAutoAck.class == FalseClass) && !block_given?
        @j_del.java_method(:publishAutoAck, [Java::boolean.java_class]).call(isPublishAutoAck)
        return self
      end
      raise ArgumentError, "Invalid arguments when calling publish_auto_ack(#{isPublishAutoAck})"
    end
    # @return [true,false] auto acknowledge status for publishing (in/out)
    def publish_auto_ack?
      if !block_given?
        return @j_del.java_method(:isPublishAutoAck, []).call()
      end
      raise ArgumentError, "Invalid arguments when calling publish_auto_ack?()"
    end
    #  Enable/disable auto keep alive (sending ping response)
    # @param [true,false] isAutoKeepAlive auto keep alive
    # @return [self]
    def auto_keep_alive(isAutoKeepAlive=nil)
      if (isAutoKeepAlive.class == TrueClass || isAutoKeepAlive.class == FalseClass) && !block_given?
        @j_del.java_method(:autoKeepAlive, [Java::boolean.java_class]).call(isAutoKeepAlive)
        return self
      end
      raise ArgumentError, "Invalid arguments when calling auto_keep_alive(#{isAutoKeepAlive})"
    end
    # @return [true,false] the auto keep alive status (sending ping response)
    def auto_keep_alive?
      if !block_given?
        return @j_del.java_method(:isAutoKeepAlive, []).call()
      end
      raise ArgumentError, "Invalid arguments when calling auto_keep_alive?()"
    end
    #  Set a disconnect handler on the MQTT endpoint. This handler is called when a DISCONNECT
    #  message is received by the remote MQTT client
    # @yield the handler
    # @return [self]
    def disconnect_handler
      if block_given?
        @j_del.java_method(:disconnectHandler, [Java::IoVertxCore::Handler.java_class]).call(Proc.new { yield })
        return self
      end
      raise ArgumentError, "Invalid arguments when calling disconnect_handler()"
    end
    #  Set a subscribe handler on the MQTT endpoint. This handler is called when a SUBSCRIBE
    #  message is received by the remote MQTT client
    # @yield the handler
    # @return [self]
    def subscribe_handler
      if block_given?
        @j_del.java_method(:subscribeHandler, [Java::IoVertxCore::Handler.java_class]).call((Proc.new { |event| yield(::Vertx::Util::Utils.safe_create(event,::VertxMqttServer::MqttSubscribeMessage)) }))
        return self
      end
      raise ArgumentError, "Invalid arguments when calling subscribe_handler()"
    end
    #  Set a unsubscribe handler on the MQTT endpoint. This handler is called when a UNSUBSCRIBE
    #  message is received by the remote MQTT client
    # @yield the handler
    # @return [self]
    def unsubscribe_handler
      if block_given?
        @j_del.java_method(:unsubscribeHandler, [Java::IoVertxCore::Handler.java_class]).call((Proc.new { |event| yield(::Vertx::Util::Utils.safe_create(event,::VertxMqttServer::MqttUnsubscribeMessage)) }))
        return self
      end
      raise ArgumentError, "Invalid arguments when calling unsubscribe_handler()"
    end
    #  Set the publish handler on the MQTT endpoint. This handler is called when a PUBLISH
    #  message is received by the remote MQTT client
    # @yield the handler
    # @return [self]
    def publish_handler
      if block_given?
        @j_del.java_method(:publishHandler, [Java::IoVertxCore::Handler.java_class]).call((Proc.new { |event| yield(::Vertx::Util::Utils.safe_create(event,::VertxMqttServer::MqttPublishMessage)) }))
        return self
      end
      raise ArgumentError, "Invalid arguments when calling publish_handler()"
    end
    #  Set the puback handler on the MQTT endpoint. This handler is called when a PUBACK
    #  message is received by the remote MQTT client
    # @yield the handler
    # @return [self]
    def publish_acknowledge_handler
      if block_given?
        @j_del.java_method(:publishAcknowledgeHandler, [Java::IoVertxCore::Handler.java_class]).call((Proc.new { |event| yield(event) }))
        return self
      end
      raise ArgumentError, "Invalid arguments when calling publish_acknowledge_handler()"
    end
    #  Set the pubrec handler on the MQTT endpoint. This handler is called when a PUBREC
    #  message is received by the remote MQTT client
    # @yield the handler
    # @return [self]
    def publish_received_handler
      if block_given?
        @j_del.java_method(:publishReceivedHandler, [Java::IoVertxCore::Handler.java_class]).call((Proc.new { |event| yield(event) }))
        return self
      end
      raise ArgumentError, "Invalid arguments when calling publish_received_handler()"
    end
    #  Set the pubrel handler on the MQTT endpoint. This handler is called when a PUBREL
    #  message is received by the remote MQTT client
    # @yield the handler
    # @return [self]
    def publish_release_handler
      if block_given?
        @j_del.java_method(:publishReleaseHandler, [Java::IoVertxCore::Handler.java_class]).call((Proc.new { |event| yield(event) }))
        return self
      end
      raise ArgumentError, "Invalid arguments when calling publish_release_handler()"
    end
    #  Set the pubcomp handler on the MQTT endpoint. This handler is called when a PUBCOMP
    #  message is received by the remote MQTT client
    # @yield the handler
    # @return [self]
    def publish_complete_handler
      if block_given?
        @j_del.java_method(:publishCompleteHandler, [Java::IoVertxCore::Handler.java_class]).call((Proc.new { |event| yield(event) }))
        return self
      end
      raise ArgumentError, "Invalid arguments when calling publish_complete_handler()"
    end
    #  Set the pingreq handler on the MQTT endpoint. This handler is called when a PINGREQ
    #  message is received by the remote MQTT client. In any case the endpoint sends the
    #  PINGRESP internally after executing this handler.
    # @yield the handler
    # @return [self]
    def ping_handler
      if block_given?
        @j_del.java_method(:pingHandler, [Java::IoVertxCore::Handler.java_class]).call(Proc.new { yield })
        return self
      end
      raise ArgumentError, "Invalid arguments when calling ping_handler()"
    end
    #  Set a close handler. This will be called when the MQTT endpoint is closed
    # @yield the handler
    # @return [self]
    def close_handler
      if block_given?
        @j_del.java_method(:closeHandler, [Java::IoVertxCore::Handler.java_class]).call(Proc.new { yield })
        return self
      end
      raise ArgumentError, "Invalid arguments when calling close_handler()"
    end
    #  Sends the CONNACK message to the remote MQTT client with "connection accepted"
    #  return code. See {::VertxMqttServer::MqttEndpoint#reject} for refusing connection
    # @param [true,false] sessionPresent if a previous session is present
    # @return [self]
    def accept(sessionPresent=nil)
      if (sessionPresent.class == TrueClass || sessionPresent.class == FalseClass) && !block_given?
        @j_del.java_method(:accept, [Java::boolean.java_class]).call(sessionPresent)
        return self
      end
      raise ArgumentError, "Invalid arguments when calling accept(#{sessionPresent})"
    end
    #  Sends the CONNACK message to the remote MQTT client rejecting the connection
    #  request with specified return code. See {::VertxMqttServer::MqttEndpoint#accept} for accepting connection
    # @param [:CONNECTION_ACCEPTED,:CONNECTION_REFUSED_UNACCEPTABLE_PROTOCOL_VERSION,:CONNECTION_REFUSED_IDENTIFIER_REJECTED,:CONNECTION_REFUSED_SERVER_UNAVAILABLE,:CONNECTION_REFUSED_BAD_USER_NAME_OR_PASSWORD,:CONNECTION_REFUSED_NOT_AUTHORIZED] returnCode the connect return code
    # @return [self]
    def reject(returnCode=nil)
      if returnCode.class == Symbol && !block_given?
        @j_del.java_method(:reject, [Java::IoNettyHandlerCodecMqtt::MqttConnectReturnCode.java_class]).call(Java::IoNettyHandlerCodecMqtt::MqttConnectReturnCode.valueOf(returnCode.to_s))
        return self
      end
      raise ArgumentError, "Invalid arguments when calling reject(#{returnCode})"
    end
    #  Sends the SUBACK message to the remote MQTT client
    # @param [Fixnum] subscribeMessageId identifier of the SUBSCRIBE message to acknowledge
    # @param [Array<:AT_MOST_ONCE,:AT_LEAST_ONCE,:EXACTLY_ONCE,:FAILURE>] grantedQoSLevels granted QoS levels for the requested topics
    # @return [self]
    def subscribe_acknowledge(subscribeMessageId=nil,grantedQoSLevels=nil)
      if subscribeMessageId.class == Fixnum && grantedQoSLevels.class == Array && !block_given?
        @j_del.java_method(:subscribeAcknowledge, [Java::int.java_class,Java::JavaUtil::List.java_class]).call(subscribeMessageId,grantedQoSLevels.map { |element| Java::IoNettyHandlerCodecMqtt::MqttQoS.valueOf(element.to_s) })
        return self
      end
      raise ArgumentError, "Invalid arguments when calling subscribe_acknowledge(#{subscribeMessageId},#{grantedQoSLevels})"
    end
    #  Sends the UNSUBACK message to the remote MQTT client
    # @param [Fixnum] unsubscribeMessageId identifier of the UNSUBSCRIBE message to acknowledge
    # @return [self]
    def unsubscribe_acknowledge(unsubscribeMessageId=nil)
      if unsubscribeMessageId.class == Fixnum && !block_given?
        @j_del.java_method(:unsubscribeAcknowledge, [Java::int.java_class]).call(unsubscribeMessageId)
        return self
      end
      raise ArgumentError, "Invalid arguments when calling unsubscribe_acknowledge(#{unsubscribeMessageId})"
    end
    #  Sends the PUBACK message to the remote MQTT client
    # @param [Fixnum] publishMessageId identifier of the PUBLISH message to acknowledge
    # @return [self]
    def publish_acknowledge(publishMessageId=nil)
      if publishMessageId.class == Fixnum && !block_given?
        @j_del.java_method(:publishAcknowledge, [Java::int.java_class]).call(publishMessageId)
        return self
      end
      raise ArgumentError, "Invalid arguments when calling publish_acknowledge(#{publishMessageId})"
    end
    #  Sends the PUBREC message to the remote MQTT client
    # @param [Fixnum] publishMessageId identifier of the PUBLISH message to acknowledge
    # @return [self]
    def publish_received(publishMessageId=nil)
      if publishMessageId.class == Fixnum && !block_given?
        @j_del.java_method(:publishReceived, [Java::int.java_class]).call(publishMessageId)
        return self
      end
      raise ArgumentError, "Invalid arguments when calling publish_received(#{publishMessageId})"
    end
    #  Sends the PUBREL message to the remote MQTT client
    # @param [Fixnum] publishMessageId identifier of the PUBLISH message to acknowledge
    # @return [self]
    def publish_release(publishMessageId=nil)
      if publishMessageId.class == Fixnum && !block_given?
        @j_del.java_method(:publishRelease, [Java::int.java_class]).call(publishMessageId)
        return self
      end
      raise ArgumentError, "Invalid arguments when calling publish_release(#{publishMessageId})"
    end
    #  Sends the PUBCOMP message to the remote MQTT client
    # @param [Fixnum] publishMessageId identifier of the PUBLISH message to acknowledge
    # @return [self]
    def publish_complete(publishMessageId=nil)
      if publishMessageId.class == Fixnum && !block_given?
        @j_del.java_method(:publishComplete, [Java::int.java_class]).call(publishMessageId)
        return self
      end
      raise ArgumentError, "Invalid arguments when calling publish_complete(#{publishMessageId})"
    end
    #  Sends the PUBLISH message to the remote MQTT client
    # @param [String] topic topic on which the message is published
    # @param [::Vertx::Buffer] payload message payload
    # @param [:AT_MOST_ONCE,:AT_LEAST_ONCE,:EXACTLY_ONCE,:FAILURE] qosLevel quality of service level
    # @param [true,false] isDup if the message is a duplicate
    # @param [true,false] isRetain if the message needs to be retained
    # @return [self]
    def publish(topic=nil,payload=nil,qosLevel=nil,isDup=nil,isRetain=nil)
      if topic.class == String && payload.class.method_defined?(:j_del) && qosLevel.class == Symbol && (isDup.class == TrueClass || isDup.class == FalseClass) && (isRetain.class == TrueClass || isRetain.class == FalseClass) && !block_given?
        @j_del.java_method(:publish, [Java::java.lang.String.java_class,Java::IoVertxCoreBuffer::Buffer.java_class,Java::IoNettyHandlerCodecMqtt::MqttQoS.java_class,Java::boolean.java_class,Java::boolean.java_class]).call(topic,payload.j_del,Java::IoNettyHandlerCodecMqtt::MqttQoS.valueOf(qosLevel.to_s),isDup,isRetain)
        return self
      end
      raise ArgumentError, "Invalid arguments when calling publish(#{topic},#{payload},#{qosLevel},#{isDup},#{isRetain})"
    end
    #  Sends the PINGRESP message to the remote MQTT client
    # @return [self]
    def pong
      if !block_given?
        @j_del.java_method(:pong, []).call()
        return self
      end
      raise ArgumentError, "Invalid arguments when calling pong()"
    end
  end
end
