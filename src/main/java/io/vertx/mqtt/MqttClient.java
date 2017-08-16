/*
 * Copyright 2016 Red Hat Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.vertx.mqtt;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.mqtt.impl.MqttClientImpl;
import io.vertx.mqtt.messages.MqttConnAckMessage;
import io.vertx.mqtt.messages.MqttPublishMessage;
import io.vertx.mqtt.messages.MqttSubAckMessage;

import java.util.Map;

/**
 * An MQTT client
 */
@VertxGen
public interface MqttClient {

  /**
   * Return an MQTT client instance
   *
   * @param vertx Vert.x instance
   * @param options MQTT client options
   * @return  MQTT client instance
   */
  static MqttClient create(Vertx vertx, MqttClientOptions options) {
    return new MqttClientImpl(vertx, options);
  }

  /**
   * Return an MQTT client instance using the default options
   *
   * @param vertx Vert.x instance
   * @return  MQTT client instance
   */
  static MqttClient create(Vertx vertx) {
    return new MqttClientImpl(vertx, new MqttClientOptions());
  }

  /**
   * Connects to an MQTT server calling connectHandler after connection
   *
   * @param port  port of the MQTT server
   * @param host  hostname/ip address of the MQTT server
   * @param connectHandler  handler called when the asynchronous connect call ends
   * @return  current MQTT client instance
   */
  @Fluent
  MqttClient connect(int port, String host, Handler<AsyncResult<MqttConnAckMessage>> connectHandler);

  /**
   * Connects to an MQTT server calling connectHandler after connection
   *
   * @param port  port of the MQTT server
   * @param host  hostname/ip address of the MQTT server
   * @param serverName  the SNI server name
   * @param connectHandler  handler called when the asynchronous connect call ends
   * @return  current MQTT client instance
   */
  @Fluent
  MqttClient connect(int port, String host, String serverName, Handler<AsyncResult<MqttConnAckMessage>> connectHandler);

  /**
   * Disconnects from the MQTT server
   *
   * @return current MQTT client instance
   */
  @Fluent
  MqttClient disconnect();

  /**
   * Disconnects from the MQTT server calling disconnectHandler after disconnection
   *
   * @param disconnectHandler handler called when asynchronous disconnect call ends
   * @return current MQTT client instance
   */
  @Fluent
  MqttClient disconnect(Handler<AsyncResult<Void>> disconnectHandler);

  /**
   * Sends the PUBLISH message to the remote MQTT server
   *
   * @param topic    topic on which the message is published
   * @param payload  message payload
   * @param qosLevel QoS level
   * @param isDup    if the message is a duplicate
   * @param isRetain if the message needs to be retained
   * @return current MQTT client instance
   */
  @Fluent
  MqttClient publish(String topic, Buffer payload, MqttQoS qosLevel, boolean isDup, boolean isRetain);

  /**
   * Sends the PUBLISH message to the remote MQTT server
   *
   * @param topic    topic on which the message is published
   * @param payload  message payload
   * @param qosLevel QoS level
   * @param isDup    if the message is a duplicate
   * @param isRetain if the message needs to be retained
   * @param publishSentHandler handler called after PUBLISH packet sent with packetid (not when QoS 0)
   * @return current MQTT client instance
   */
  @Fluent
  MqttClient publish(String topic, Buffer payload, MqttQoS qosLevel, boolean isDup, boolean isRetain, Handler<AsyncResult<Integer>> publishSentHandler);

  /**
   * Sets handler which will be called each time publish is completed
   *
   * @param publishCompletionHandler handler called with the packetId
   * @return current MQTT client instance
   */
  @Fluent
  MqttClient publishCompletionHandler(Handler<Integer> publishCompletionHandler);

  /**
   * Sets handler which will be called each time server publish something to client
   *
   * @param publishHandler handler to call
   * @return current MQTT client instance
   */
  @Fluent
  MqttClient publishHandler(Handler<MqttPublishMessage> publishHandler);

  /**
   * Sets handler which will be called after SUBACK packet receiving
   *
   * @param subscribeCompletionHandler handler to call. List inside is a granted QoS array
   * @return current MQTT client instance
   */
  @Fluent
  MqttClient subscribeCompletionHandler(Handler<MqttSubAckMessage> subscribeCompletionHandler);

  /**
   * Subscribes to the topic with a specified QoS level
   *
   * @param topic topic you subscribe on
   * @param qos   QoS level
   * @return current MQTT client instance
   */
  @Fluent
  MqttClient subscribe(String topic, int qos);

  /**
   * Subscribes to the topic with a specified QoS level
   *
   * @param topic                 topic you subscribe on
   * @param qos                   QoS level
   * @param subscribeSentHandler handler called after SUBSCRIBE packet sent with packetid
   * @return current MQTT client instance
   */
  @Fluent
  MqttClient subscribe(String topic, int qos, Handler<AsyncResult<Integer>> subscribeSentHandler);

  /**
   * Subscribes to the topics with related QoS levels
   *
   * @param topics topics and related QoS levels to subscribe to
   * @return current MQTT client instance
   */
  @Fluent
  MqttClient subscribe(Map<String, Integer> topics);


  /**
   * Subscribes to the topic and adds a handler which will be called after the request is sent
   *
   * @param topics                topics you subscribe on
   * @param subscribeSentHandler  handler called after SUBSCRIBE packet sent with packetid
   * @return current MQTT client instance
   */
  @Fluent
  MqttClient subscribe(Map<String, Integer> topics, Handler<AsyncResult<Integer>> subscribeSentHandler);


  /**
   * Sets handler which will be called after UNSUBACK packet receiving
   *
   * @param unsubscribeCompletionHandler handler to call with the packetid
   * @return current MQTT client instance
   */
  @Fluent
  MqttClient unsubscribeCompletionHandler(Handler<Integer> unsubscribeCompletionHandler);

  /**
   * Unsubscribe from receiving messages on given topic
   *
   * @param topic Topic you want to unsubscribe from
   * @return current MQTT client instance
   */
  @Fluent
  MqttClient unsubscribe(String topic);

  /**
   * Unsubscribe from receiving messages on given topic
   *
   * @param topic Topic you want to unsubscribe from
   * @param unsubscribeSentHandler  handler called after UNSUBSCRIBE packet sent
   * @return current MQTT client instance
   */
  @Fluent
  MqttClient unsubscribe(String topic, Handler<AsyncResult<Integer>> unsubscribeSentHandler);

  /**
   * Sets handler which will be called after PINGRESP packet receiving
   *
   * @param pingResponseHandler handler to call
   * @return current MQTT client instance
   */
  @Fluent
  MqttClient pingResponseHandler(Handler<Void> pingResponseHandler);

  /**
   * Set an exception handler for the client, that will be called when an error happens
   * in internal netty structures.
   *
   * {@code io.netty.handler.codec.DecoderException} can be one of the cause
   *
   * @param handler the exception handler
   * @return current MQTT client instance
   */
  @Fluent
  MqttClient exceptionHandler(Handler<Throwable> handler);

  /**
   * Set a handler that will be called when the connection with server is closed
   *
   * @param closeHandler handler to call
   * @return current MQTT client instance
   */
  @Fluent
  MqttClient closeHandler(Handler<Void> closeHandler);

  /**
   * This method is needed by the client in order to avoid server closes the
   * connection due to the keep alive timeout if client has no messages to send
   *
   * @return current MQTT client instance
   */
  @Fluent
  MqttClient ping();

  /**
   * @return the client identifier
   */
  String clientId();
}
