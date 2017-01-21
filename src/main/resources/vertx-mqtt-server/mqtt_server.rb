require 'vertx/vertx'
require 'vertx-mqtt-server/mqtt_endpoint'
require 'vertx/util/utils.rb'
# Generated from io.vertx.mqtt.MqttServer
module VertxMqttServer
  #  An MQTT server
  #  <p>
  #     You can accept incoming MQTT connection requests providing a {::VertxMqttServer::MqttServer#endpoint_handler}. As the
  #     requests arrive, the handler will be called with an instance of {::VertxMqttServer::MqttEndpoint} in order to manage the
  #     communication with the remote MQTT client.
  #  </p>
  class MqttServer
    # @private
    # @param j_del [::VertxMqttServer::MqttServer] the java delegate
    def initialize(j_del)
      @j_del = j_del
    end
    # @private
    # @return [::VertxMqttServer::MqttServer] the underlying java delegate
    def j_del
      @j_del
    end
    @@j_api_type = Object.new
    def @@j_api_type.accept?(obj)
      obj.class == MqttServer
    end
    def @@j_api_type.wrap(obj)
      MqttServer.new(obj)
    end
    def @@j_api_type.unwrap(obj)
      obj.j_del
    end
    def self.j_api_type
      @@j_api_type
    end
    def self.j_class
      Java::IoVertxMqtt::MqttServer.java_class
    end
    #  Return an MQTT server instance
    # @param [::Vertx::Vertx] vertx Vert.x instance
    # @param [Hash] options MQTT server options
    # @return [::VertxMqttServer::MqttServer] MQTT server instance
    def self.create(vertx=nil,options=nil)
      if vertx.class.method_defined?(:j_del) && !block_given? && options == nil
        return ::Vertx::Util::Utils.safe_create(Java::IoVertxMqtt::MqttServer.java_method(:create, [Java::IoVertxCore::Vertx.java_class]).call(vertx.j_del),::VertxMqttServer::MqttServer)
      elsif vertx.class.method_defined?(:j_del) && options.class == Hash && !block_given?
        return ::Vertx::Util::Utils.safe_create(Java::IoVertxMqtt::MqttServer.java_method(:create, [Java::IoVertxCore::Vertx.java_class,Java::IoVertxMqtt::MqttServerOptions.java_class]).call(vertx.j_del,Java::IoVertxMqtt::MqttServerOptions.new(::Vertx::Util::Utils.to_json_object(options))),::VertxMqttServer::MqttServer)
      end
      raise ArgumentError, "Invalid arguments when calling create(#{vertx},#{options})"
    end
    #  Start the server listening for incoming connections on the port and host specified
    #  It ignores any options specified through the constructor
    # @param [Fixnum] port the port to listen on
    # @param [String] host the host to listen on
    # @yield handler called when the asynchronous listen call ends
    # @return [self]
    def listen(port=nil,host=nil)
      if !block_given? && port == nil && host == nil
        @j_del.java_method(:listen, []).call()
        return self
      elsif port.class == Fixnum && !block_given? && host == nil
        @j_del.java_method(:listen, [Java::int.java_class]).call(port)
        return self
      elsif block_given? && port == nil && host == nil
        @j_del.java_method(:listen, [Java::IoVertxCore::Handler.java_class]).call((Proc.new { |ar| yield(ar.failed ? ar.cause : nil, ar.succeeded ? ::Vertx::Util::Utils.safe_create(ar.result,::VertxMqttServer::MqttServer) : nil) }))
        return self
      elsif port.class == Fixnum && host.class == String && !block_given?
        @j_del.java_method(:listen, [Java::int.java_class,Java::java.lang.String.java_class]).call(port,host)
        return self
      elsif port.class == Fixnum && block_given? && host == nil
        @j_del.java_method(:listen, [Java::int.java_class,Java::IoVertxCore::Handler.java_class]).call(port,(Proc.new { |ar| yield(ar.failed ? ar.cause : nil, ar.succeeded ? ::Vertx::Util::Utils.safe_create(ar.result,::VertxMqttServer::MqttServer) : nil) }))
        return self
      elsif port.class == Fixnum && host.class == String && block_given?
        @j_del.java_method(:listen, [Java::int.java_class,Java::java.lang.String.java_class,Java::IoVertxCore::Handler.java_class]).call(port,host,(Proc.new { |ar| yield(ar.failed ? ar.cause : nil, ar.succeeded ? ::Vertx::Util::Utils.safe_create(ar.result,::VertxMqttServer::MqttServer) : nil) }))
        return self
      end
      raise ArgumentError, "Invalid arguments when calling listen(#{port},#{host})"
    end
    #  Set the endpoint handler for the server. If an MQTT client connect to the server a
    #  new MqttEndpoint instance will be created and passed to the handler
    # @yield the endpoint handler
    # @return [self]
    def endpoint_handler
      if block_given?
        @j_del.java_method(:endpointHandler, [Java::IoVertxCore::Handler.java_class]).call((Proc.new { |event| yield(::Vertx::Util::Utils.safe_create(event,::VertxMqttServer::MqttEndpoint)) }))
        return self
      end
      raise ArgumentError, "Invalid arguments when calling endpoint_handler()"
    end
    #  The actual port the server is listening on. This is useful if you bound the server specifying 0 as port number
    #  signifying an ephemeral port
    # @return [Fixnum] the actual port the server is listening on.
    def actual_port
      if !block_given?
        return @j_del.java_method(:actualPort, []).call()
      end
      raise ArgumentError, "Invalid arguments when calling actual_port()"
    end
    #  Close the server supplying an handler that will be called when the server is actually closed (or has failed)
    # @yield the handler called on completion
    # @return [void]
    def close
      if !block_given?
        return @j_del.java_method(:close, []).call()
      elsif block_given?
        return @j_del.java_method(:close, [Java::IoVertxCore::Handler.java_class]).call((Proc.new { |ar| yield(ar.failed ? ar.cause : nil) }))
      end
      raise ArgumentError, "Invalid arguments when calling close()"
    end
  end
end
