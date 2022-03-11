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
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.mqtt.MqttConnAckVariableHeader;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttFixedHeader;
import io.netty.handler.codec.mqtt.MqttMessageFactory;
import io.netty.handler.codec.mqtt.MqttMessageIdAndPropertiesVariableHeader;
import io.netty.handler.codec.mqtt.MqttMessageIdVariableHeader;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttPubReplyMessageVariableHeader;
import io.netty.handler.codec.mqtt.MqttPublishVariableHeader;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttReasonCodeAndPropertiesVariableHeader;
import io.netty.handler.codec.mqtt.MqttSubAckPayload;
import io.netty.handler.codec.mqtt.MqttUnsubAckPayload;
import io.netty.handler.codec.mqtt.MqttVersion;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.impl.NetSocketInternal;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.net.SocketAddress;
import io.vertx.mqtt.MqttAuth;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.MqttTopicSubscription;
import io.vertx.mqtt.MqttWill;
import io.vertx.mqtt.messages.MqttDisconnectMessage;
import io.vertx.mqtt.messages.MqttPubAckMessage;
import io.vertx.mqtt.messages.MqttPubCompMessage;
import io.vertx.mqtt.messages.MqttPubRecMessage;
import io.vertx.mqtt.messages.MqttPubRelMessage;
import io.vertx.mqtt.messages.codes.MqttDisconnectReasonCode;
import io.vertx.mqtt.messages.codes.MqttPubAckReasonCode;
import io.vertx.mqtt.messages.codes.MqttPubCompReasonCode;
import io.vertx.mqtt.messages.codes.MqttPubRecReasonCode;
import io.vertx.mqtt.messages.codes.MqttPubRelReasonCode;
import io.vertx.mqtt.messages.codes.MqttSubAckReasonCode;
import io.vertx.mqtt.messages.codes.MqttUnsubAckReasonCode;

import javax.net.ssl.SSLSession;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Represents an MQTT endpoint for point-to-point communication with the remote MQTT client
 */
public class MqttEndpointImpl implements MqttEndpoint {

  private static final int MAX_MESSAGE_ID = 65535;

  private static final Logger log = LoggerFactory.getLogger(MqttEndpointImpl.class);

  // connection to the remote MQTT client
  private final NetSocketInternal conn;

  // information about connected remote MQTT client (from CONNECT message)
  private String clientIdentifier;
  private final MqttAuth auth;
  private final MqttWill will;
  private final boolean isCleanSession;
  private final int protocolVersion;
  private final String protocolName;
  private final int keepAliveTimeoutSeconds;
  private final MqttProperties connectProperties;

  // information about connected remote MQTT client (from WebSocket handshake)
  private final MultiMap httpHeaders;
  private String httpRequestUri;

  // handler to call when a subscribe request comes in
  private Handler<io.vertx.mqtt.messages.MqttSubscribeMessage> subscribeHandler;
  // handler to call when a unsubscribe request comes in
  private Handler<io.vertx.mqtt.messages.MqttUnsubscribeMessage> unsubscribeHandler;
  // handler to call when a publish message comes in
  private Handler<io.vertx.mqtt.messages.MqttPublishMessage> publishHandler;
  // handler to call when a puback message comes in
  private Handler<Integer> pubackHandler;
  private Handler<MqttPubAckMessage> pubackHandlerWithMessage;
  // handler to call when a pubrec message comes in
  private Handler<Integer> pubrecHandler;
  private Handler<MqttPubRecMessage> pubrecHandlerWithMessage;
  // handler to call when a pubrel message comes in
  private Handler<Integer> pubrelHandler;
  private Handler<MqttPubRelMessage> pubrelHandlerWithMessage;
  // handler to call when a pubcomp message comes in
  private Handler<Integer> pubcompHandler;
  private Handler<MqttPubCompMessage> pubcompHandlerWithMessage;
  // handler to call when a disconnect request comes in
  private Handler<Void> disconnectHandler;
  private Handler<MqttDisconnectMessage> disconnectHandlerWithMessage;
  // handler to call when a pingreq message comes in
  private Handler<Void> pingreqHandler;
  // handler to call when the endpoint is isClosed
  private Handler<Void> closeHandler;
  // handler to call when a problem at protocol level happens
  private Handler<Throwable> exceptionHandler;

