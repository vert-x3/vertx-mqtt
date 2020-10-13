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
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.mqtt.MqttConnectPayload;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttConnectVariableHeader;
import io.netty.handler.codec.mqtt.MqttDecoder;
import io.netty.handler.codec.mqtt.MqttEncoder;
import io.netty.handler.codec.mqtt.MqttFixedHeader;
import io.netty.handler.codec.mqtt.MqttMessageFactory;
import io.netty.handler.codec.mqtt.MqttMessageIdVariableHeader;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttPublishVariableHeader;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttSubscribePayload;
import io.netty.handler.codec.mqtt.MqttTopicSubscription;
import io.netty.handler.codec.mqtt.MqttUnsubscribePayload;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.CloseFuture;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.impl.future.PromiseInternal;
import io.vertx.core.net.impl.NetSocketInternal;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.impl.VertxHandler;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;
import io.vertx.mqtt.MqttConnectionException;
import io.vertx.mqtt.MqttException;
import io.vertx.mqtt.messages.MqttConnAckMessage;
import io.vertx.mqtt.messages.MqttMessage;
import io.vertx.mqtt.messages.MqttPublishMessage;
import io.vertx.mqtt.messages.MqttSubAckMessage;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.netty.handler.codec.mqtt.MqttQoS.*;

/**
 * MQTT client implementation
 */
public class MqttClientImpl implements MqttClient {

  private enum Status { CLOSED, CONNECTING, CONNECTED, CLOSING }

  // patterns for topics validation
  private static final Pattern validTopicNamePattern = Pattern.compile("^[^#+\\u0000]+$");
  private static final Pattern validTopicFilterPattern = Pattern.compile("^(#|((\\+(?![^/]))?([^#+]*(/\\+(?![^/]))?)*(/#)?))$");
  private static final Logger log = LoggerFactory.getLogger(MqttClientImpl.class);

  private static final int MAX_MESSAGE_ID = 65535;
  private static final int MAX_TOPIC_LEN = 65535;
  private static final int MIN_TOPIC_LEN = 1;
  private static final String PROTOCOL_NAME = "MQTT";
  private static final int PROTOCOL_VERSION = 4;
  private static final int DEFAULT_IDLE_TIMEOUT = 0;

  private final VertxInternal vertx;
  private final MqttClientOptions options;
  private NetSocketInternal connection;
  private ContextInternal ctx;

  // handler to call when a publish is complete
  private Handler<Integer> publishCompletionHandler;
  // handler to call when a publish has expired
  private Handler<Integer> publishCompletionExpirationHandler;
  // handler to call when a PUBACK is received for an unknown packetId
  private Handler<Integer> publishCompletionPhantomHandler;
  // handler to call when a unsubscribe request is completed
  private Handler<Integer> unsubscribeCompletionHandler;
  // handler to call when a publish message comes in
  private Handler<MqttPublishMessage> publishHandler;
  // handler to call when a subscribe request is completed
  private Handler<MqttSubAckMessage> subscribeCompletionHandler;
  // handler to call when a connection request is completed
  private Promise<MqttConnAckMessage> connectPromise;
  // handler to call when a connection disconnects
  private Promise<Void> disconnectPromise;
  // handler to call when a pingresp is received
  private Handler<Void> pingrespHandler;
  // handler to call when a problem at protocol level happens
  private Handler<Throwable> exceptionHandler;
  //handler to call when the remote MQTT server closes the connection
  private Handler<Void> closeHandler;

  // storage of PUBLISH QoS=1 messages which was not responded with PUBACK
  private HashMap<Integer, ExpiringPacket> qos1outbound = new HashMap<>();

  // storage of PUBLISH QoS=2 messages which was not responded with PUBREC
  // and PUBREL messages which was not responded with PUBCOMP
  private HashMap<Integer, ExpiringPacket> qos2outbound = new HashMap<>();

  // storage of PUBLISH messages which was responded with PUBREC
  private HashMap<Integer, MqttMessage> qos2inbound = new HashMap<>();

  // counter for the message identifier
  private int messageIdCounter;

  // total number of unacknowledged packets
  private int countInflightQueue;

  private NetClient client;
  private Status status = Status.CLOSED;

  /**
   * Constructor
   *
   * @param vertx Vert.x instance
   * @param options MQTT client options
   */
  public MqttClientImpl(Vertx vertx, MqttClientOptions options) {

    // copy given options
    NetClientOptions netClientOptions = new NetClientOptions(options);
    netClientOptions.setIdleTimeout(DEFAULT_IDLE_TIMEOUT);

    this.vertx = (VertxInternal) vertx;
    this.options = new MqttClientOptions(options);
  }

  int getInFlightMessagesCount() {
    synchronized (this) {
      return countInflightQueue;
    }
  }

  @Override
  public Future<MqttConnAckMessage> connect(int port, String host) {

    return this.doConnect(port, host, null);
  }

