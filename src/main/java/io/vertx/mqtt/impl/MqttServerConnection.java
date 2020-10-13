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
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.mqtt.MqttConnectMessage;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttUnacceptableProtocolVersionException;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.vertx.core.Handler;
import io.vertx.core.VertxException;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.impl.NetSocketInternal;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.net.impl.VertxHandler;
import io.vertx.mqtt.MqttAuth;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.MqttServerOptions;
import io.vertx.mqtt.MqttWill;
import io.vertx.mqtt.messages.MqttPublishMessage;
import io.vertx.mqtt.messages.MqttSubscribeMessage;
import io.vertx.mqtt.messages.MqttUnsubscribeMessage;

import java.util.UUID;

/**
 * Represents an MQTT connection with a remote client
 */
public class MqttServerConnection {

  private static final Logger log = LoggerFactory.getLogger(MqttServerConnection.class);

  // handler to call when a remote MQTT client connects and establishes a connection
  private Handler<MqttEndpoint> endpointHandler;

  // handler to call when an connection is rejected
  private Handler<Throwable> exceptionHandler;

  private final NetSocketInternal so;

  // endpoint for handling point-to-point communication with the remote MQTT client
  private MqttEndpointImpl endpoint;
  private final ChannelHandlerContext chctx;
  private final MqttServerOptions options;

  public MqttServerConnection(NetSocketInternal so,
                              Handler<MqttEndpoint> endpointHandler,
                              Handler<Throwable> exceptionHandler,
                              MqttServerOptions options) {
    this.so = so;
    this.endpointHandler = endpointHandler;
    this.exceptionHandler = exceptionHandler;
    this.chctx = so.channelHandlerContext();
    this.options = options;
  }

  /**
   * Handle the MQTT message received by the remote MQTT client
   *
   * @param msg message to handle
   */
  void handleMessage(Object msg) {

    // handling directly native Netty MQTT messages, some of them are translated
    // to the related Vert.x ones for polyglotization
    if (msg instanceof io.netty.handler.codec.mqtt.MqttMessage) {

      io.netty.handler.codec.mqtt.MqttMessage mqttMessage = (io.netty.handler.codec.mqtt.MqttMessage) msg;

      DecoderResult result = mqttMessage.decoderResult();
      if (result.isFailure()) {
        Throwable cause = result.cause();
        if (cause instanceof MqttUnacceptableProtocolVersionException) {
          endpoint = new MqttEndpointImpl(so, null, null, null, false, 0, null, 0);
          endpoint.reject(MqttConnectReturnCode.CONNECTION_REFUSED_UNACCEPTABLE_PROTOCOL_VERSION);
        } else {
          chctx.pipeline().fireExceptionCaught(result.cause());
        }
        return;
      }
      if (!result.isFinished()) {
        chctx.pipeline().fireExceptionCaught(new Exception("Unfinished message"));
        return;
      }

      switch (mqttMessage.fixedHeader().messageType()) {

        case CONNECT:
          handleConnect((MqttConnectMessage) msg);
          break;

        case SUBSCRIBE:

          io.netty.handler.codec.mqtt.MqttSubscribeMessage subscribe = (io.netty.handler.codec.mqtt.MqttSubscribeMessage) mqttMessage;

          MqttSubscribeMessage mqttSubscribeMessage = MqttSubscribeMessage.create(
            subscribe.variableHeader().messageId(),
            subscribe.payload().topicSubscriptions());
          this.handleSubscribe(mqttSubscribeMessage);
          break;

        case UNSUBSCRIBE:

          io.netty.handler.codec.mqtt.MqttUnsubscribeMessage unsubscribe = (io.netty.handler.codec.mqtt.MqttUnsubscribeMessage) mqttMessage;

          MqttUnsubscribeMessage mqttUnsubscribeMessage = MqttUnsubscribeMessage.create(
            unsubscribe.variableHeader().messageId(),
            unsubscribe.payload().topics());
          this.handleUnsubscribe(mqttUnsubscribeMessage);
          break;

        case PUBLISH:

          io.netty.handler.codec.mqtt.MqttPublishMessage publish = (io.netty.handler.codec.mqtt.MqttPublishMessage) mqttMessage;
          ByteBuf newBuf = VertxHandler.safeBuffer(publish.payload(), this.chctx.alloc());

          MqttPublishMessage mqttPublishMessage = MqttPublishMessage.create(
            publish.variableHeader().packetId(),
            publish.fixedHeader().qosLevel(),
            publish.fixedHeader().isDup(),
            publish.fixedHeader().isRetain(),
            publish.variableHeader().topicName(),
            newBuf);
          this.handlePublish(mqttPublishMessage);
          break;

        case PUBACK:

          io.netty.handler.codec.mqtt.MqttPubAckMessage mqttPubackMessage = (io.netty.handler.codec.mqtt.MqttPubAckMessage) mqttMessage;
          this.handlePuback(mqttPubackMessage.variableHeader().messageId());
          break;

        case PUBREC:

          int pubrecMessageId = ((io.netty.handler.codec.mqtt.MqttMessageIdVariableHeader) mqttMessage.variableHeader()).messageId();
          this.handlePubrec(pubrecMessageId);
          break;

        case PUBREL:

          int pubrelMessageId = ((io.netty.handler.codec.mqtt.MqttMessageIdVariableHeader) mqttMessage.variableHeader()).messageId();
          this.handlePubrel(pubrelMessageId);
          break;

        case PUBCOMP:

          int pubcompMessageId = ((io.netty.handler.codec.mqtt.MqttMessageIdVariableHeader) mqttMessage.variableHeader()).messageId();
          this.handlePubcomp(pubcompMessageId);
          break;

        case PINGREQ:

          this.handlePingreq();
          break;

        case DISCONNECT:

          this.handleDisconnect();
          break;

        default:

          this.chctx.fireExceptionCaught(new Exception("Wrong MQTT message type " + mqttMessage.fixedHeader().messageType()));
          break;

      }

    } else {

      this.chctx.fireExceptionCaught(new Exception("Wrong message type " + msg.getClass().getName()));
    }
  }

