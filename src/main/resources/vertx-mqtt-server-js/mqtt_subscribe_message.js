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

/** @module vertx-mqtt-server-js/mqtt_subscribe_message */
var utils = require('vertx-js/util/utils');
var MqttTopicSubscription = require('vertx-mqtt-server-js/mqtt_topic_subscription');
var MqttMessage = require('vertx-mqtt-server-js/mqtt_message');

var io = Packages.io;
var JsonObject = io.vertx.core.json.JsonObject;
var JMqttSubscribeMessage = Java.type('io.vertx.mqtt.messages.MqttSubscribeMessage');

/**
 Represents an MQTT SUBSCRIBE message

 @class
*/
var MqttSubscribeMessage = function(j_val) {

  var j_mqttSubscribeMessage = j_val;
  var that = this;
  MqttMessage.call(this, j_val);

  /**

   @public

   @return {number} Message identifier
   */
  this.messageId = function() {
    var __args = arguments;
    if (__args.length === 0) {
      if (that.cachedmessageId == null) {
        that.cachedmessageId = j_mqttSubscribeMessage["messageId()"]();
      }
      return that.cachedmessageId;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**

   @public

   @return {Array.<MqttTopicSubscription>} List with topics and related quolity of service levels
   */
  this.topicSubscriptions = function() {
    var __args = arguments;
    if (__args.length === 0) {
      if (that.cachedtopicSubscriptions == null) {
        that.cachedtopicSubscriptions = utils.convReturnListSetVertxGen(j_mqttSubscribeMessage["topicSubscriptions()"](), MqttTopicSubscription);
      }
      return that.cachedtopicSubscriptions;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  // A reference to the underlying Java delegate
  // NOTE! This is an internal API and must not be used in user code.
  // If you rely on this property your code is likely to break if we change it / remove it without warning.
  this._jdel = j_mqttSubscribeMessage;
};

MqttSubscribeMessage._jclass = utils.getJavaClass("io.vertx.mqtt.messages.MqttSubscribeMessage");
MqttSubscribeMessage._jtype = {
  accept: function(obj) {
    return MqttSubscribeMessage._jclass.isInstance(obj._jdel);
  },
  wrap: function(jdel) {
    var obj = Object.create(MqttSubscribeMessage.prototype, {});
    MqttSubscribeMessage.apply(obj, arguments);
    return obj;
  },
  unwrap: function(obj) {
    return obj._jdel;
  }
};
MqttSubscribeMessage._create = function(jdel) {
  var obj = Object.create(MqttSubscribeMessage.prototype, {});
  MqttSubscribeMessage.apply(obj, arguments);
  return obj;
}
module.exports = MqttSubscribeMessage;