  private boolean isConnected;
  private boolean isClosed;
  // counter for the message identifier
  private int messageIdCounter;
  // if the endpoint handles subscription/unsubscription requests with auto acknowledge
  private boolean isSubscriptionAutoAck;
  // if the endpoint handles publishing (in/out) with auto acknowledge
  private boolean isPublishAutoAck;
  // if the endpoint should sends the pingresp automatically
  private boolean isAutoKeepAlive = true;

  /**
   * Constructor
   *
   * @param conn                 connection instance with the remote MQTT client
   * @param clientIdentifier     client identifier of the remote
   * @param auth                 instance with the authentication information
   * @param will                 instance with the will information
   * @param isCleanSession       if the sessione should be cleaned or not
   * @param protocolVersion      protocol version required by the client
   * @param protocolName         protocol name sent by the client
   * @param keepAliveTimeoutSeconds keep alive timeout (in seconds)
   * @param connectProperties    MQTT properties of the CONNECT message
   */
  public MqttEndpointImpl(NetSocketInternal conn, String clientIdentifier, MqttAuth auth, MqttWill will, boolean isCleanSession, int protocolVersion, String protocolName, int keepAliveTimeoutSeconds, MqttProperties connectProperties, MultiMap httpHeaders, String httpRequestUri) {
    this.conn = conn;
    this.clientIdentifier = clientIdentifier;
    this.auth = auth;
    this.will = will;
    this.isCleanSession = isCleanSession;
    this.protocolVersion = protocolVersion;
    this.protocolName = protocolName;
    this.keepAliveTimeoutSeconds = keepAliveTimeoutSeconds;
    this.connectProperties = connectProperties;
    this.httpHeaders = httpHeaders;
    this.httpRequestUri = httpRequestUri;
  }

  public String clientIdentifier() {
    return this.clientIdentifier;
  }

  public MqttAuth auth() {
    return this.auth;
  }

  public MqttWill will() {
    return this.will;
  }

  public boolean isCleanSession() {
    return this.isCleanSession;
  }

  public int protocolVersion() {
    return this.protocolVersion;
  }

  public String protocolName() {
    return this.protocolName;
  }

  public int keepAliveTimeSeconds() {
    return this.keepAliveTimeoutSeconds;
  }

  public int lastMessageId() {
    return this.messageIdCounter;
  }

  public void subscriptionAutoAck(boolean isSubscriptionAutoAck) {
    this.isSubscriptionAutoAck = isSubscriptionAutoAck;
  }

  public boolean isSubscriptionAutoAck() {
    return this.isSubscriptionAutoAck;
  }

  public MqttEndpoint publishAutoAck(boolean isPublishAutoAck) {

    this.isPublishAutoAck = isPublishAutoAck;
    return this;
  }

  public boolean isPublishAutoAck() {
    return this.isPublishAutoAck;
  }

  public MqttEndpoint autoKeepAlive(boolean isAutoKeepAlive) {

    this.isAutoKeepAlive = isAutoKeepAlive;
    return this;
  }

  public boolean isAutoKeepAlive() {
    return this.isAutoKeepAlive;
  }

  public boolean isConnected() {
    synchronized (this.conn) {
      return this.isConnected;
    }
  }

  @Override
  public MqttProperties connectProperties() {
    return this.connectProperties;
  }

  public MqttEndpoint setClientIdentifier(String clientIdentifier) {

    synchronized (this.conn) {
      this.clientIdentifier = clientIdentifier;
    }
    return this;
  }

  public MqttEndpointImpl disconnectHandler(Handler<Void> handler) {

    synchronized (this.conn) {
      this.checkClosed();
      this.disconnectHandler = handler;
      return this;
    }
  }