  /**
   * Used for calling the endpoint handler when a connection is established with a remote MQTT client
   */
  private void handleConnect(MqttConnectMessage msg) {

    // if client sent one more CONNECT packet
    if (endpoint != null) {
      //we should treat it as a protocol violation and disconnect the client
      endpoint.close();
      return;
    }

    // retrieve will information from CONNECT message
    MqttWill will =
      new MqttWill(msg.variableHeader().isWillFlag(),
        msg.payload().willTopic(),
        msg.payload().willMessageInBytes() != null ? Buffer.buffer(msg.payload().willMessageInBytes()) : null,
        msg.variableHeader().willQos(),
        msg.variableHeader().isWillRetain());

    // retrieve authorization information from CONNECT message
    MqttAuth auth = (msg.variableHeader().hasUserName() &&
      msg.variableHeader().hasPassword()) ?
      new MqttAuth(
        msg.payload().userName(),
        msg.payload().password()) : null;

    // check if remote MQTT client didn't specify a client-id
    boolean isZeroBytes = (msg.payload().clientIdentifier() == null) ||
                          msg.payload().clientIdentifier().isEmpty();

    String clientIdentifier = null;

    // client-id got from payload or auto-generated (according to options)
    if (!isZeroBytes) {
      clientIdentifier = msg.payload().clientIdentifier();
    } else if (this.options.isAutoClientId()) {
      clientIdentifier = UUID.randomUUID().toString();
    }

    // create the MQTT endpoint provided to the application handler
    this.endpoint =
      new MqttEndpointImpl(
        so,
        clientIdentifier,
        auth,
        will,
        msg.variableHeader().isCleanSession(),
        msg.variableHeader().version(),
        msg.variableHeader().name(),
        msg.variableHeader().keepAliveTimeSeconds());

    // remove the idle state handler for timeout on CONNECT
    chctx.pipeline().remove("idle");
    chctx.pipeline().remove("timeoutOnConnect");

    // keep alive == 0 means NO keep alive, no timeout to handle
    if (msg.variableHeader().keepAliveTimeSeconds() != 0) {

      // the server waits for one and a half times the keep alive time period (MQTT spec)
      int keepAliveTimeout = (int)(msg.variableHeader().keepAliveTimeSeconds() * 1.5);

      // modifying the channel pipeline for adding the idle state handler with previous timeout
      chctx.pipeline().addBefore("handler", "idle", new IdleStateHandler(keepAliveTimeout, 0, 0));
      chctx.pipeline().addBefore("handler", "keepAliveHandler", new ChannelDuplexHandler() {

        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {

          if (evt instanceof IdleStateEvent) {
            IdleStateEvent e = (IdleStateEvent) evt;
            if (e.state() == IdleState.READER_IDLE) {
              endpoint.close();
            }
          }
        }
      });
    }

    // MQTT spec 3.1.1 : if client-id is "zero-bytes", clean session MUST be true
    if (isZeroBytes && !msg.variableHeader().isCleanSession()) {
      if (this.exceptionHandler != null) {
        this.exceptionHandler.handle(new VertxException("With zero-length client-id, clean session MUST be true"));
      }
      this.endpoint.reject(MqttConnectReturnCode.CONNECTION_REFUSED_IDENTIFIER_REJECTED);
    } else {

      // an exception at connection level is propagated to the endpoint
      this.so.exceptionHandler(t -> {
        this.endpoint.handleException(t);
      });

      // Used for calling the close handler when the remote MQTT client closes the connection
      this.so.closeHandler(v -> this.endpoint.handleClosed());

      this.endpointHandler.handle(this.endpoint);
    }
  }

