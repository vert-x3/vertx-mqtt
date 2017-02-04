/*
 * Copyright 2014 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

/** @module vertx-mqtt-server-js/mqtt_server */
var utils = require('vertx-js/util/utils');
var Vertx = require('vertx-js/vertx');
var MqttEndpoint = require('vertx-mqtt-server-js/mqtt_endpoint');

var io = Packages.io;
var JsonObject = io.vertx.core.json.JsonObject;
var JMqttServer = Java.type('io.vertx.mqtt.MqttServer');
var MqttServerOptions = Java.type('io.vertx.mqtt.MqttServerOptions');

/**
 An MQTT server
 <p>
 @class
*/
var MqttServer = function(j_val) {

  var j_mqttServer = j_val;
  var that = this;

  /**
   Start the server listening for incoming connections on the port and host specified
   It ignores any options specified through the constructor

   @public
   @param port {number} the port to listen on 
   @param host {string} the host to listen on 
   @param listenHandler {function} handler called when the asynchronous listen call ends 
   @return {MqttServer} a reference to this, so the API can be used fluently
   */
  this.listen = function() {
    var __args = arguments;
    if (__args.length === 0) {
      j_mqttServer["listen()"]();
      return that;
    }  else if (__args.length === 1 && typeof __args[0] ==='number') {
      j_mqttServer["listen(int)"](__args[0]);
      return that;
    }  else if (__args.length === 1 && typeof __args[0] === 'function') {
      j_mqttServer["listen(io.vertx.core.Handler)"](function(ar) {
      if (ar.succeeded()) {
        __args[0](utils.convReturnVertxGen(MqttServer, ar.result()), null);
      } else {
        __args[0](null, ar.cause());
      }
    });
      return that;
    }  else if (__args.length === 2 && typeof __args[0] ==='number' && typeof __args[1] === 'string') {
      j_mqttServer["listen(int,java.lang.String)"](__args[0], __args[1]);
      return that;
    }  else if (__args.length === 2 && typeof __args[0] ==='number' && typeof __args[1] === 'function') {
      j_mqttServer["listen(int,io.vertx.core.Handler)"](__args[0], function(ar) {
      if (ar.succeeded()) {
        __args[1](utils.convReturnVertxGen(MqttServer, ar.result()), null);
      } else {
        __args[1](null, ar.cause());
      }
    });
      return that;
    }  else if (__args.length === 3 && typeof __args[0] ==='number' && typeof __args[1] === 'string' && typeof __args[2] === 'function') {
      j_mqttServer["listen(int,java.lang.String,io.vertx.core.Handler)"](__args[0], __args[1], function(ar) {
      if (ar.succeeded()) {
        __args[2](utils.convReturnVertxGen(MqttServer, ar.result()), null);
      } else {
        __args[2](null, ar.cause());
      }
    });
      return that;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**
   Set the endpoint handler for the server. If an MQTT client connect to the server a
   new MqttEndpoint instance will be created and passed to the handler

   @public
   @param handler {function} the endpoint handler 
   @return {MqttServer} a reference to this, so the API can be used fluently
   */
  this.endpointHandler = function(handler) {
    var __args = arguments;
    if (__args.length === 1 && typeof __args[0] === 'function') {
      j_mqttServer["endpointHandler(io.vertx.core.Handler)"](function(jVal) {
      handler(utils.convReturnVertxGen(MqttEndpoint, jVal));
    });
      return that;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**
   The actual port the server is listening on. This is useful if you bound the server specifying 0 as port number
   signifying an ephemeral port

   @public

   @return {number} the actual port the server is listening on.
   */
  this.actualPort = function() {
    var __args = arguments;
    if (__args.length === 0) {
      return j_mqttServer["actualPort()"]();
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**
   Close the server supplying an handler that will be called when the server is actually closed (or has failed).

   @public
   @param completionHandler {function} the handler called on completion 
   */
  this.close = function() {
    var __args = arguments;
    if (__args.length === 0) {
      j_mqttServer["close()"]();
    }  else if (__args.length === 1 && typeof __args[0] === 'function') {
      j_mqttServer["close(io.vertx.core.Handler)"](function(ar) {
      if (ar.succeeded()) {
        __args[0](null, null);
      } else {
        __args[0](null, ar.cause());
      }
    });
    } else throw new TypeError('function invoked with invalid arguments');
  };

  // A reference to the underlying Java delegate
  // NOTE! This is an internal API and must not be used in user code.
  // If you rely on this property your code is likely to break if we change it / remove it without warning.
  this._jdel = j_mqttServer;
};

MqttServer._jclass = utils.getJavaClass("io.vertx.mqtt.MqttServer");
MqttServer._jtype = {
  accept: function(obj) {
    return MqttServer._jclass.isInstance(obj._jdel);
  },
  wrap: function(jdel) {
    var obj = Object.create(MqttServer.prototype, {});
    MqttServer.apply(obj, arguments);
    return obj;
  },
  unwrap: function(obj) {
    return obj._jdel;
  }
};
MqttServer._create = function(jdel) {
  var obj = Object.create(MqttServer.prototype, {});
  MqttServer.apply(obj, arguments);
  return obj;
}
/**
 Return an MQTT server instance

 @memberof module:vertx-mqtt-server-js/mqtt_server
 @param vertx {Vertx} Vert.x instance 
 @param options {Object} MQTT server options 
 @return {MqttServer} MQTT server instance
 */
MqttServer.create = function() {
  var __args = arguments;
  if (__args.length === 1 && typeof __args[0] === 'object' && __args[0]._jdel) {
    return utils.convReturnVertxGen(MqttServer, JMqttServer["create(io.vertx.core.Vertx)"](__args[0]._jdel));
  }else if (__args.length === 2 && typeof __args[0] === 'object' && __args[0]._jdel && (typeof __args[1] === 'object' && __args[1] != null)) {
    return utils.convReturnVertxGen(MqttServer, JMqttServer["create(io.vertx.core.Vertx,io.vertx.mqtt.MqttServerOptions)"](__args[0]._jdel, __args[1] != null ? new MqttServerOptions(new JsonObject(Java.asJSONCompatible(__args[1]))) : null));
  } else throw new TypeError('function invoked with invalid arguments');
};

module.exports = MqttServer;