  public MqttEndpointImpl disconnectMessageHandler(Handler<MqttDisconnectMessage> handler) {
    synchronized (this.conn) {
      this.checkClosed();
      this.disconnectHandlerWithMessage = handler;
      return this;
    }
  }


  public MqttEndpointImpl subscribeHandler(Handler<io.vertx.mqtt.messages.MqttSubscribeMessage> handler) {

    synchronized (this.conn) {
      this.checkClosed();
      this.subscribeHandler = handler;
      return this;
    }
  }

  public MqttEndpointImpl unsubscribeHandler(Handler<io.vertx.mqtt.messages.MqttUnsubscribeMessage> handler) {

    synchronized (this.conn) {
      this.checkClosed();
      this.unsubscribeHandler = handler;
      return this;
    }
  }

  public MqttEndpointImpl publishHandler(Handler<io.vertx.mqtt.messages.MqttPublishMessage> handler) {

    synchronized (this.conn) {
      this.checkClosed();
      this.publishHandler = handler;
      return this;
    }
  }

  public MqttEndpointImpl publishAcknowledgeHandler(Handler<Integer> handler) {

    synchronized (this.conn) {
      this.checkClosed();
      this.pubackHandler = handler;
      return this;
    }
  }

  public MqttEndpointImpl publishAcknowledgeMessageHandler(Handler<MqttPubAckMessage> handler) {
    synchronized (this.conn) {
      this.checkClosed();
      this.pubackHandlerWithMessage = handler;
      return this;
    }
  }


  public MqttEndpointImpl publishReceivedHandler(Handler<Integer> handler) {

    synchronized (this.conn) {
      this.checkClosed();
      this.pubrecHandler = handler;
      return this;
    }
  }

  public MqttEndpointImpl publishReceivedMessageHandler(Handler<MqttPubRecMessage> handler) {
    synchronized (this.conn) {
      this.checkClosed();
      this.pubrecHandlerWithMessage = handler;
      return this;
    }
  }


  public MqttEndpointImpl publishReleaseHandler(Handler<Integer> handler) {

    synchronized (this.conn) {
      this.checkClosed();
      this.pubrelHandler = handler;
      return this;
    }
  }

  public MqttEndpointImpl publishReleaseMessageHandler(Handler<MqttPubRelMessage> handler) {
    synchronized (this.conn) {
      this.checkClosed();
      this.pubrelHandlerWithMessage = handler;
      return this;
    }
  }


  public MqttEndpointImpl publishCompletionHandler(Handler<Integer> handler) {

    synchronized (this.conn) {
      this.checkClosed();
      this.pubcompHandler = handler;
      return this;
    }
  }

  public MqttEndpointImpl publishCompletionMessageHandler(Handler<MqttPubCompMessage> handler) {
    synchronized (this.conn) {
      this.checkClosed();
      this.pubcompHandlerWithMessage = handler;
      return this;
    }
  }


  public MqttEndpointImpl pingHandler(Handler<Void> handler) {

    synchronized (this.conn) {
      this.checkClosed();
      this.pingreqHandler = handler;
      return this;
    }
  }

  public MqttEndpointImpl closeHandler(Handler<Void> handler) {

    synchronized (this.conn) {
      this.checkClosed();
      this.closeHandler = handler;
      return this;
    }
  }

  public MqttEndpointImpl exceptionHandler(Handler<Throwable> handler) {

    synchronized (this.conn) {
      this.checkClosed();
      this.exceptionHandler = handler;
      return this;
    }
  }

  private MqttEndpointImpl connack(MqttConnectReturnCode returnCode, boolean sessionPresent, MqttProperties properties) {

    MqttFixedHeader fixedHeader =
      new MqttFixedHeader(MqttMessageType.CONNACK, false, MqttQoS.AT_MOST_ONCE, false, 0);
    MqttConnAckVariableHeader variableHeader =
      new MqttConnAckVariableHeader(returnCode, sessionPresent, properties);

    io.netty.handler.codec.mqtt.MqttMessage connack = MqttMessageFactory.newMessage(fixedHeader, variableHeader, null);

    this.write(connack);

    // if a server sends a CONNACK packet containing a non zero return code it MUST then close the Network Connection (MQTT 3.1.1 spec)
    if (returnCode != MqttConnectReturnCode.CONNECTION_ACCEPTED) {
      this.close();
    } else {
      this.isConnected = true;
    }

    return this;
  }