  /**
   * See {@link MqttClient#connect(int, String, Handler)} for more details
   */
  @Override
  public MqttClient connect(int port, String host, Handler<AsyncResult<MqttConnAckMessage>> connectHandler) {

    Future<MqttConnAckMessage> fut = connect(port, host);
    if (connectHandler != null) {
      fut.onComplete(connectHandler);
    }
    return this;
  }

  @Override
  public Future<MqttConnAckMessage> connect(int port, String host, String serverName) {

    return connect(port, host, serverName);
  }

  /**
   * See {@link MqttClient#connect(int, String, String, Handler)} for more details
   */
  @Override
  public MqttClient connect(int port, String host, String serverName, Handler<AsyncResult<MqttConnAckMessage>> connectHandler) {

    Future<MqttConnAckMessage> fut = this.doConnect(port, host, serverName);
    if (connectHandler != null) {
      fut.onComplete(connectHandler);
    }
    return this;
  }

  private Future<MqttConnAckMessage> doConnect(int port, String host, String serverName) {

    ContextInternal ctx = vertx.getOrCreateContext();
    NetClient client = vertx.createNetClient(options, new CloseFuture());
    PromiseInternal<MqttConnAckMessage> connectPromise = ctx.promise();
    PromiseInternal<Void> disconnectPromise = ctx.promise();

    synchronized (this) {
      if (this.status != Status.CLOSED) {
        return ctx.failedFuture(new IllegalStateException("Client " + this.status.name().toLowerCase()));
      }
      this.status = Status.CONNECTING;
      this.ctx = ctx;
      this.connectPromise = connectPromise;
      this.disconnectPromise = disconnectPromise;
      this.client = client;
    }

    ctx.runOnContext(v -> {
      log.debug(String.format("Trying to connect with %s:%d", host, port));

      client.connect(port, host, serverName, done -> {

        // the TCP connection fails
        if (done.failed()) {
          log.error(String.format("Can't connect to %s:%d", host, port), done.cause());
          synchronized (this) {
            this.connectPromise = null;
            this.disconnectPromise = null;
            this.ctx = null;
            this.client = null;
          }
          client.close();
          connectPromise.fail(done.cause());
          disconnectPromise.complete();
        } else {
          log.info(String.format("Connection with %s:%d established successfully", host, port));

          boolean closing;
          synchronized (MqttClientImpl.this) {
            if (closing = (status == Status.CLOSING)) {
              this.status = Status.CLOSED;
              this.client = null;
              this.connectPromise = null;
              this.disconnectPromise = null;
            }
          }

          NetSocketInternal soi = (NetSocketInternal) done.result();

          if (closing) {
            soi.close();
            connectPromise.fail("Disconnected");
            disconnectPromise.complete();
            return;
          }

          if (options.isAutoGeneratedClientId() && (options.getClientId() == null || options.getClientId().isEmpty())) {
            options.setClientId(generateRandomClientId());
          }

          initChannel(soi);
          synchronized (MqttClientImpl.this) {
            this.connection = soi;
          }

          soi.messageHandler(msg -> this.handleMessage(soi.channelHandlerContext(), msg));
          soi.closeHandler(v2 -> {
            client.close();
            synchronized (MqttClientImpl.this) {
              this.connection = null;
              this.status = Status.CLOSED;
              this.connectPromise = null;
              this.disconnectPromise = null;
            }
            connectPromise.fail("Closed");
            disconnectPromise.complete();
          });

          // an exception at connection level
          soi.exceptionHandler(this::handleException);

          MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.CONNECT,
            false,
            AT_MOST_ONCE,
            false,
            0);

          MqttConnectVariableHeader variableHeader = new MqttConnectVariableHeader(
            PROTOCOL_NAME,
            PROTOCOL_VERSION,
            options.hasUsername(),
            options.hasPassword(),
            options.isWillRetain(),
            options.getWillQoS(),
            options.isWillFlag(),
            options.isCleanSession(),
            options.getKeepAliveInterval()
          );

          MqttConnectPayload payload = new MqttConnectPayload(
            options.getClientId() == null ? "" : options.getClientId(),
            options.getWillTopic(),
            options.getWillMessage() != null ? options.getWillMessage().getBytes(StandardCharsets.UTF_8) : null,
            options.hasUsername() ? options.getUsername() : null,
            options.hasPassword() ? options.getPassword().getBytes() : null
          );

          io.netty.handler.codec.mqtt.MqttMessage connect = MqttMessageFactory.newMessage(fixedHeader, variableHeader, payload);

          this.write(connect);
        }

      });
    });

