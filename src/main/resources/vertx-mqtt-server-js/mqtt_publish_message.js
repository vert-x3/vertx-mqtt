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

/** @module vertx-mqtt-server-js/mqtt_publish_message */
var utils = require('vertx-js/util/utils');
var Buffer = require('vertx-js/buffer');
var MqttMessage = require('vertx-mqtt-server-js/mqtt_message');

var io = Packages.io;
var JsonObject = io.vertx.core.json.JsonObject;
var JMqttPublishMessage = Java.type('io.vertx.mqtt.messages.MqttPublishMessage');

/**
 Represents an MQTT PUBLISH message

 @class
*/
var MqttPublishMessage = function(j_val) {

  var j_mqttPublishMessage = j_val;
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
        that.cachedmessageId = j_mqttPublishMessage["messageId()"]();
      }
      return that.cachedmessageId;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**

   @public

   @return {Object} Quality of service level
   */
  this.qosLevel = function() {
    var __args = arguments;
    if (__args.length === 0) {
      if (that.cachedqosLevel == null) {
        that.cachedqosLevel = utils.convReturnEnum(j_mqttPublishMessage["qosLevel()"]());
      }
      return that.cachedqosLevel;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**

   @public

   @return {boolean} If the message is a duplicate
   */
  this.isDup = function() {
    var __args = arguments;
    if (__args.length === 0) {
      if (that.cachedisDup == null) {
        that.cachedisDup = j_mqttPublishMessage["isDup()"]();
      }
      return that.cachedisDup;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**

   @public

   @return {boolean} If the message needs to be retained
   */
  this.isRetain = function() {
    var __args = arguments;
    if (__args.length === 0) {
      if (that.cachedisRetain == null) {
        that.cachedisRetain = j_mqttPublishMessage["isRetain()"]();
      }
      return that.cachedisRetain;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**

   @public

   @return {string} Topic on which the message was published
   */
  this.topicName = function() {
    var __args = arguments;
    if (__args.length === 0) {
      if (that.cachedtopicName == null) {
        that.cachedtopicName = j_mqttPublishMessage["topicName()"]();
      }
      return that.cachedtopicName;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**

   @public

   @return {Buffer} Payload message
   */
  this.payload = function() {
    var __args = arguments;
    if (__args.length === 0) {
      if (that.cachedpayload == null) {
        that.cachedpayload = utils.convReturnVertxGen(Buffer, j_mqttPublishMessage["payload()"]());
      }
      return that.cachedpayload;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  // A reference to the underlying Java delegate
  // NOTE! This is an internal API and must not be used in user code.
  // If you rely on this property your code is likely to break if we change it / remove it without warning.
  this._jdel = j_mqttPublishMessage;
};

MqttPublishMessage._jclass = utils.getJavaClass("io.vertx.mqtt.messages.MqttPublishMessage");
MqttPublishMessage._jtype = {
  accept: function(obj) {
    return MqttPublishMessage._jclass.isInstance(obj._jdel);
  },
  wrap: function(jdel) {
    var obj = Object.create(MqttPublishMessage.prototype, {});
    MqttPublishMessage.apply(obj, arguments);
    return obj;
  },
  unwrap: function(obj) {
    return obj._jdel;
  }
};
MqttPublishMessage._create = function(jdel) {
  var obj = Object.create(MqttPublishMessage.prototype, {});
  MqttPublishMessage.apply(obj, arguments);
  return obj;
}
module.exports = MqttPublishMessage;