  @Override
  public MqttEndpoint accept() {
    return accept(false);
  }

  public MqttEndpointImpl accept(boolean sessionPresent) {
    return accept(sessionPresent, MqttProperties.NO_PROPERTIES);
  }

  public MqttEndpointImpl accept(boolean sessionPresent, MqttProperties properties) {
    synchronized (conn) {
      if (this.isConnected) {
        throw new IllegalStateException("Connection already accepted");
      }

      return this.connack(MqttConnectReturnCode.CONNECTION_ACCEPTED, sessionPresent, properties);
    }
  }

  public MqttEndpointImpl reject(MqttConnectReturnCode returnCode) {
    return reject(returnCode, MqttProperties.NO_PROPERTIES);
  }

  public MqttEndpointImpl reject(MqttConnectReturnCode returnCode, MqttProperties properties) {

    synchronized (conn) {
      if (returnCode == MqttConnectReturnCode.CONNECTION_ACCEPTED) {
        throw new IllegalArgumentException("Need to use the 'accept' method for accepting connection");
      }

      // sessionPresent flag has no meaning in this case, the network connection will be closed
      return this.connack(returnCode, false, properties);
    }
  }


  public MqttEndpointImpl subscribeAcknowledge(int subscribeMessageId, List<MqttQoS> grantedQoSLevels) {
    return subscribeAcknowledgeWithCode(subscribeMessageId,
      grantedQoSLevels.stream().mapToInt(MqttQoS::value).toArray(),
      MqttProperties.NO_PROPERTIES);
  }

  public MqttEndpointImpl subscribeAcknowledge(int subscribeMessageId, List<MqttSubAckReasonCode> reasonCodes, MqttProperties properties) {
    return subscribeAcknowledgeWithCode(subscribeMessageId, reasonCodes.stream().mapToInt(MqttSubAckReasonCode::value).toArray(), properties);
  }

  private MqttEndpointImpl subscribeAcknowledgeWithCode(int subscribeMessageId, int[] reasonCodes, MqttProperties properties) {
    MqttFixedHeader fixedHeader =
      new MqttFixedHeader(MqttMessageType.SUBACK, false, MqttQoS.AT_MOST_ONCE, false, 0);
    MqttMessageIdAndPropertiesVariableHeader variableHeader =
      new MqttMessageIdAndPropertiesVariableHeader(subscribeMessageId, properties);

    MqttSubAckPayload payload = new MqttSubAckPayload(reasonCodes);

    io.netty.handler.codec.mqtt.MqttMessage suback = MqttMessageFactory.newMessage(fixedHeader, variableHeader, payload);

    this.write(suback);

    return this;
  }

  public MqttEndpointImpl unsubscribeAcknowledge(int unsubscribeMessageId) {
    return unsubscribeAcknowledge(unsubscribeMessageId, Collections.emptyList(), MqttProperties.NO_PROPERTIES);
  }

  public MqttEndpointImpl unsubscribeAcknowledge(int unsubscribeMessageId, List<MqttUnsubAckReasonCode> reasonCodes, MqttProperties properties) {
    MqttFixedHeader fixedHeader =
      new MqttFixedHeader(MqttMessageType.UNSUBACK, false, MqttQoS.AT_MOST_ONCE, false, 0);
    MqttMessageIdAndPropertiesVariableHeader variableHeader =
      new MqttMessageIdAndPropertiesVariableHeader(unsubscribeMessageId, properties);

    short[] reasoneCodesNum = new short[reasonCodes.size()];
    for(int i = 0; i < reasoneCodesNum.length; i++) {
      reasoneCodesNum[i] = reasonCodes.get(i).value();
    }
    MqttUnsubAckPayload payload = new MqttUnsubAckPayload(reasoneCodesNum);

    io.netty.handler.codec.mqtt.MqttMessage unsuback = MqttMessageFactory.newMessage(fixedHeader, variableHeader, payload);

    this.write(unsuback);

    return this;
  }


