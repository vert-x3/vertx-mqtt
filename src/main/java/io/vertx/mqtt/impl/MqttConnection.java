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

import io.netty.channel.Channel;
import io.netty.handler.codec.mqtt.MqttConnectMessage;
import io.netty.handler.timeout.IdleStateHandler;
import io.vertx.core.Handler;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.impl.ConnectionBase;
import io.vertx.core.spi.metrics.NetworkMetrics;
import io.vertx.core.spi.metrics.TCPMetrics;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.messages.MqttPublishMessage;
import io.vertx.mqtt.messages.MqttSubscribeMessage;
import io.vertx.mqtt.messages.MqttUnsubscribeMessage;

/**
 * Represents an MQTT connection with a remote client
 */
public class MqttConnection extends ConnectionBase {

  // handler to call when a remote MQTT client connects and establishes a connection
  private final Handler<MqttEndpoint> endpointHandler;
  // endpoint for handling point-to-point communication with the remote MQTT client
  private MqttEndpointImpl endpoint;
  private final TCPMetrics metrics;

  @Override
  public NetworkMetrics metrics() {
    return metrics;
  }

  /**
   * Constructor
   *
   * @param vertx   Vert.x instance
   * @param channel Channel (netty) used for communication with MQTT remote client
   * @param context Vert.x context
   */
  public MqttConnection(VertxInternal vertx, Channel channel, ContextImpl context, Handler<MqttEndpoint> endpointHandler, TCPMetrics metrics) {
    super(vertx, channel, context);

    this.endpointHandler = endpointHandler;
    this.metrics = metrics;
  }

  @Override
  public Object metric() {
    return null;
  }

  @Override
  protected void handleInterestedOpsChanged() {
  }

  /**
   * Handle the MQTT message received by the remote MQTT client
   *
   * @param msg message to handle
   */
  synchronized void handleMessage(Object msg) {

    // handling directly native Netty MQTT messages because we don't need to
    // expose them at higher level (so no need for polyglotization)
    if (msg instanceof io.netty.handler.codec.mqtt.MqttMessage) {

      io.netty.handler.codec.mqtt.MqttMessage mqttMessage = (io.netty.handler.codec.mqtt.MqttMessage) msg;

      switch (mqttMessage.fixedHeader().messageType()) {

        case CONNECT:
          handleConnect((MqttConnectMessage) msg);
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

          this.channel.pipeline().fireExceptionCaught(new Exception("Wrong message type " + msg.getClass().getName()));
          break;

      }

      // handling mapped Vert.x MQTT messages (from Netty ones) because they'll be provided
      // to the higher layer (so need for ployglotization)
    } else {

      if (msg instanceof MqttSubscribeMessage) {

        this.handleSubscribe((MqttSubscribeMessage) msg);

      } else if (msg instanceof MqttUnsubscribeMessage) {

        this.handleUnsubscribe((MqttUnsubscribeMessage) msg);

      } else if (msg instanceof MqttPublishMessage) {

        this.handlePublish((MqttPublishMessage) msg);

      } else {

        this.channel.pipeline().fireExceptionCaught(new Exception("Wrong message type"));
      }
    }
  }

  /**
   * Used for calling the endpoint handler when a connection is established with a remote MQTT client
   */
  private void handleConnect(MqttConnectMessage msg) {

    // retrieve will information from CONNECT message
    MqttWillImpl will =
      new MqttWillImpl(msg.variableHeader().isWillFlag(),
        msg.payload().willTopic(),
        msg.payload().willMessage(),
        msg.variableHeader().willQos(),
        msg.variableHeader().isWillRetain());

    // retrieve authorization information from CONNECT message
    MqttAuthImpl auth = (msg.variableHeader().hasUserName() &&
      msg.variableHeader().hasPassword()) ?
      new MqttAuthImpl(
        msg.payload().userName(),
        msg.payload().password()) : null;

    // create the MQTT endpoint provided to the application handler
    endpoint =
      new MqttEndpointImpl(
        this,
        msg.payload().clientIdentifier(),
        auth,
        will,
        msg.variableHeader().isCleanSession(),
        msg.variableHeader().version(),
        msg.variableHeader().name(),
        msg.variableHeader().keepAliveTimeSeconds());

    // keep alive == 0 means NO keep alive, no timeout to handle
    if (msg.variableHeader().keepAliveTimeSeconds() != 0) {

      // the server waits for one and a half times the keep alive time period (MQTT spec)
      int timeout = msg.variableHeader().keepAliveTimeSeconds() +
        msg.variableHeader().keepAliveTimeSeconds() / 2;

      // modifying the channel pipeline for adding the idle state handler with previous timeout
      channel.pipeline().addBefore("handler", "idle", new IdleStateHandler(0, 0, timeout));
    }

    endpointHandler.handle(endpoint);
  }

