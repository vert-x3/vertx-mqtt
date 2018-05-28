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

package io.vertx.mqtt.impl;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageIdVariableHeader;
import io.vertx.core.impl.NetSocketInternal;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.impl.VertxHandler;
import io.vertx.mqtt.MqttClientOptions;
import io.vertx.mqtt.messages.MqttConnAckMessage;
import io.vertx.mqtt.messages.MqttSubAckMessage;
import io.vertx.mqtt.messages.MqttPublishMessage;

/**
 * Represents an MQTT connection with a remote server
 */
public class MqttClientConnection {

  private static final Logger log = LoggerFactory.getLogger(MqttClientConnection.class);

  final NetSocketInternal so;
  private final ChannelHandlerContext chctx;
  private MqttClientOptions options;
  private MqttClientImpl client;

  MqttClientConnection(MqttClientImpl client, NetSocketInternal so, MqttClientOptions options) {
    this.so = so;
    this.chctx = so.channelHandlerContext();
    this.client = client;
    this.options = options;
  }

  /**
   * Handle the MQTT message received from the remote MQTT server
   *
   * @param msg Incoming Packet
   */
  void handleMessage(Object msg) {
    synchronized (so) {
      // handling directly native Netty MQTT messages, some of them are translated
      // to the related Vert.x ones for polyglotization
      if (msg instanceof MqttMessage) {

        MqttMessage mqttMessage = (MqttMessage) msg;

        DecoderResult result = mqttMessage.decoderResult();
        if (result.isFailure()) {
          chctx.pipeline().fireExceptionCaught(result.cause());
          return;
        }
        if (!result.isFinished()) {
          chctx.pipeline().fireExceptionCaught(new Exception("Unfinished message"));
          return;
        }

        log.debug(String.format("Incoming packet %s", msg));
        switch (mqttMessage.fixedHeader().messageType()) {

          case CONNACK:

            io.netty.handler.codec.mqtt.MqttConnAckMessage connack = (io.netty.handler.codec.mqtt.MqttConnAckMessage) mqttMessage;

            MqttConnAckMessage mqttConnAckMessage = MqttConnAckMessage.create(
              connack.variableHeader().connectReturnCode(),
              connack.variableHeader().isSessionPresent());
            handleConnack(mqttConnAckMessage);
            break;

          case PUBLISH:

            io.netty.handler.codec.mqtt.MqttPublishMessage publish = (io.netty.handler.codec.mqtt.MqttPublishMessage) mqttMessage;
            ByteBuf newBuf = VertxHandler.safeBuffer(publish.payload(), chctx.alloc());

            MqttPublishMessage mqttPublishMessage = MqttPublishMessage.create(
              publish.variableHeader().messageId(),
              publish.fixedHeader().qosLevel(),
              publish.fixedHeader().isDup(),
              publish.fixedHeader().isRetain(),
              publish.variableHeader().topicName(),
              newBuf);
            handlePublish(mqttPublishMessage);
            break;

          case PUBACK:
            handlePuback(((MqttMessageIdVariableHeader) mqttMessage.variableHeader()).messageId());
            break;

          case PUBREC:
            handlePubrec(((MqttMessageIdVariableHeader) mqttMessage.variableHeader()).messageId());
            break;

          case PUBREL:
            handlePubrel(((MqttMessageIdVariableHeader) mqttMessage.variableHeader()).messageId());
            break;

          case PUBCOMP:
            handlePubcomp(((MqttMessageIdVariableHeader) mqttMessage.variableHeader()).messageId());
            break;

          case SUBACK:

            io.netty.handler.codec.mqtt.MqttSubAckMessage unsuback = (io.netty.handler.codec.mqtt.MqttSubAckMessage) mqttMessage;

            MqttSubAckMessage mqttSubAckMessage = MqttSubAckMessage.create(
              unsuback.variableHeader().messageId(),
              unsuback.payload().grantedQoSLevels());
            handleSuback(mqttSubAckMessage);
            break;

          case UNSUBACK:
            handleUnsuback(((MqttMessageIdVariableHeader) mqttMessage.variableHeader()).messageId());
            break;

          case PINGRESP:
            handlePingresp();
            break;

          default:

            this.chctx.pipeline().fireExceptionCaught(new Exception("Wrong message type " + msg.getClass().getName()));
            break;
        }

      } else {

        this.chctx.pipeline().fireExceptionCaught(new Exception("Wrong message type"));
      }
    }
  }

  /**
   * Used for calling the pingresp handler when the server replies to the ping
   */
  private void handlePingresp() {
    synchronized (so) {
      this.client.handlePingresp();
    }
  }

  /**
   * Used for calling the unsuback handler when the server acks an unsubscribe
   *
   * @param unsubackMessageId identifier of the subscribe acknowledged by the server
   */
  private void handleUnsuback(int unsubackMessageId) {
    synchronized (so) {
      this.client.handleUnsuback(unsubackMessageId);
    }
  }

  /**
   * Used for calling the suback handler when the server acknoweldge subscribe to topics
   *
   * @param msg message with suback information
   */
  private void handleSuback(MqttSubAckMessage msg) {
    synchronized (so) {
      this.client.handleSuback(msg);
    }
  }

  /**
   * Used for calling the pubcomp handler when the server client acknowledge a QoS 2 message with pubcomp
   *
   * @param pubcompMessageId identifier of the message acknowledged by the server
   */
  private void handlePubcomp(int pubcompMessageId) {
    synchronized (so) {
      this.client.handlePubcomp(pubcompMessageId);
    }
  }

  /**
   * Used for calling the puback handler when the server acknowledge a QoS 1 message with puback
   *
   * @param pubackMessageId identifier of the message acknowledged by the server
   */
  private void handlePuback(int pubackMessageId) {
    synchronized (so) {
      this.client.handlePuback(pubackMessageId);
    }
  }

  /**
   * Used for calling the pubrel handler when the server acknowledge a QoS 2 message with pubrel
   *
   * @param pubrelMessageId identifier of the message acknowledged by the server
   */
  private void handlePubrel(int pubrelMessageId) {
    synchronized (so) {
      this.client.handlePubrel(pubrelMessageId);
    }
  }

  /**
   * Used for calling the publish handler when the server publishes a message
   *
   * @param msg published message
   */
  private void handlePublish(MqttPublishMessage msg) {
    synchronized (so) {
      this.client.handlePublish(msg);
    }
  }

  /**
   * Used for sending the pubrel when a pubrec is received from the server
   *
   * @param pubrecMessageId identifier of the message acknowledged by server
   */
  private void handlePubrec(int pubrecMessageId) {
    synchronized (so) {
      this.client.handlePubrec(pubrecMessageId);
    }
  }

  /**
   * Used for calling the connect handler when the server replies to the request
   *
   * @param msg  connection response message
   */
  private void handleConnack(MqttConnAckMessage msg) {
    synchronized (so) {
      this.client.handleConnack(msg);
    }
  }

  /**
   * Close the NetSocket
   */
  void close() {
    synchronized (so) {
      so.close();
    }
  }

  /**
   * Write the message to socket
   * @param mqttMessage message
   */
  void writeMessage(MqttMessage mqttMessage) {
    so.writeMessage(mqttMessage);
  }
}