  public MqttEndpointImpl publishAcknowledge(int publishMessageId) {
    return publishAcknowledge(publishMessageId, MqttPubAckReasonCode.SUCCESS, MqttProperties.NO_PROPERTIES);
  }

  public MqttEndpointImpl publishAcknowledge(int publishMessageId, MqttPubAckReasonCode reasonCode, MqttProperties
    properties) {

    MqttFixedHeader fixedHeader =
      new MqttFixedHeader(MqttMessageType.PUBACK, false, MqttQoS.AT_MOST_ONCE, false, 0);
    MqttPubReplyMessageVariableHeader variableHeader =
      new MqttPubReplyMessageVariableHeader(publishMessageId, reasonCode.value(), properties);

    io.netty.handler.codec.mqtt.MqttMessage puback = MqttMessageFactory.newMessage(fixedHeader, variableHeader, null);

    this.write(puback);

    return this;
  }

  public MqttEndpointImpl publishReceived(int publishMessageId) {
    return publishReceived(publishMessageId, MqttPubRecReasonCode.SUCCESS, MqttProperties.NO_PROPERTIES);
  }

  public MqttEndpointImpl publishReceived(int publishMessageId, MqttPubRecReasonCode reasonCode, MqttProperties properties) {
    MqttFixedHeader fixedHeader =
      new MqttFixedHeader(MqttMessageType.PUBREC, false, MqttQoS.AT_MOST_ONCE, false, 0);
    MqttMessageIdAndPropertiesVariableHeader variableHeader =
      new MqttMessageIdAndPropertiesVariableHeader(publishMessageId, properties);

    io.netty.handler.codec.mqtt.MqttMessage pubrec = MqttMessageFactory.newMessage(fixedHeader, variableHeader, null);

    this.write(pubrec);

    return this;
  }


  public MqttEndpointImpl publishRelease(int publishMessageId) {
    return publishRelease(publishMessageId, MqttPubRelReasonCode.SUCCESS, MqttProperties.NO_PROPERTIES);
  }

  public MqttEndpointImpl publishRelease(int publishMessageId, MqttPubRelReasonCode reasonCode, MqttProperties properties) {
    MqttFixedHeader fixedHeader =
      new MqttFixedHeader(MqttMessageType.PUBREL, false, MqttQoS.AT_LEAST_ONCE, false, 0);
    MqttMessageIdAndPropertiesVariableHeader variableHeader =
      new MqttMessageIdAndPropertiesVariableHeader(publishMessageId, properties);

    io.netty.handler.codec.mqtt.MqttMessage pubrel = MqttMessageFactory.newMessage(fixedHeader, variableHeader, null);

    this.write(pubrel);

    return this;
  }


  public MqttEndpointImpl publishComplete(int publishMessageId) {
    return publishComplete(publishMessageId, MqttPubCompReasonCode.SUCCESS, MqttProperties.NO_PROPERTIES);
  }

  public MqttEndpointImpl publishComplete(int publishMessageId, MqttPubCompReasonCode reasonCode, MqttProperties properties) {

    MqttFixedHeader fixedHeader =
      new MqttFixedHeader(MqttMessageType.PUBCOMP, false, MqttQoS.AT_MOST_ONCE, false, 0);
    MqttMessageIdAndPropertiesVariableHeader variableHeader =
      new MqttMessageIdAndPropertiesVariableHeader(publishMessageId, properties);

    io.netty.handler.codec.mqtt.MqttMessage pubcomp = MqttMessageFactory.newMessage(fixedHeader, variableHeader, null);

    this.write(pubcomp);

    return this;
  }


  @Override
  public Future<Integer> publish(String topic, Buffer payload, MqttQoS qosLevel, boolean isDup, boolean isRetain) {
    return publish(topic, payload, qosLevel, isDup, isRetain, this.nextMessageId());
  }