  /**
   * Used for calling the subscribe handler when the remote MQTT client subscribes to topics
   *
   * @param msg message with subscribe information
   */
  synchronized void handleSubscribe(MqttSubscribeMessage msg) {

    if (this.endpoint != null) {
      this.endpoint.handleSubscribe(msg);
    }
  }

  /**
   * Used for calling the unsubscribe handler when the remote MQTT client unsubscribe to topics
   *
   * @param msg message with unsubscribe information
   */
  synchronized void handleUnsubscribe(MqttUnsubscribeMessage msg) {

    if (this.endpoint != null) {
      this.endpoint.handleUnsubscribe(msg);
    }
  }

  /**
   * Used for calling the publish handler when the remote MQTT client publishes a message
   *
   * @param msg published message
   */
  synchronized void handlePublish(MqttPublishMessage msg) {

    if (this.endpoint != null) {
      this.endpoint.handlePublish(msg);
    }
  }

  /**
   * Used for calling the puback handler when the remote MQTT client acknowledge a QoS 1 message with puback
   *
   * @param pubackMessageId identifier of the message acknowledged by the remote MQTT client
   */
  synchronized void handlePuback(int pubackMessageId) {

    if (this.endpoint != null) {
      this.endpoint.handlePuback(pubackMessageId);
    }
  }

  /**
   * Used for calling the pubrec handler when the remote MQTT client acknowledge a QoS 2 message with pubrec
   *
   * @param pubrecMessageId identifier of the message acknowledged by the remote MQTT client
   */
  synchronized void handlePubrec(int pubrecMessageId) {

    if (this.endpoint != null) {
      this.endpoint.handlePubrec(pubrecMessageId);
    }
  }

  /**
   * Used for calling the pubrel handler when the remote MQTT client acknowledge a QoS 2 message with pubrel
   *
   * @param pubrelMessageId identifier of the message acknowledged by the remote MQTT client
   */
  synchronized void handlePubrel(int pubrelMessageId) {

    if (this.endpoint != null) {
      this.endpoint.handlePubrel(pubrelMessageId);
    }
  }

  /**
   * Used for calling the pubcomp handler when the remote MQTT client acknowledge a QoS 2 message with pubcomp
   *
   * @param pubcompMessageId identifier of the message acknowledged by the remote MQTT client
   */
  synchronized void handlePubcomp(int pubcompMessageId) {

    if (this.endpoint != null) {
      this.endpoint.handlePubcomp(pubcompMessageId);
    }
  }

  /**
   * Used internally for handling the pinreq from the remote MQTT client
   */
  synchronized void handlePingreq() {

    if (this.endpoint != null) {
      this.endpoint.handlePingreq();
    }
  }

  /**
   * Used for calling the disconnect handler when the remote MQTT client disconnects
   */
  synchronized void handleDisconnect() {

    if (this.endpoint != null) {
      this.endpoint.handleDisconnect();
    }
  }

  /**
   * Used for calling the close handler when the remote MQTT client closes the connection
   */
  synchronized protected void handleClosed() {

    super.handleClosed();
    if (this.endpoint != null) {
      this.endpoint.handleClosed();
    }
  }
}