    return connectPromise.future();
  }

  /**
   * See {@link MqttClient#disconnect()} for more details
   */
  @Override
  public Future<Void> disconnect() {

    NetSocketInternal connection;
    Status status;
    Future<Void> fut;
    synchronized (this) {
      status = this.status;
      fut = this.disconnectPromise.future();
      switch (this.status) {
        case CLOSED:
          return vertx.getOrCreateContext().succeededFuture();
        case CONNECTED:
          this.status = Status.CLOSING;
          connection = this.connection;
          break;
        case CONNECTING:
          this.status = Status.CLOSING;
          connection = this.connection;
          break;
        case CLOSING:
          connection = null;
          break;
        default:
          throw new AssertionError();
      }
    }

    if (connection != null) {
      if (status == Status.CONNECTED) {
        MqttFixedHeader fixedHeader = new MqttFixedHeader(
          MqttMessageType.DISCONNECT,
          false,
          AT_MOST_ONCE,
          false,
          0
        );
        io.netty.handler.codec.mqtt.MqttMessage disconnect = MqttMessageFactory.newMessage(fixedHeader, null, null);
        connection.writeMessage(disconnect);
      }
      connection.close();
    }

    return fut;
  }

  /**
   * See {@link MqttClient#disconnect(Handler)} for more details
   */
  @Override
  public MqttClient disconnect(Handler<AsyncResult<Void>> disconnectHandler) {

    Future<Void> fut = disconnect();
    if (disconnectHandler != null) {
      fut.onComplete(disconnectHandler);
    }
    return this;
  }

  /**
   * See {@link MqttClient#publish(String, Buffer, MqttQoS, boolean, boolean)} for more details
   */
  @Override
  public Future<Integer> publish(String topic, Buffer payload, MqttQoS qosLevel, boolean isDup, boolean isRetain) {

    if (MqttQoS.FAILURE == qosLevel) {
        throw new IllegalArgumentException("QoS level must be one of AT_MOST_ONCE, AT_LEAST_ONCE or EXACTLY_ONCE");
    }

    io.netty.handler.codec.mqtt.MqttMessage publish;
    MqttPublishVariableHeader variableHeader;
    synchronized (this) {
      if (countInflightQueue >= options.getMaxInflightQueue()) {
        String msg = String.format("Attempt to exceed the limit of %d inflight messages", options.getMaxInflightQueue());
        log.error(msg);
        MqttException exception = new MqttException(MqttException.MQTT_INFLIGHT_QUEUE_FULL, msg);
        return ctx.failedFuture(exception);
      }

      if (!isValidTopicName(topic)) {
        String msg = String.format("Invalid Topic Name - %s. It mustn't contains wildcards: # and +. Also it can't contains U+0000(NULL) chars", topic);
        log.error(msg);
        MqttException exception = new MqttException(MqttException.MQTT_INVALID_TOPIC_NAME, msg);
        return ctx.failedFuture(exception);
      }

      MqttFixedHeader fixedHeader = new MqttFixedHeader(
        MqttMessageType.PUBLISH,
        isDup,
        qosLevel,
        isRetain,
        0
      );
      ByteBuf buf = Unpooled.copiedBuffer(payload.getBytes());
      variableHeader = new MqttPublishVariableHeader(topic, nextMessageId());
      publish = MqttMessageFactory.newMessage(fixedHeader, variableHeader, buf);
      switch (qosLevel) {
        case AT_LEAST_ONCE:
          qos1outbound.put(variableHeader.packetId(), new ExpiringPacket(this::handlePubackTimeout, variableHeader.packetId()));
          countInflightQueue++;
          break;
        case EXACTLY_ONCE:
          qos2outbound.put(variableHeader.packetId(), new ExpiringPacket(this::handlePubrecTimeout, variableHeader.packetId()));
          countInflightQueue++;
          break;
        default:
          // nothing to do for AT_MOST_ONCE
          break;
      }
    }

    return this.write(publish).map(variableHeader.packetId());
  }

  /**
   * See {@link MqttClient#publish(String, Buffer, MqttQoS, boolean, boolean, Handler)} for more details
   */
  @Override
  public MqttClient publish(String topic, Buffer payload, MqttQoS qosLevel, boolean isDup, boolean isRetain, Handler<AsyncResult<Integer>> publishSentHandler) {

    Future<Integer> fut = publish(topic, payload, qosLevel, isDup, isRetain);
    if (publishSentHandler != null) {
      fut.onComplete(publishSentHandler);
    }
    return this;
  }

  /**
   * See {@link MqttClient#publishCompletionHandler(Handler)} for more details
   */
  @Override
  public MqttClient publishCompletionHandler(Handler<Integer> publishCompletionHandler) {

    this.publishCompletionHandler = publishCompletionHandler;
    return this;
  }

  private synchronized Handler<Integer> publishCompletionHandler() {
    return this.publishCompletionHandler;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public MqttClient publishCompletionExpirationHandler(Handler<Integer> publishCompletionExpirationHandler) {

    this.publishCompletionExpirationHandler = publishCompletionExpirationHandler;
    return this;
  }

  private synchronized Handler<Integer> publishCompletionExpirationHandler() {
    return this.publishCompletionExpirationHandler;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public MqttClient publishCompletionUnknownPacketIdHandler(Handler<Integer> publishCompletionPhantomHandler) {

    this.publishCompletionPhantomHandler = publishCompletionPhantomHandler;
    return this;
  }

  private synchronized Handler<Integer> publishCompletionUnknownPacketIdHandler() {
    return this.publishCompletionPhantomHandler;
  }

  /**
   * See {@link MqttClient#publishHandler(Handler)} for more details
   */
  @Override
  public MqttClient publishHandler(Handler<MqttPublishMessage> publishHandler) {

    this.publishHandler = publishHandler;
    return this;
  }

  private synchronized Handler<MqttPublishMessage> publishHandler() {
    return this.publishHandler;
  }

  /**
   * See {@link MqttClient#subscribeCompletionHandler(Handler)} for more details
   */
  @Override
  public MqttClient subscribeCompletionHandler(Handler<MqttSubAckMessage> subscribeCompletionHandler) {

    this.subscribeCompletionHandler = subscribeCompletionHandler;
    return this;
  }

  private synchronized Handler<MqttSubAckMessage> subscribeCompletionHandler() {
    return this.subscribeCompletionHandler;
  }

  /**
   * See {@link MqttClient#subscribe(String, int)} for more details
   */
  @Override
  public Future<Integer> subscribe(String topic, int qos) {
    return subscribe(Collections.singletonMap(topic, qos));
  }

  /**
   * See {@link MqttClient#subscribe(String, int, Handler)} for more details
   */
  @Override
  public MqttClient subscribe(String topic, int qos, Handler<AsyncResult<Integer>> subscribeSentHandler) {
    return subscribe(Collections.singletonMap(topic, qos), subscribeSentHandler);
  }

  /**
   * See {@link MqttClient#subscribe(Map)} for more details
   */
  @Override
  public Future<Integer> subscribe(Map<String, Integer> topics) {

    Map<String, Integer> invalidTopics = topics.entrySet()
      .stream()
      .filter(e -> !isValidTopicFilter(e.getKey()))
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    if (invalidTopics.size() > 0) {
      String msg = String.format("Invalid Topic Filters: %s", invalidTopics);
      log.error(msg);
      MqttException exception = new MqttException(MqttException.MQTT_INVALID_TOPIC_FILTER, msg);
      return ctx.failedFuture(exception);
    }

    MqttFixedHeader fixedHeader = new MqttFixedHeader(
      MqttMessageType.SUBSCRIBE,
      false,
      AT_LEAST_ONCE,
      false,
      0);

    MqttMessageIdVariableHeader variableHeader = MqttMessageIdVariableHeader.from(nextMessageId());
    List<MqttTopicSubscription> subscriptions = topics.entrySet()
      .stream()
      .map(e -> new MqttTopicSubscription(e.getKey(), valueOf(e.getValue())))
      .collect(Collectors.toList());

    MqttSubscribePayload payload = new MqttSubscribePayload(subscriptions);

    io.netty.handler.codec.mqtt.MqttMessage subscribe = MqttMessageFactory.newMessage(fixedHeader, variableHeader, payload);

    return this.write(subscribe).map(variableHeader.messageId());
  }

  /**
   * See {@link MqttClient#subscribe(Map, Handler)} for more details
   */
  @Override
  public MqttClient subscribe(Map<String, Integer> topics, Handler<AsyncResult<Integer>> subscribeSentHandler) {

    Future<Integer> fut = subscribe(topics);
    if (subscribeSentHandler != null) {
      fut.onComplete(subscribeSentHandler);
    }
    return this;
  }

  /**
   * See {@link MqttClient#unsubscribeCompletionHandler(Handler)} for more details
   */
  @Override
  public MqttClient unsubscribeCompletionHandler(Handler<Integer> unsubscribeCompletionHandler) {

    this.unsubscribeCompletionHandler = unsubscribeCompletionHandler;
    return this;
  }

  private synchronized Handler<Integer> unsubscribeCompletionHandler() {

    return this.unsubscribeCompletionHandler;
  }

  /**
   * See {@link MqttClient#unsubscribe(String, Handler)} )} for more details
   */
  @Override
  public MqttClient unsubscribe(String topic, Handler<AsyncResult<Integer>> unsubscribeSentHandler) {

    Future<Integer> fut = unsubscribe(topic);
    if (unsubscribeSentHandler != null) {
      fut.onComplete(unsubscribeSentHandler);
    }
    return this;
  }

  /**
   * See {@link MqttClient#unsubscribe(String)} )} for more details
   */
  @Override
  public Future<Integer> unsubscribe(String topic) {

    MqttFixedHeader fixedHeader = new MqttFixedHeader(
      MqttMessageType.UNSUBSCRIBE,
      false,
      AT_LEAST_ONCE,
      false,
      0);

    MqttMessageIdVariableHeader variableHeader = MqttMessageIdVariableHeader.from(nextMessageId());

    MqttUnsubscribePayload payload = new MqttUnsubscribePayload(Stream.of(topic).collect(Collectors.toList()));

    io.netty.handler.codec.mqtt.MqttMessage unsubscribe = MqttMessageFactory.newMessage(fixedHeader, variableHeader, payload);

    this.write(unsubscribe);

    return ctx.succeededFuture(variableHeader.messageId());
  }

  /**
   * See {@link MqttClient#pingResponseHandler(Handler)} for more details
   */
  @Override
  public synchronized MqttClient pingResponseHandler(Handler<Void> pingResponseHandler) {
    this.pingrespHandler = pingResponseHandler;
    return this;
  }

  private synchronized Handler<Void> pingResponseHandler() {
    return this.pingrespHandler;
  }

  /**
   * See {@link MqttClient#exceptionHandler(Handler)} for more details
   */
  @Override
  public synchronized MqttClient exceptionHandler(Handler<Throwable> handler) {
    exceptionHandler = handler;
    return this;
  }

  private synchronized Handler<Throwable> exceptionHandler() {
    return this.exceptionHandler;
  }

  /**
   * See {@link MqttClient#closeHandler(Handler)} for more details
   */
  @Override
  public synchronized MqttClient closeHandler(Handler<Void> closeHandler) {
    this.closeHandler = closeHandler;
    return this;
  }

  private synchronized Handler<Void> closeHandler() {
    return this.closeHandler;
  }

  /**
   * See {@link MqttClient#ping()} for more details
   */
  @Override
  public MqttClient ping() {

    MqttFixedHeader fixedHeader =
      new MqttFixedHeader(MqttMessageType.PINGREQ, false, MqttQoS.AT_MOST_ONCE, false, 0);

    io.netty.handler.codec.mqtt.MqttMessage pingreq = MqttMessageFactory.newMessage(fixedHeader, null, null);

    this.write(pingreq);

    return this;
  }

  @Override
  public synchronized String clientId() {
    return this.options.getClientId();
  }

  @Override
  public synchronized boolean isConnected() {
    return this.status == Status.CONNECTED;
  }

  /**
   * Sends PUBACK packet to server
   *
   * @param publishMessageId identifier of the PUBLISH message to acknowledge
   */
  private void publishAcknowledge(int publishMessageId) {

    MqttFixedHeader fixedHeader =
      new MqttFixedHeader(MqttMessageType.PUBACK, false, AT_MOST_ONCE, false, 0);

    MqttMessageIdVariableHeader variableHeader =
      MqttMessageIdVariableHeader.from(publishMessageId);

    io.netty.handler.codec.mqtt.MqttMessage puback = MqttMessageFactory.newMessage(fixedHeader, variableHeader, null);

    this.write(puback);
  }

  /**
   * Sends PUBREC packet to server
   *
   * @param publishMessage a PUBLISH message to acknowledge
   */
  private void publishReceived(MqttPublishMessage publishMessage) {

    MqttFixedHeader fixedHeader =
      new MqttFixedHeader(MqttMessageType.PUBREC, false, AT_MOST_ONCE, false, 0);

    MqttMessageIdVariableHeader variableHeader =
      MqttMessageIdVariableHeader.from(publishMessage.messageId());

    io.netty.handler.codec.mqtt.MqttMessage pubrec = MqttMessageFactory.newMessage(fixedHeader, variableHeader, null);

    synchronized (this) {
      qos2inbound.put(publishMessage.messageId(), publishMessage);
    }
    this.write(pubrec);
  }

  /**
   * Sends PUBCOMP packet to server
   *
   * @param publishMessageId identifier of the PUBLISH message to acknowledge
   */
  private void publishComplete(int publishMessageId) {

    MqttFixedHeader fixedHeader =
      new MqttFixedHeader(MqttMessageType.PUBCOMP, false, AT_MOST_ONCE, false, 0);

    MqttMessageIdVariableHeader variableHeader =
      MqttMessageIdVariableHeader.from(publishMessageId);

    io.netty.handler.codec.mqtt.MqttMessage pubcomp = MqttMessageFactory.newMessage(fixedHeader, variableHeader, null);

    this.write(pubcomp);
  }

  /**
   * Sends the PUBREL message to server
   *
   * @param publishMessageId  identifier of the PUBLISH message to acknowledge
   */
  private void publishRelease(int publishMessageId) {

    MqttFixedHeader fixedHeader =
      new MqttFixedHeader(MqttMessageType.PUBREL, false, MqttQoS.AT_LEAST_ONCE, false, 0);

    MqttMessageIdVariableHeader variableHeader =
      MqttMessageIdVariableHeader.from(publishMessageId);

    io.netty.handler.codec.mqtt.MqttMessage pubrel = MqttMessageFactory.newMessage(fixedHeader, variableHeader, null);

    synchronized (this) {
      qos2outbound.put(publishMessageId, new ExpiringPacket(this::handlePubcompTimeout, publishMessageId));
    }
    this.write(pubrel);
  }

  private void initChannel(NetSocketInternal sock) {

    ChannelPipeline pipeline = sock.channelHandlerContext().pipeline();

    // add into pipeline netty's (en/de)coder
    pipeline.addBefore("handler", "mqttEncoder", MqttEncoder.INSTANCE);

    if (this.options.getMaxMessageSize() > 0) {
      pipeline.addBefore("handler", "mqttDecoder", new MqttDecoder(this.options.getMaxMessageSize()));
    } else {
      // max message size not set, so the default from Netty MQTT codec is used
      pipeline.addBefore("handler", "mqttDecoder", new MqttDecoder());
    }

    if (this.options.isAutoKeepAlive() &&
      this.options.getKeepAliveInterval() != 0) {

      int keepAliveInterval = this.options.getKeepAliveInterval();

      // handler for sending PINGREQ (keepAlive) if reader- or writer-channel become idle
      pipeline.addBefore("handler", "idle",
        new IdleStateHandler(0, 0, keepAliveInterval) {
          @Override
          protected void channelIdle(ChannelHandlerContext ctx, IdleStateEvent evt) {
            if (evt.state() == IdleState.ALL_IDLE) {
              // verify that server is still connected (e.g. when using QoS-0)
              ping();
            }
          }
        });


      long keepAliveTimeout = (keepAliveInterval * 1000) * 3 / 2;
      // handler for ping-response timeout. connection will be closed if broker READER_IDLE extends timeout
      pipeline.addBefore("handler", "idleTimeout", new IdleStateHandler(keepAliveTimeout, 0L, 0L, TimeUnit.MILLISECONDS) {
        @Override
        protected void channelIdle(ChannelHandlerContext ctx, IdleStateEvent evt) {
          if (evt.state() == IdleState.READER_IDLE) {
            ctx.close();
          }
        }
      });
    }
  }

  /**
   * Update and return the next message identifier
   *
   * @return message identifier
   */
  private synchronized int nextMessageId() {

    // if 0 or MAX_MESSAGE_ID, it becomes 1 (first valid messageId)
    this.messageIdCounter = ((this.messageIdCounter % MAX_MESSAGE_ID) != 0) ? this.messageIdCounter + 1 : 1;
    return this.messageIdCounter;
  }

  private synchronized NetSocketInternal connection() {
    return connection;
  }

  private Future<Void> write(io.netty.handler.codec.mqtt.MqttMessage mqttMessage) {
    log.debug(String.format("Sending packet %s", mqttMessage));
    return this.connection().writeMessage(mqttMessage);
  }

  /**
   * Used for calling the close handler when the remote MQTT server closes the connection
   */
  private void handleClosed() {
    Promise<MqttConnAckMessage> connectPromise;
    Promise<Void> disconnectPromise;
    NetClient client;
    synchronized (this) {
      client = this.client;
      connectPromise = this.connectPromise;
      disconnectPromise = this.disconnectPromise;
      this.disconnectPromise = null;
      this.status = Status.CLOSED;
      this.connection = null;
      this.ctx = null;
      this.client = null;
    }

    Handler<Void> handler = closeHandler();
    if (handler != null) {
      handler.handle(null);
    }
    disconnectPromise.complete();
    if (connectPromise != null) {
      connectPromise.fail("Closed");
    }
    client.close();
  }

  /**
   * Handle the MQTT message received from the remote MQTT server
   *
   * @param msg Incoming Packet
   */
  private void handleMessage(ChannelHandlerContext chctx, Object msg) {

    // handling directly native Netty MQTT messages, some of them are translated
    // to the related Vert.x ones for polyglotization
    if (msg instanceof io.netty.handler.codec.mqtt.MqttMessage) {

      io.netty.handler.codec.mqtt.MqttMessage mqttMessage = (io.netty.handler.codec.mqtt.MqttMessage) msg;

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
            publish.variableHeader().packetId(),
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

          chctx.pipeline().fireExceptionCaught(new Exception("Wrong message type " + msg.getClass().getName()));
          break;
      }

    } else {

      chctx.pipeline().fireExceptionCaught(new Exception("Wrong message type"));
    }
  }

  /**
   * Used for calling the pingresp handler when the server replies to the ping
   */
  private void handlePingresp() {

    Handler<Void> handler = pingResponseHandler();
    if (handler != null) {
      handler.handle(null);
    }
  }

  /**
   * Used for calling the unsuback handler when the server acks an unsubscribe
   *
   * @param unsubackMessageId identifier of the subscribe acknowledged by the server
   */
  private void handleUnsuback(int unsubackMessageId) {

    Handler<Integer> handler = unsubscribeCompletionHandler();
    if (handler != null) {
      handler.handle(unsubackMessageId);
    }
  }

  /**
   * Used for calling the puback handler when the server acknowledge a QoS 1 message with puback
   *
   * @param pubackMessageId identifier of the message acknowledged by the server
   */
  private void handlePuback(int pubackMessageId) {

    synchronized (this) {

      ExpiringPacket removedPacket = qos1outbound.remove(pubackMessageId);

      if (removedPacket == null) {
        log.debug("Received PUBACK packet without having related PUBLISH packet in storage");
        // PUBACK has been received after timer has already fired
        Handler<Integer> handler = publishCompletionUnknownPacketIdHandler();
        if (handler != null) {
          handler.handle(pubackMessageId);
        }
        return;
      }
      removedPacket.cancelTimer();
      countInflightQueue--;
    }
    Handler<Integer> handler = publishCompletionHandler();
    if (handler != null) {
      handler.handle(pubackMessageId);
    }
  }

  private void handlePubackTimeout(int packetId) {
      ExpiringPacket expiredMessage;
      synchronized (this) {
        expiredMessage = qos1outbound.remove(packetId);

        if (expiredMessage == null) {
          // the message has already been ACKed
          log.debug("PUBLISH expiration timer fired but QoS 1 message has already been PUBACKed by server");
          return;
        }
      }
      countInflightQueue--;
      Handler<Integer> handler = publishCompletionExpirationHandler();
      if (handler != null) {
        handler.handle(expiredMessage.packetId);
      }
    }

  /**
   * Used for calling the pubcomp handler when the server client acknowledge a QoS 2 message with pubcomp
   *
   * @param pubcompMessageId identifier of the message acknowledged by the server
   */
  private void handlePubcomp(int pubcompMessageId) {

    synchronized (this) {
      ExpiringPacket removedPacket = qos2outbound.remove(pubcompMessageId);

      if (removedPacket == null) {
        log.debug("Received PUBCOMP packet without having related PUBREL packet in storage");
        Handler<Integer> handler = publishCompletionUnknownPacketIdHandler();
        if (handler != null) {
          handler.handle(pubcompMessageId);
        }
        return;
      }
      removedPacket.cancelTimer();
      countInflightQueue--;
    }
    Handler<Integer> handler = publishCompletionHandler();
    if (handler != null) {
      handler.handle(pubcompMessageId);
    }
  }

  private void handlePubcompTimeout(int packetId) {
      ExpiringPacket expiredMessage;
      synchronized (this) {
        expiredMessage = qos2outbound.remove(packetId);

        if (expiredMessage == null) {
          log.debug("PUBCOMP expiration timer fired but QoS 2 message has already been PUBCOMPed by server");
          return;
        }
      }
      countInflightQueue--;
      Handler<Integer> handler = publishCompletionExpirationHandler();
      if (handler != null) {
        handler.handle(expiredMessage.packetId);
      }
    }

  /**
   * Used for sending the pubrel when a pubrec is received from the server
   *
   * @param pubrecMessageId identifier of the message acknowledged by server
   */
  private void handlePubrec(int pubrecMessageId) {

    synchronized (this) {
      ExpiringPacket removedPacket = qos2outbound.remove(pubrecMessageId);

      if (removedPacket == null) {
        log.debug("Received PUBREC packet without having related PUBLISH packet in storage");
        Handler<Integer> handler = publishCompletionUnknownPacketIdHandler();
        if (handler != null) {
          handler.handle(pubrecMessageId);
        }
        return;
      }
      removedPacket.cancelTimer();
    }
    this.publishRelease(pubrecMessageId);
  }

  private void handlePubrecTimeout(int packetId) {
      ExpiringPacket expiredMessage;
      synchronized (this) {
        expiredMessage = qos2outbound.remove(packetId);

        if (expiredMessage == null) {
          log.debug("PUBREC expiration timer fired but QoS 2 message has already been PUBRECed by server");
          return;
        }
      }
      countInflightQueue--;
      Handler<Integer> handler = publishCompletionExpirationHandler();
      if (handler != null) {
        handler.handle(expiredMessage.packetId);
      }
    }

  /**
   * Used for calling the suback handler when the server acknowledges subscribe to topics
   *
   * @param msg message with suback information
   */
  private void handleSuback(MqttSubAckMessage msg) {

    Handler<MqttSubAckMessage> handler = subscribeCompletionHandler();
    if (handler != null) {
      handler.handle(msg);
    }
  }

  /**
   * Used for calling the publish handler when the server publishes a message
   *
   * @param msg published message
   */
  private void handlePublish(MqttPublishMessage msg) {

    Handler<MqttPublishMessage> handler;
    switch (msg.qosLevel()) {

      case AT_MOST_ONCE:
        handler = this.publishHandler();
        if (handler != null) {
          handler.handle(msg);
        }
        break;

      case AT_LEAST_ONCE:
        this.publishAcknowledge(msg.messageId());
        handler = this.publishHandler();
        if (handler != null) {
          handler.handle(msg);
        }
        break;

      case EXACTLY_ONCE:
        this.publishReceived(msg);
        // we will handle the PUBLISH when a PUBREL comes
        break;
    }
  }

  /**
   * Used for calling the pubrel handler when the server acknowledge a QoS 2 message with pubrel
   *
   * @param pubrelMessageId identifier of the message acknowledged by the server
   */
  private void handlePubrel(int pubrelMessageId) {
    MqttMessage message;
    synchronized (this) {
      message = qos2inbound.remove(pubrelMessageId);

      if (message == null) {
        log.warn("Received PUBREL packet without having related PUBREC packet in storage");
        return;
      }
      this.publishComplete(pubrelMessageId);
    }
    Handler<MqttPublishMessage> handler = this.publishHandler();
    if (handler != null) {
      handler.handle((MqttPublishMessage) message);
    }
  }

  /**
   * Used for calling the connect handler when the server replies to the request
   *
   * @param msg  connection response message
   */
  private void handleConnack(MqttConnAckMessage msg) {

    Status status = msg.code() == MqttConnectReturnCode.CONNECTION_ACCEPTED ? Status.CONNECTED : Status.CLOSING;


    if (msg.code() == MqttConnectReturnCode.CONNECTION_ACCEPTED) {
      NetSocketInternal connection;
      Promise<MqttConnAckMessage> connectPromise;
      synchronized (this) {
        connection = this.connection;
        connectPromise = this.connectPromise;
        this.connectPromise = null;
        this.status = Status.CONNECTED;
      }
      connection.closeHandler(v -> handleClosed());
      connectPromise.complete(msg);
    } else {
      Promise<MqttConnAckMessage> connectPromise;
      Promise<Void> disconnectPromise;
      NetSocketInternal connection;
      NetClient client;
      synchronized (this) {
        connectPromise = this.connectPromise;
        disconnectPromise = this.disconnectPromise;
        connection = this.connection;
        client = this.client;
        this.connectPromise = null;
        this.disconnectPromise = null;
        this.status = Status.CLOSED;
        this.connection = null;
        this.client = null;
      }
      connection.closeHandler(null);
      MqttConnectionException exception = new MqttConnectionException(msg.code());
      log.error(String.format("Connection refused by the server - code: %s", msg.code()));
      connectPromise.fail(exception);
      disconnectPromise.complete();
      client.close();
    }
  }

  /**
   * Used for calling the exception handler when an error at connection level
   *
   * @param t exception raised
   */
  private void handleException(Throwable t) {

    Handler<Throwable> handler = exceptionHandler();
    if (handler != null) {
      handler.handle(t);
    }
  }

  /**
   * @return Randomly-generated ClientId
   */
  private String generateRandomClientId() {
    return UUID.randomUUID().toString();
  }

  /**
   * Check either given Topic Name valid of not
   *
   * @param topicName given Topic Name
   * @return true - valid, otherwise - false
   */
  private boolean isValidTopicName(String topicName) {
    if(!isValidStringSizeInUTF8(topicName)){
      return false;
    }

    Matcher matcher = validTopicNamePattern.matcher(topicName);
    return matcher.find();
  }

  /**
   * Check either given Topic Filter valid of not
   *
   * @param topicFilter given Topic Filter
   * @return true - valid, otherwise - false
   */
  private boolean isValidTopicFilter(String topicFilter) {
    if(!isValidStringSizeInUTF8(topicFilter)){
      return false;
    }

    Matcher matcher = validTopicFilterPattern.matcher(topicFilter);
    return matcher.find();
  }

  /**
   * Check either given string has size more then 65535 bytes in UTF-8 Encoding
   *
   * @param string given string
   * @return true - size is lower or equal than 65535, otherwise - false
   */
  private boolean isValidStringSizeInUTF8(String string) {
    try {
      int length = string.getBytes("UTF-8").length;
      return length >= MIN_TOPIC_LEN && length <= MAX_TOPIC_LEN;
    } catch (UnsupportedEncodingException e) {
      log.error("UTF-8 charset is not supported", e);
    }

    return false;
  }

  /**
   * A wrapper around a packet ID for which the client will wait a limited time
   * for the server's ACK to arrive.
   */
  private class ExpiringPacket {
    private final int packetId;
    private final long timerId;

    /**
     * Creates a new expiring packet.
     *
     * @param timeoutHandler The handler to invoke once the client stops waiting for the server's ACK.
     * @param packetId The packet ID.
     */
    ExpiringPacket(Handler<Integer> timeoutHandler, final int packetId) {
      this.packetId = packetId;
      if (options.getAckTimeout() > -1) {
          this.timerId = vertx.setTimer(options.getAckTimeout() * 1000L, tid -> timeoutHandler.handle(packetId));
      } else {
          // default MQTT client behavior,
          // don't start a timer for expiring the publish
          this.timerId = -1;
      }
    }

    /**
     * Cancels the timer created for expiring the ACK.
     * <p>
     * This method should be invoked once the server's ACK for the packet ID has arrived
     * in order to prevent the client from timing out while waiting for an ACK.
     *
     * @return {@code true} if the timer has been canceled.
     */
    boolean cancelTimer() {
        return vertx.cancelTimer(timerId);
    }
  }
}