  @Override
  public MqttEndpointImpl publish(String topic, Buffer payload, MqttQoS qosLevel, boolean isDup, boolean isRetain, Handler<AsyncResult<Integer>> publishSentHandler) {
    return publish(topic, payload, qosLevel, isDup, isRetain, this.nextMessageId(), publishSentHandler);
  }

  @Override
  public Future<Integer> publish(String topic, Buffer payload, MqttQoS qosLevel, boolean isDup, boolean isRetain, int messageId) {
    return publish(topic, payload, qosLevel, isDup, isRetain, messageId, MqttProperties.NO_PROPERTIES);
  }

  @Override
  public Future<Integer> publish(String topic, Buffer payload, MqttQoS qosLevel, boolean isDup, boolean isRetain, int messageId, MqttProperties properties) {
    if (messageId > MAX_MESSAGE_ID || messageId < 0) {
      throw new IllegalArgumentException("messageId must be non-negative integer not larger than " + MAX_MESSAGE_ID);
    }

    MqttFixedHeader fixedHeader =
      new MqttFixedHeader(MqttMessageType.PUBLISH, isDup, qosLevel, isRetain, 0);
    MqttPublishVariableHeader variableHeader =
      new MqttPublishVariableHeader(topic, messageId, properties);

    ByteBuf buf = Unpooled.copiedBuffer(payload.getBytes());

    io.netty.handler.codec.mqtt.MqttMessage publish = MqttMessageFactory.newMessage(fixedHeader, variableHeader, buf);

    return this.write(publish).map(variableHeader.packetId());
  }

  @Override
  public MqttEndpointImpl publish(String topic,
                                  Buffer payload,
                                  MqttQoS qosLevel,
                                  boolean isDup,
                                  boolean isRetain,
                                  int messageId,
                                  Handler<AsyncResult<Integer>> publishSentHandler) {
    return publish(topic, payload, qosLevel, isDup, isRetain, messageId, MqttProperties.NO_PROPERTIES, publishSentHandler);
  }

  @Override
  public MqttEndpointImpl publish(String topic,
                                  Buffer payload,
                                  MqttQoS qosLevel,
                                  boolean isDup,
                                  boolean isRetain,
                                  int messageId,
                                  MqttProperties properties,
                                  Handler<AsyncResult<Integer>> publishSentHandler) {
    Future<Integer> fut = publish(topic, payload, qosLevel, isDup, isRetain, messageId, properties);
    if (publishSentHandler != null) {
      fut.onComplete(publishSentHandler);
    }
    return this;
  }

  public MqttEndpointImpl pong() {

    MqttFixedHeader fixedHeader =
      new MqttFixedHeader(MqttMessageType.PINGRESP, false, MqttQoS.AT_MOST_ONCE, false, 0);

    io.netty.handler.codec.mqtt.MqttMessage pingresp = MqttMessageFactory.newMessage(fixedHeader, null, null);

    this.write(pingresp);

    return this;
  }

  public MqttEndpointImpl disconnect(MqttDisconnectReasonCode code, MqttProperties properties) {
    if(protocolVersion >= MqttVersion.MQTT_5.protocolLevel()) {
      MqttFixedHeader fixedHeader =
        new MqttFixedHeader(MqttMessageType.DISCONNECT, false, MqttQoS.AT_MOST_ONCE, false, 0);

      MqttReasonCodeAndPropertiesVariableHeader variableHeader = new MqttReasonCodeAndPropertiesVariableHeader(code.value(), properties);

      io.netty.handler.codec.mqtt.MqttMessage disconnect = MqttMessageFactory.newMessage(fixedHeader, variableHeader, null);

      this.write(disconnect);
    }

    this.close();

    return this;
  }

  /**
   * Used for calling the subscribe handler when the remote MQTT client subscribes to topics
   *
   * @param msg message with subscribe information
   */
  void handleSubscribe(io.vertx.mqtt.messages.MqttSubscribeMessage msg) {

    synchronized (this.conn) {
      if (this.subscribeHandler != null) {
        this.subscribeHandler.handle(msg);
      }

      // with auto ack enabled, the requested QoS levels are granted
      if (this.isSubscriptionAutoAck) {
        this.subscribeAcknowledge(msg.messageId(), msg.topicSubscriptions()
          .stream()
          .map(MqttTopicSubscription::qualityOfService)
          .collect(Collectors.toList()));
      }
    }
  }