  /**
   * Used for calling the subscribe handler when the remote MQTT client subscribes to topics
   *
   * @param msg message with subscribe information
   */
  void handleSubscribe(MqttSubscribeMessage msg) {

    synchronized (this.so) {
      if (this.checkConnected()) {
        this.endpoint.handleSubscribe(msg);
      }
    }
  }

  /**
   * Used for calling the unsubscribe handler when the remote MQTT client unsubscribe to topics
   *
   * @param msg message with unsubscribe information
   */
  void handleUnsubscribe(MqttUnsubscribeMessage msg) {

    synchronized (this.so) {
      if (this.checkConnected()) {
        this.endpoint.handleUnsubscribe(msg);
      }
    }
  }

  /**
   * Used for calling the publish handler when the remote MQTT client publishes a message
   *
   * @param msg published message
   */
  void handlePublish(MqttPublishMessage msg) {

    synchronized (this.so) {
      if (this.checkConnected()) {
        this.endpoint.handlePublish(msg);
      }
    }
  }

  /**
   * Used for calling the puback handler when the remote MQTT client acknowledge a QoS 1 message with puback
   *
   * @param pubackMessageId identifier of the message acknowledged by the remote MQTT client
   */
  void handlePuback(int pubackMessageId) {

    synchronized (this.so) {
      if (this.checkConnected()) {
        this.endpoint.handlePuback(pubackMessageId);
      }
    }
  }

  /**
   * Used for calling the pubrec handler when the remote MQTT client acknowledge a QoS 2 message with pubrec
   *
   * @param pubrecMessageId identifier of the message acknowledged by the remote MQTT client
   */
  void handlePubrec(int pubrecMessageId) {

    synchronized (this.so) {
      if (this.checkConnected()) {
        this.endpoint.handlePubrec(pubrecMessageId);
      }
    }
  }

  /**
   * Used for calling the pubrel handler when the remote MQTT client acknowledge a QoS 2 message with pubrel
   *
   * @param pubrelMessageId identifier of the message acknowledged by the remote MQTT client
   */
  void handlePubrel(int pubrelMessageId) {

    synchronized (this.so) {
      if (this.checkConnected()) {
        this.endpoint.handlePubrel(pubrelMessageId);
      }
    }
  }

  /**
   * Used for calling the pubcomp handler when the remote MQTT client acknowledge a QoS 2 message with pubcomp
   *
   * @param pubcompMessageId identifier of the message acknowledged by the remote MQTT client
   */
  void handlePubcomp(int pubcompMessageId) {

    synchronized (this.so) {
      if (this.checkConnected()) {
        this.endpoint.handlePubcomp(pubcompMessageId);
      }
    }
  }

  /**
   * Used internally for handling the pinreq from the remote MQTT client
   */
  void handlePingreq() {

    synchronized (this.so) {
      if (this.checkConnected()) {
        this.endpoint.handlePingreq();
      }
    }
  }

  /**
   * Used for calling the disconnect handler when the remote MQTT client disconnects
   */
  void handleDisconnect() {

    synchronized (this.so) {
      if (this.checkConnected()) {
        this.endpoint.handleDisconnect();
      }
    }
  }

  /**
   * Check if the endpoint was created and is connected
   *
   * @return  status of the endpoint (connected or not)
   */
  private boolean checkConnected() {

    synchronized (this.so) {
      if ((this.endpoint != null) && (this.endpoint.isConnected())) {
        return true;
      } else {
        so.close();
        throw new IllegalStateException("Received an MQTT packet from a not connected client (CONNECT not sent yet)");
      }
    }
  }
}
