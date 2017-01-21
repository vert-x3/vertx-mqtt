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

/** @module vertx-mqtt-server-js/mqtt_unsubscribe_message */
var utils = require('vertx-js/util/utils');
var MqttMessage = require('vertx-mqtt-server-js/mqtt_message');

var io = Packages.io;
var JsonObject = io.vertx.core.json.JsonObject;
var JMqttUnsubscribeMessage = io.vertx.mqtt.messages.MqttUnsubscribeMessage;

/**
 Represents an MQTT UNSUBSCRIBE message

 @class
*/
var MqttUnsubscribeMessage = function(j_val) {

  var j_mqttUnsubscribeMessage = j_val;
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
        that.cachedmessageId = j_mqttUnsubscribeMessage["messageId()"]();
      }
      return that.cachedmessageId;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**

   @public

   @return {Array.<string>} List of topics to unsubscribe
   */
  this.topics = function() {
    var __args = arguments;
    if (__args.length === 0) {
      if (that.cachedtopics == null) {
        that.cachedtopics = j_mqttUnsubscribeMessage["topics()"]();
      }
      return that.cachedtopics;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  // A reference to the underlying Java delegate
  // NOTE! This is an internal API and must not be used in user code.
  // If you rely on this property your code is likely to break if we change it / remove it without warning.
  this._jdel = j_mqttUnsubscribeMessage;
};

MqttUnsubscribeMessage._jclass = utils.getJavaClass("io.vertx.mqtt.messages.MqttUnsubscribeMessage");
MqttUnsubscribeMessage._jtype = {
  accept: function(obj) {
    return MqttUnsubscribeMessage._jclass.isInstance(obj._jdel);
  },
  wrap: function(jdel) {
    var obj = Object.create(MqttUnsubscribeMessage.prototype, {});
    MqttUnsubscribeMessage.apply(obj, arguments);
    return obj;
  },
  unwrap: function(obj) {
    return obj._jdel;
  }
};
MqttUnsubscribeMessage._create = function(jdel) {
  var obj = Object.create(MqttUnsubscribeMessage.prototype, {});
  MqttUnsubscribeMessage.apply(obj, arguments);
  return obj;
}
module.exports = MqttUnsubscribeMessage;