  /**
   * Used for calling the unsubscribe handler when the remote MQTT client unsubscribes to topics
   *
   * @param msg message with unsubscribe information
   */
  void handleUnsubscribe(io.vertx.mqtt.messages.MqttUnsubscribeMessage msg) {

    synchronized (this.conn) {
      if (this.unsubscribeHandler != null) {
        this.unsubscribeHandler.handle(msg);
      }

      if (this.isSubscriptionAutoAck) {
        this.unsubscribeAcknowledge(msg.messageId());
      }
    }
  }

  /**
   * Used for calling the publish handler when the remote MQTT client publishes a message
   *
   * @param msg published message
   */
  void handlePublish(io.vertx.mqtt.messages.MqttPublishMessage msg) {

    synchronized (this.conn) {
      if (this.publishHandler != null) {
        this.publishHandler.handle(msg);
      }

      if (this.isPublishAutoAck) {

        switch (msg.qosLevel()) {

          case AT_LEAST_ONCE:
            this.publishAcknowledge(msg.messageId());
            break;

          case EXACTLY_ONCE:
            this.publishReceived(msg.messageId());
            break;
        }
      }
    }
  }

  /**
   * Used for calling the puback handler when the remote MQTT client acknowledge a QoS 1 message with puback
   *
   * @param pubackMessageId identifier of the message acknowledged by the remote MQTT client
   * @param code reason code
   * @param properties MQTT properties
   */
  void handlePuback(int pubackMessageId, MqttPubAckReasonCode code, MqttProperties properties) {

    synchronized (this.conn) {
      if (this.pubackHandler != null) {
        this.pubackHandler.handle(pubackMessageId);
      }
      if (this.pubackHandlerWithMessage != null) {
        this.pubackHandlerWithMessage.handle(MqttPubAckMessage.create(pubackMessageId, code, properties));
      }
    }
  }

  /**
   * Used for calling the pubrec handler when the remote MQTT client acknowledge a QoS 2 message with pubrec
   *
   * @param pubrecMessageId identifier of the message acknowledged by the remote MQTT client
   * @param code reason code
   * @param properties MQTT properties
   */
  void handlePubrec(int pubrecMessageId, MqttPubRecReasonCode code, MqttProperties properties) {
    synchronized (this.conn) {
      if (this.pubrecHandler != null) {
        this.pubrecHandler.handle(pubrecMessageId);
      }
      if(this.pubrecHandlerWithMessage != null) {
        this.pubrecHandlerWithMessage.handle(MqttPubRecMessage.create(pubrecMessageId, code, properties));
      }

      if (this.isPublishAutoAck) {
        this.publishRelease(pubrecMessageId);
      }
    }
  }

  /**
   * Used for calling the pubrel handler when the remote MQTT client acknowledge a QoS 2 message with pubrel
   *
   * @param pubrelMessageId identifier of the message acknowledged by the remote MQTT client
   * @param code reason code
   * @param properties MQTT message properties
   */
  void handlePubrel(int pubrelMessageId, MqttPubRelReasonCode code, MqttProperties properties) {

    synchronized (this.conn) {
      if (this.pubrelHandler != null) {
        this.pubrelHandler.handle(pubrelMessageId);
      }
      if (this.pubrelHandlerWithMessage != null) {
        this.pubrelHandlerWithMessage.handle(MqttPubRelMessage.create(pubrelMessageId, code, properties));
      }

      if (this.isPublishAutoAck) {
        this.publishComplete(pubrelMessageId);
      }
    }
  }

