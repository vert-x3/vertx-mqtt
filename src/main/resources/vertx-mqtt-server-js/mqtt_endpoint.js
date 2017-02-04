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

/** @module vertx-mqtt-server-js/mqtt_endpoint */
var utils = require('vertx-js/util/utils');
var MqttUnsubscribeMessage = require('vertx-mqtt-server-js/mqtt_unsubscribe_message');
var Buffer = require('vertx-js/buffer');
var MqttSubscribeMessage = require('vertx-mqtt-server-js/mqtt_subscribe_message');
var MqttAuth = require('vertx-mqtt-server-js/mqtt_auth');
var MqttWill = require('vertx-mqtt-server-js/mqtt_will');
var MqttPublishMessage = require('vertx-mqtt-server-js/mqtt_publish_message');

var io = Packages.io;
var JsonObject = io.vertx.core.json.JsonObject;
var JMqttEndpoint = Java.type('io.vertx.mqtt.MqttEndpoint');

/**
 Represents an MQTT endpoint for point-to-point communication with the remote MQTT client

 @class
*/
var MqttEndpoint = function(j_val) {

  var j_mqttEndpoint = j_val;
  var that = this;

  /**
   Close the endpoint, so the connection with remote MQTT client

   @public

   */
  this.close = function() {
    var __args = arguments;
    if (__args.length === 0) {
      j_mqttEndpoint["close()"]();
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**
   Client identifier as provided by the remote MQTT client

   @public

   @return {string} 
   */
  this.clientIdentifier = function() {
    var __args = arguments;
    if (__args.length === 0) {
      if (that.cachedclientIdentifier == null) {
        that.cachedclientIdentifier = j_mqttEndpoint["clientIdentifier()"]();
      }
      return that.cachedclientIdentifier;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**
   Authentication information as provided by the remote MQTT client

   @public

   @return {MqttAuth} 
   */
  this.auth = function() {
    var __args = arguments;
    if (__args.length === 0) {
      if (that.cachedauth == null) {
        that.cachedauth = utils.convReturnVertxGen(MqttAuth, j_mqttEndpoint["auth()"]());
      }
      return that.cachedauth;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**
   Will information as provided by the remote MQTT client

   @public

   @return {MqttWill} 
   */
  this.will = function() {
    var __args = arguments;
    if (__args.length === 0) {
      if (that.cachedwill == null) {
        that.cachedwill = utils.convReturnVertxGen(MqttWill, j_mqttEndpoint["will()"]());
      }
      return that.cachedwill;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**
   Protocol version required by the remote MQTT client

   @public

   @return {number} 
   */
  this.protocolVersion = function() {
    var __args = arguments;
    if (__args.length === 0) {
      if (that.cachedprotocolVersion == null) {
        that.cachedprotocolVersion = j_mqttEndpoint["protocolVersion()"]();
      }
      return that.cachedprotocolVersion;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**
   Protocol name provided by the remote MQTT client

   @public

   @return {string} 
   */
  this.protocolName = function() {
    var __args = arguments;
    if (__args.length === 0) {
      if (that.cachedprotocolName == null) {
        that.cachedprotocolName = j_mqttEndpoint["protocolName()"]();
      }
      return that.cachedprotocolName;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**
   If clean session is requested by the remote MQTT client

   @public

   @return {boolean} 
   */
  this.isCleanSession = function() {
    var __args = arguments;
    if (__args.length === 0) {
      if (that.cachedisCleanSession == null) {
        that.cachedisCleanSession = j_mqttEndpoint["isCleanSession()"]();
      }
      return that.cachedisCleanSession;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**
   Keep alive timeout (in seconds) specified by the remote MQTT client

   @public

   @return {number} 
   */
  this.keepAliveTimeSeconds = function() {
    var __args = arguments;
    if (__args.length === 0) {
      if (that.cachedkeepAliveTimeSeconds == null) {
        that.cachedkeepAliveTimeSeconds = j_mqttEndpoint["keepAliveTimeSeconds()"]();
      }
      return that.cachedkeepAliveTimeSeconds;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**
   Message identifier used for last published message

   @public

   @return {number} 
   */
  this.lastMessageId = function() {
    var __args = arguments;
    if (__args.length === 0) {
      if (that.cachedlastMessageId == null) {
        that.cachedlastMessageId = j_mqttEndpoint["lastMessageId()"]();
      }
      return that.cachedlastMessageId;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**
   Enable/disable subscription/unsubscription requests auto acknowledge

   @public
   @param isSubscriptionAutoAck {boolean} auto acknowledge status 
   */
  this.subscriptionAutoAck = function(isSubscriptionAutoAck) {
    var __args = arguments;
    if (__args.length === 1 && typeof __args[0] ==='boolean') {
      j_mqttEndpoint["subscriptionAutoAck(boolean)"](isSubscriptionAutoAck);
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**
   Return auto acknowledge status for subscription/unsubscription requests

   @public

   @return {boolean} 
   */
  this.isSubscriptionAutoAck = function() {
    var __args = arguments;
    if (__args.length === 0) {
      return j_mqttEndpoint["isSubscriptionAutoAck()"]();
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**
   Enable/disable publishing (in/out) auto acknowledge

   @public
   @param isPublishAutoAck {boolean} auto acknowledge status 
   @return {MqttEndpoint} a reference to this, so the API can be used fluently
   */
  this.publishAutoAck = function(isPublishAutoAck) {
    var __args = arguments;
    if (__args.length === 1 && typeof __args[0] ==='boolean') {
      j_mqttEndpoint["publishAutoAck(boolean)"](isPublishAutoAck);
      return that;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**

   @public

   @return {boolean} auto acknowledge status for publishing (in/out)
   */
  this.isPublishAutoAck = function() {
    var __args = arguments;
    if (__args.length === 0) {
      return j_mqttEndpoint["isPublishAutoAck()"]();
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**
   Enable/disable auto keep alive (sending ping response)

   @public
   @param isAutoKeepAlive {boolean} auto keep alive 
   @return {MqttEndpoint} a reference to this, so the API can be used fluently
   */
  this.autoKeepAlive = function(isAutoKeepAlive) {
    var __args = arguments;
    if (__args.length === 1 && typeof __args[0] ==='boolean') {
      j_mqttEndpoint["autoKeepAlive(boolean)"](isAutoKeepAlive);
      return that;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**
   Return auto keep alive status (sending ping response)

   @public

   @return {boolean} 
   */
  this.isAutoKeepAlive = function() {
    var __args = arguments;
    if (__args.length === 0) {
      return j_mqttEndpoint["isAutoKeepAlive()"]();
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**
   Set a disconnect handler on the MQTT endpoint. This handler is called when a DISCONNECT
   message is received by the remote MQTT client

   @public
   @param handler {function} the handler 
   @return {MqttEndpoint} a reference to this, so the API can be used fluently
   */
  this.disconnectHandler = function(handler) {
    var __args = arguments;
    if (__args.length === 1 && typeof __args[0] === 'function') {
      j_mqttEndpoint["disconnectHandler(io.vertx.core.Handler)"](handler);
      return that;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**
   Set a subscribe handler on the MQTT endpoint. This handler is called when a SUBSCRIBE
   message is received by the remote MQTT client

   @public
   @param handler {function} the handler 
   @return {MqttEndpoint} a reference to this, so the API can be used fluently
   */
  this.subscribeHandler = function(handler) {
    var __args = arguments;
    if (__args.length === 1 && typeof __args[0] === 'function') {
      j_mqttEndpoint["subscribeHandler(io.vertx.core.Handler)"](function(jVal) {
      handler(utils.convReturnVertxGen(MqttSubscribeMessage, jVal));
    });
      return that;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**
   Set a unsubscribe handler on the MQTT endpoint. This handler is called when a UNSUBSCRIBE
   message is received by the remote MQTT client

   @public
   @param handler {function} the handler 
   @return {MqttEndpoint} a reference to this, so the API can be used fluently
   */
  this.unsubscribeHandler = function(handler) {
    var __args = arguments;
    if (__args.length === 1 && typeof __args[0] === 'function') {
      j_mqttEndpoint["unsubscribeHandler(io.vertx.core.Handler)"](function(jVal) {
      handler(utils.convReturnVertxGen(MqttUnsubscribeMessage, jVal));
    });
      return that;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**
   Set the publish handler on the MQTT endpoint. This handler is called when a PUBLISH
   message is received by the remote MQTT client

   @public
   @param handler {function} the handler 
   @return {MqttEndpoint} a reference to this, so the API can be used fluently
   */
  this.publishHandler = function(handler) {
    var __args = arguments;
    if (__args.length === 1 && typeof __args[0] === 'function') {
      j_mqttEndpoint["publishHandler(io.vertx.core.Handler)"](function(jVal) {
      handler(utils.convReturnVertxGen(MqttPublishMessage, jVal));
    });
      return that;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**
   Set the puback handler on the MQTT endpoint. This handler is called when a PUBACK
   message is received by the remote MQTT client

   @public
   @param handler {function} the handler 
   @return {MqttEndpoint} a reference to this, so the API can be used fluently
   */
  this.publishAcknowledgeHandler = function(handler) {
    var __args = arguments;
    if (__args.length === 1 && typeof __args[0] === 'function') {
      j_mqttEndpoint["publishAcknowledgeHandler(io.vertx.core.Handler)"](function(jVal) {
      handler(jVal);
    });
      return that;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**
   Set the pubrec handler on the MQTT endpoint. This handler is called when a PUBREC
   message is received by the remote MQTT client

   @public
   @param handler {function} the handler 
   @return {MqttEndpoint} a reference to this, so the API can be used fluently
   */
  this.publishReceivedHandler = function(handler) {
    var __args = arguments;
    if (__args.length === 1 && typeof __args[0] === 'function') {
      j_mqttEndpoint["publishReceivedHandler(io.vertx.core.Handler)"](function(jVal) {
      handler(jVal);
    });
      return that;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**
   Set the pubrel handler on the MQTT endpoint. This handler is called when a PUBREL
   message is received by the remote MQTT client

   @public
   @param handler {function} the handler 
   @return {MqttEndpoint} a reference to this, so the API can be used fluently
   */
  this.publishReleaseHandler = function(handler) {
    var __args = arguments;
    if (__args.length === 1 && typeof __args[0] === 'function') {
      j_mqttEndpoint["publishReleaseHandler(io.vertx.core.Handler)"](function(jVal) {
      handler(jVal);
    });
      return that;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**
   Set the pubcomp handler on the MQTT endpoint. This handler is called when a PUBCOMP
   message is received by the remote MQTT client

   @public
   @param handler {function} the handler 
   @return {MqttEndpoint} a reference to this, so the API can be used fluently
   */
  this.publishCompleteHandler = function(handler) {
    var __args = arguments;
    if (__args.length === 1 && typeof __args[0] === 'function') {
      j_mqttEndpoint["publishCompleteHandler(io.vertx.core.Handler)"](function(jVal) {
      handler(jVal);
    });
      return that;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**
   Set the pingreq handler on the MQTT endpoint. This handler is called when a PINGREQ
   message is received by the remote MQTT client. In any case the endpoint sends the
   PINGRESP internally after executing this handler.

   @public
   @param handler {function} the handler 
   @return {MqttEndpoint} a reference to this, so the API can be used fluently
   */
  this.pingHandler = function(handler) {
    var __args = arguments;
    if (__args.length === 1 && typeof __args[0] === 'function') {
      j_mqttEndpoint["pingHandler(io.vertx.core.Handler)"](handler);
      return that;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**
   Set a close handler. This will be called when the MQTT endpoint is closed

   @public
   @param handler {function} the handler 
   @return {MqttEndpoint} a reference to this, so the API can be used fluently
   */
  this.closeHandler = function(handler) {
    var __args = arguments;
    if (__args.length === 1 && typeof __args[0] === 'function') {
      j_mqttEndpoint["closeHandler(io.vertx.core.Handler)"](handler);
      return that;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**
   Sends the CONNACK message to the remote MQTT client with "connection accepted"
   return code. See {@link MqttEndpoint#reject} for refusing connection

   @public
   @param sessionPresent {boolean} if a previous session is present 
   @return {MqttEndpoint} a reference to this, so the API can be used fluently
   */
  this.accept = function(sessionPresent) {
    var __args = arguments;
    if (__args.length === 1 && typeof __args[0] ==='boolean') {
      j_mqttEndpoint["accept(boolean)"](sessionPresent);
      return that;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**
   Sends the CONNACK message to the remote MQTT client rejecting the connection
   request with specified return code. See {@link MqttEndpoint#accept} for accepting connection

   @public
   @param returnCode {Object} the connect return code 
   @return {MqttEndpoint} a reference to this, so the API can be used fluently
   */
  this.reject = function(returnCode) {
    var __args = arguments;
    if (__args.length === 1 && typeof __args[0] === 'string') {
      j_mqttEndpoint["reject(io.netty.handler.codec.mqtt.MqttConnectReturnCode)"](io.netty.handler.codec.mqtt.MqttConnectReturnCode.valueOf(returnCode));
      return that;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**
   Sends the SUBACK message to the remote MQTT client

   @public
   @param subscribeMessageId {number} identifier of the SUBSCRIBE message to acknowledge 
   @param grantedQoSLevels {Array.<Object>} granted QoS levels for the requested topics 
   @return {MqttEndpoint} a reference to this, so the API can be used fluently
   */
  this.subscribeAcknowledge = function(subscribeMessageId, grantedQoSLevels) {
    var __args = arguments;
    if (__args.length === 2 && typeof __args[0] ==='number' && typeof __args[1] === 'object' && __args[1] instanceof Array) {
      j_mqttEndpoint["subscribeAcknowledge(int,java.util.List)"](subscribeMessageId, utils.convParamListEnum(grantedQoSLevels, function(val) { return Packages.io.netty.handler.codec.mqtt.MqttQoS.valueOf(val); }));
      return that;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**
   Sends the UNSUBACK message to the remote MQTT client

   @public
   @param unsubscribeMessageId {number} identifier of the UNSUBSCRIBE message to acknowledge 
   @return {MqttEndpoint} a reference to this, so the API can be used fluently
   */
  this.unsubscribeAcknowledge = function(unsubscribeMessageId) {
    var __args = arguments;
    if (__args.length === 1 && typeof __args[0] ==='number') {
      j_mqttEndpoint["unsubscribeAcknowledge(int)"](unsubscribeMessageId);
      return that;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**
   Sends the PUBACK message to the remote MQTT client

   @public
   @param publishMessageId {number} identifier of the PUBLISH message to acknowledge 
   @return {MqttEndpoint} a reference to this, so the API can be used fluently
   */
  this.publishAcknowledge = function(publishMessageId) {
    var __args = arguments;
    if (__args.length === 1 && typeof __args[0] ==='number') {
      j_mqttEndpoint["publishAcknowledge(int)"](publishMessageId);
      return that;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**
   Sends the PUBREC message to the remote MQTT client

   @public
   @param publishMessageId {number} identifier of the PUBLISH message to acknowledge 
   @return {MqttEndpoint} a reference to this, so the API can be used fluently
   */
  this.publishReceived = function(publishMessageId) {
    var __args = arguments;
    if (__args.length === 1 && typeof __args[0] ==='number') {
      j_mqttEndpoint["publishReceived(int)"](publishMessageId);
      return that;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**
   Sends the PUBREL message to the remote MQTT client

   @public
   @param publishMessageId {number} identifier of the PUBLISH message to acknowledge 
   @return {MqttEndpoint} a reference to this, so the API can be used fluently
   */
  this.publishRelease = function(publishMessageId) {
    var __args = arguments;
    if (__args.length === 1 && typeof __args[0] ==='number') {
      j_mqttEndpoint["publishRelease(int)"](publishMessageId);
      return that;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**
   Sends the PUBCOMP message to the remote MQTT client

   @public
   @param publishMessageId {number} identifier of the PUBLISH message to acknowledge 
   @return {MqttEndpoint} a reference to this, so the API can be used fluently
   */
  this.publishComplete = function(publishMessageId) {
    var __args = arguments;
    if (__args.length === 1 && typeof __args[0] ==='number') {
      j_mqttEndpoint["publishComplete(int)"](publishMessageId);
      return that;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**
   Sends the PUBLISH message to the remote MQTT client

   @public
   @param topic {string} topic on which the message is published 
   @param payload {Buffer} message payload 
   @param qosLevel {Object} quality of service level 
   @param isDup {boolean} if the message is a duplicate 
   @param isRetain {boolean} if the message needs to be retained 
   @return {MqttEndpoint} a reference to this, so the API can be used fluently
   */
  this.publish = function(topic, payload, qosLevel, isDup, isRetain) {
    var __args = arguments;
    if (__args.length === 5 && typeof __args[0] === 'string' && typeof __args[1] === 'object' && __args[1]._jdel && typeof __args[2] === 'string' && typeof __args[3] ==='boolean' && typeof __args[4] ==='boolean') {
      j_mqttEndpoint["publish(java.lang.String,io.vertx.core.buffer.Buffer,io.netty.handler.codec.mqtt.MqttQoS,boolean,boolean)"](topic, payload._jdel, io.netty.handler.codec.mqtt.MqttQoS.valueOf(qosLevel), isDup, isRetain);
      return that;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**
   Sends the PINGRESP message to the remote MQTT client

   @public

   @return {MqttEndpoint} a reference to this, so the API can be used fluently
   */
  this.pong = function() {
    var __args = arguments;
    if (__args.length === 0) {
      j_mqttEndpoint["pong()"]();
      return that;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  // A reference to the underlying Java delegate
  // NOTE! This is an internal API and must not be used in user code.
  // If you rely on this property your code is likely to break if we change it / remove it without warning.
  this._jdel = j_mqttEndpoint;
};

MqttEndpoint._jclass = utils.getJavaClass("io.vertx.mqtt.MqttEndpoint");
MqttEndpoint._jtype = {
  accept: function(obj) {
    return MqttEndpoint._jclass.isInstance(obj._jdel);
  },
  wrap: function(jdel) {
    var obj = Object.create(MqttEndpoint.prototype, {});
    MqttEndpoint.apply(obj, arguments);
    return obj;
  },
  unwrap: function(obj) {
    return obj._jdel;
  }
};
MqttEndpoint._create = function(jdel) {
  var obj = Object.create(MqttEndpoint.prototype, {});
  MqttEndpoint.apply(obj, arguments);
  return obj;
}
module.exports = MqttEndpoint;