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

/** @module vertx-mqtt-server-js/mqtt_topic_subscription */
var utils = require('vertx-js/util/utils');

var io = Packages.io;
var JsonObject = io.vertx.core.json.JsonObject;
var JMqttTopicSubscription = Java.type('io.vertx.mqtt.MqttTopicSubscription');

/**
 Represents a subscription to a topic

 @class
*/
var MqttTopicSubscription = function(j_val) {

  var j_mqttTopicSubscription = j_val;
  var that = this;

  /**

   @public

   @return {string} Subscription topic name
   */
  this.topicName = function() {
    var __args = arguments;
    if (__args.length === 0) {
      if (that.cachedtopicName == null) {
        that.cachedtopicName = j_mqttTopicSubscription["topicName()"]();
      }
      return that.cachedtopicName;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  /**

   @public

   @return {Object} Quality of Service level for the subscription
   */
  this.qualityOfService = function() {
    var __args = arguments;
    if (__args.length === 0) {
      if (that.cachedqualityOfService == null) {
        that.cachedqualityOfService = utils.convReturnEnum(j_mqttTopicSubscription["qualityOfService()"]());
      }
      return that.cachedqualityOfService;
    } else throw new TypeError('function invoked with invalid arguments');
  };

  // A reference to the underlying Java delegate
  // NOTE! This is an internal API and must not be used in user code.
  // If you rely on this property your code is likely to break if we change it / remove it without warning.
  this._jdel = j_mqttTopicSubscription;
};

MqttTopicSubscription._jclass = utils.getJavaClass("io.vertx.mqtt.MqttTopicSubscription");
MqttTopicSubscription._jtype = {
  accept: function(obj) {
    return MqttTopicSubscription._jclass.isInstance(obj._jdel);
  },
  wrap: function(jdel) {
    var obj = Object.create(MqttTopicSubscription.prototype, {});
    MqttTopicSubscription.apply(obj, arguments);
    return obj;
  },
  unwrap: function(obj) {
    return obj._jdel;
  }
};
MqttTopicSubscription._create = function(jdel) {
  var obj = Object.create(MqttTopicSubscription.prototype, {});
  MqttTopicSubscription.apply(obj, arguments);
  return obj;
}
module.exports = MqttTopicSubscription;