  /**
   * Used for calling the pubcomp handler when the remote MQTT client acknowledge a QoS 2 message with pubcomp
   *
   * @param pubcompMessageId identifier of the message acknowledged by the remote MQTT client
   */
  void handlePubcomp(int pubcompMessageId, MqttPubCompReasonCode code, MqttProperties properties) {
    synchronized (this.conn) {
      if (this.pubcompHandler != null) {
        this.pubcompHandler.handle(pubcompMessageId);
      }
      if (this.pubcompHandlerWithMessage != null) {
        this.pubcompHandlerWithMessage.handle(MqttPubCompMessage.create(pubcompMessageId, code, properties));
      }
    }
  }

  /**
   * Used internally for handling the pinreq from the remote MQTT client
   */
  void handlePingreq() {

    synchronized (this.conn) {

      if (this.pingreqHandler != null) {
        this.pingreqHandler.handle(null);
      }

      if (this.isAutoKeepAlive) {
        this.pong();
      }
    }
  }

  /**
   * Used for calling the disconnect handler when the remote MQTT client disconnects
   * @param code status code
   * @param properties MQTT message properties
   */
  void handleDisconnect(MqttDisconnectReasonCode code, MqttProperties properties) {

    synchronized (this.conn) {
      if (this.disconnectHandler != null) {
        this.disconnectHandler.handle(null);
      }
      if (this.disconnectHandlerWithMessage != null) {
        this.disconnectHandlerWithMessage.handle(MqttDisconnectMessage.create(code, properties));
      }
      // if client didn't close the connection, the sever SHOULD close it (MQTT spec)
      this.close();
    }
  }

  /**
   * Used for calling the close handler when the remote MQTT client closes the connection
   */
  void handleClosed() {

    synchronized (this.conn) {
      this.cleanup();

      if (this.closeHandler != null) {
        this.closeHandler.handle(null);
      }
    }
  }

  /**
   * Used for calling the exception handler when an error at protocol level happens
   *
   * @param t exception raised
   */
  void handleException(Throwable t) {

    synchronized (this.conn) {
      if (this.exceptionHandler != null) {
        this.exceptionHandler.handle(t);
      }
    }
  }

  public void close() {

    synchronized (this.conn) {
      checkClosed();
      this.conn.close();

      this.cleanup();
    }
  }

  public SocketAddress localAddress() {
    synchronized (this.conn) {
      this.checkClosed();
      return conn.localAddress();
    }
  }

  public SocketAddress remoteAddress() {
    synchronized (this.conn) {
      this.checkClosed();
      return conn.remoteAddress();
    }
  }

  public boolean isSsl() {
    synchronized (this.conn) {
      this.checkClosed();
      return conn.isSsl();
    }
  }

  @Override
  public MultiMap httpHeaders() {
    return httpHeaders;
  }

  @Override
  public String httpRequestURI() {
    return httpRequestUri;
  }

  public SSLSession sslSession() {
    synchronized (this.conn) {
      this.checkClosed();
      return this.conn.sslSession();
    }
  }

  private Future<Void> write(io.netty.handler.codec.mqtt.MqttMessage mqttMessage) {
    synchronized (this.conn) {
      if (mqttMessage.fixedHeader().messageType() != MqttMessageType.CONNACK) {
        this.checkConnected();
      }
      return this.conn.writeMessage(mqttMessage);
    }
  }

  /**
   * Check if the MQTT endpoint is closed
   */
  private void checkClosed() {

    if (this.isClosed) {
      throw new IllegalStateException("MQTT endpoint is closed");
    }
  }

  /**
   * Check if the connection was accepted
   */
  private void checkConnected() {

    if (!this.isConnected) {
      throw new IllegalStateException("Connection not accepted yet");
    }
  }

  /**
   * Cleanup
   */
  private void cleanup() {
    if (!this.isClosed) {
      this.isClosed = true;
      this.isConnected = false;
    }
  }

  /**
   * Update and return the next message identifier
   *
   * @return message identifier
   */
  private int nextMessageId() {

    // if 0 or MAX_MESSAGE_ID, it becomes 1 (first valid messageId)
    this.messageIdCounter = ((this.messageIdCounter % MAX_MESSAGE_ID) != 0) ? this.messageIdCounter + 1 : 1;
    return this.messageIdCounter;
  }
}
