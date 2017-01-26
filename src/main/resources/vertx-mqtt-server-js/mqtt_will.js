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

/** @module vertx-mqtt-server-js/mqtt_will */
var utils = require('vertx-js/util/utils');

var io = Packages.io;
var JsonObject = io.vertx.core.json.JsonObject;
var JMqttWill = Java.type('io.vertx.mqtt.MqttWill');

/**
 Will information from the remote MQTT client

 @class
*/
var MqttWill = function(j_val) {

  var j_mqttWill = j_val;
  var that = this;

  /**

   @public

   @return {boolean} Will flag for indicating the will message presence
   */
  this.isWillFlag = function() {
    var __args = arguments;
    if (__args.length === 0) {
      if (that.cachedisWillFlag == null) {
        that.cachedisWillFlag = j_mqttWill["isWillFlag()"]();
      }
      return that.cachedisWillFlag;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**

   @public

   @return {string} Topic for the will as provided by the remote MQTT client
   */
  this.willTopic = function() {
    var __args = arguments;
    if (__args.length === 0) {
      if (that.cachedwillTopic == null) {
        that.cachedwillTopic = j_mqttWill["willTopic()"]();
      }
      return that.cachedwillTopic;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**

   @public

   @return {string} Payload for the will as provided by the remote MQTT client
   */
  this.willMessage = function() {
    var __args = arguments;
    if (__args.length === 0) {
      if (that.cachedwillMessage == null) {
        that.cachedwillMessage = j_mqttWill["willMessage()"]();
      }
      return that.cachedwillMessage;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**

   @public

   @return {number} QoS level for the will as provided by the remote MQTT client
   */
  this.willQos = function() {
    var __args = arguments;
    if (__args.length === 0) {
      if (that.cachedwillQos == null) {
        that.cachedwillQos = j_mqttWill["willQos()"]();
      }
      return that.cachedwillQos;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**

   @public

   @return {boolean} If the will must be retained as provided by the remote MQTT client
   */
  this.isWillRetain = function() {
    var __args = arguments;
    if (__args.length === 0) {
      if (that.cachedisWillRetain == null) {
        that.cachedisWillRetain = j_mqttWill["isWillRetain()"]();
      }
      return that.cachedisWillRetain;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  // A reference to the underlying Java delegate
  // NOTE! This is an internal API and must not be used in user code.
  // If you rely on this property your code is likely to break if we change it / remove it without warning.
  this._jdel = j_mqttWill;
};

MqttWill._jclass = utils.getJavaClass("io.vertx.mqtt.MqttWill");
MqttWill._jtype = {
  accept: function(obj) {
    return MqttWill._jclass.isInstance(obj._jdel);
  },
  wrap: function(jdel) {
    var obj = Object.create(MqttWill.prototype, {});
    MqttWill.apply(obj, arguments);
    return obj;
  },
  unwrap: function(obj) {
    return obj._jdel;
  }
};
MqttWill._create = function(jdel) {
  var obj = Object.create(MqttWill.prototype, {});
  MqttWill.apply(obj, arguments);
  return obj;
}
module.exports = MqttWill;