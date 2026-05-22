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
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.FixedRecvByteBufAllocator;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.mqtt.*;
import io.netty.handler.codec.mqtt.MqttProperties.BinaryProperty;
import io.netty.handler.codec.mqtt.MqttProperties.IntegerProperty;
import io.netty.handler.codec.mqtt.MqttProperties.StringPair;
import io.netty.handler.codec.mqtt.MqttProperties.UserProperties;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.CloseFuture;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.impl.future.PromiseInternal;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.impl.NetClientBuilder;
import io.vertx.core.net.impl.NetSocketInternal;
import io.vertx.core.net.impl.VertxHandler;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;
import io.vertx.mqtt.MqttConnectionException;
import io.vertx.mqtt.MqttException;
import io.vertx.mqtt.messages.MqttConnAckMessage;
import io.vertx.mqtt.messages.MqttMessage;
import io.vertx.mqtt.messages.MqttPublishMessage;
import io.vertx.mqtt.messages.MqttSubAckMessage;
import io.vertx.mqtt.messages.MqttAuthenticationExchangeMessage;
import io.vertx.mqtt.messages.codes.MqttAuthenticateReasonCode;
import io.vertx.mqtt.messages.codes.MqttDisconnectReasonCode;
import io.vertx.mqtt.messages.codes.MqttPubAckReasonCode;
import io.vertx.mqtt.messages.codes.MqttPubRecReasonCode;
import io.vertx.mqtt.messages.codes.MqttPubRelReasonCode;
import io.vertx.mqtt.messages.codes.MqttPubCompReasonCode;
import io.vertx.mqtt.messages.MqttDisconnectMessage;
import io.vertx.mqtt.messages.MqttPubAckMessage;
import io.vertx.mqtt.messages.MqttPubRecMessage;
import io.vertx.mqtt.messages.MqttPubCompMessage;
import io.vertx.mqtt.messages.MqttUnsubAckMessage;
import io.vertx.mqtt.messages.impl.MqttPublishMessageImpl;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

  private final VertxInternal vertx;
  private final MqttClientOptions options;
  private NetSocketInternal connection;
  private ChannelConfig connOption;
  private ContextInternal ctx;

  // handler to call when a publish is complete
  private Handler<Integer> publishCompletionHandler;
  // handler to call when a publish has expired
  private Handler<Integer> publishCompletionExpirationHandler;
  // handler to call when a PUBACK is received for an unknown packetId
  private Handler<Integer> publishCompletionPhantomHandler;
  // handler to call when a unsubscribe request is completed
  private Handler<Integer> unsubscribeCompletionHandler;
  // handler to call when a UNSUBACK is received
  private Handler<MqttUnsubAckMessage> unsubscribeCompletionMessageHandler;
  // handler to call when a publish message comes in
  private Handler<MqttPublishMessage> publishHandler;
  // handler to call when a subscribe request is completed
  private Handler<MqttSubAckMessage> subscribeCompletionHandler;
  // handler to call when an auth message comes in
  private Handler<MqttAuthenticationExchangeMessage> authenticationExchangeHandler;
  // MQTT 5.0 typed handlers for incoming PUBACK/PUBREC/PUBCOMP with reason codes
  private Handler<MqttPubAckMessage> publishAckMessageHandler;
  private Handler<MqttPubRecMessage> publishRecMessageHandler;
  private Handler<MqttPubCompMessage> publishCompMessageHandler;

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
  // handler to call when a server-initiated DISCONNECT is received (fires before closeHandler)
  private Handler<MqttDisconnectMessage> disconnectMessageHandler;
  // pending server-initiated DISCONNECT message (built in handleMessage, fired in handleClosed)
  private MqttDisconnectMessage pendingDisconnectMessage = null;

  // storage of PUBLISH QoS=1 messages which was not responded with PUBACK
  private HashMap<Integer, ExpiringPacket> qos1outbound = new HashMap<>();

  // storage of PUBLISH QoS=2 messages which was not responded with PUBREC
  // and PUBREL messages which was not responded with PUBCOMP
  private HashMap<Integer, ExpiringPacket> qos2outbound = new HashMap<>();

  // storage of PUBLISH messages which was responded with PUBREC
  private HashMap<Integer, MqttMessage> qos2inbound = new HashMap<>();

  // MQTT5 Topic alias: topic → alias number (client-to-server direction, outgoing PUBLISH)
  private HashMap<String, Integer> topicAlias = new HashMap<>();
  // Maximum number of topic aliases the server accepts (from CONNACK TOPIC_ALIAS_MAXIMUM, 0 = disabled)
  private int serverTopicAliasMaximum = 0;
  // MQTT5 Topic alias: alias → topic (server-to-client direction, incoming PUBLISH)
  private HashMap<Integer, String> serverTopicAlias = new HashMap<>();
  // Whether the server supports Subscription Identifiers (from CONNACK, default true per spec §3.2.2.3.12)
  private boolean serverSubscriptionIdentifierAvailable = true;
  // Whether the server supports Wildcard Subscriptions (from CONNACK, default true per spec §3.2.2.3.11)
  private boolean serverWildcardSubscriptionAvailable = true;
  // Whether the server supports Shared Subscriptions (from CONNACK, default true per spec §3.2.2.3.14)
  private boolean serverSharedSubscriptionAvailable = true;
  // Maximum concurrent QoS1/2 in-flight messages the server accepts (from CONNACK RECEIVE_MAXIMUM)
  private int serverReceiveMaximum = Integer.MAX_VALUE;
  // Maximum QoS the server accepts (from CONNACK MAXIMUM_QOS: 0, 1 or 2; default 2)
  private int serverMaxQos = 2;
  // Maximum packet size the server accepts (from CONNACK MAXIMUM_PACKET_SIZE; default = unlimited)
  private long serverMaximumPacketSize = Long.MAX_VALUE;

  // counter for the message identifier
  private int messageIdCounter;

  // Keep alive management
  private final long keepAliveTimeout;
  private Deque<Ping> pings = new ArrayDeque<>();

  // total number of unacknowledged packets
  private int countInflightQueue;

  private NetClient client;
  private Status status = Status.CLOSED;

  // SERVER_REFERENCE target set on receipt of a server-initiated DISCONNECT (redirect pending)
  private String pendingRedirect = null;

  /**
   * Constructor
   *
   * @param vertx Vert.x instance
   * @param options MQTT client options
   */
  public MqttClientImpl(Vertx vertx, MqttClientOptions options) {
    this.vertx = (VertxInternal) vertx;
    this.options = new MqttClientOptions(options);
    this.keepAliveTimeout = ((options.getKeepAliveInterval() * 1000) * 3) / 2;
  }

  int getInFlightMessagesCount() {
    synchronized (this) {
      return countInflightQueue;
    }
  }

  @Override
  public Future<MqttConnAckMessage> connect(int port, String host) {
    return this.connect(port, host, null, (Map<String, String>) null);
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
    return this.connect(port, host, serverName, (Map<String, String>) null);
  }

  /**
   * See {@link MqttClient#connect(int, String, String, Handler)} for more details
   */
  @Override
  public MqttClient connect(int port, String host, String serverName, Handler<AsyncResult<MqttConnAckMessage>> connectHandler) {

    Future<MqttConnAckMessage> fut = this.connect(port, host, serverName);
    if (connectHandler != null) {
      fut.onComplete(connectHandler);
    }
    return this;
  }

  @Override
  public Future<MqttConnAckMessage> connect(int port, String host, String serverName, Map<String,String> userProperties) {

    if (this.options.getVersion() != 5 && userProperties != null) {
      throw new IllegalArgumentException("userProperties is available only with MQTTv5");
    }
    
    ContextInternal ctx = vertx.getOrCreateContext();
    NetClient client = new NetClientBuilder(vertx, options).closeFuture(new CloseFuture()).build();
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
            this.status = Status.CLOSED;
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
            this.connOption = soi.channelHandlerContext().channel().config();
            if (options.getRecvByteBufAllocatorSize() != -1) {
              this.connOption.setRecvByteBufAllocator(new FixedRecvByteBufAllocator(options.getRecvByteBufAllocatorSize()));
            }
          }

          soi.messageHandler(msg -> this.handleMessage(soi.channelHandlerContext(), msg));
          soi.closeHandler(v2 -> {
            client.close();
            synchronized (MqttClientImpl.this) {
              this.connection = null;
              this.connOption = null;
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

          MqttProperties props = MqttProperties.NO_PROPERTIES;

          if (options.getVersion() == 5) {
            props = new MqttProperties();
            if (options.getSessionExpireInterval() != null)
              props.add(new IntegerProperty(MqttProperties.MqttPropertyType.SESSION_EXPIRY_INTERVAL.value(), (int) options.getSessionExpireInterval().longValue()));
            if (options.getReceiveMaximum() != null)
              props.add(new IntegerProperty(MqttProperties.MqttPropertyType.RECEIVE_MAXIMUM.value(), options.getReceiveMaximum()));
            if (options.getMaximumPacketSize() != null)
              props.add(new IntegerProperty(MqttProperties.MqttPropertyType.MAXIMUM_PACKET_SIZE.value(), (int) options.getMaximumPacketSize().longValue()));
            if (options.getTopicAliasMaximum() != null)
              props.add(new IntegerProperty(MqttProperties.MqttPropertyType.TOPIC_ALIAS_MAXIMUM.value(), options.getTopicAliasMaximum()));
            if (options.getRequestResponseInformation() != null)
              props.add(new IntegerProperty(MqttProperties.MqttPropertyType.REQUEST_RESPONSE_INFORMATION.value(), options.getRequestResponseInformation() ? 1 : 0 ));
            if (options.getRequestProblemInformation() != null)
              props.add(new IntegerProperty(MqttProperties.MqttPropertyType.REQUEST_PROBLEM_INFORMATION.value(), options.getRequestProblemInformation() ? 1 : 0 ));
            if (options.getAuthenticationMethod() != null)
              props.add(new MqttProperties.StringProperty(MqttProperties.MqttPropertyType.AUTHENTICATION_METHOD.value(), options.getAuthenticationMethod()));
            if (options.getAuthenticationData() != null)
              props.add(new BinaryProperty(MqttProperties.MqttPropertyType.AUTHENTICATION_DATA.value(), options.getAuthenticationData().getBytes()));
            if (userProperties != null && !userProperties.isEmpty()) {
              Collection<StringPair> values = userProperties.entrySet().stream().map(e -> new StringPair(e.getKey(), e.getValue())).collect(Collectors.toList());
              props.add(new UserProperties(values));
            }
          }

          io.vertx.mqtt.MqttClientWillOptions willOpts = options.getWillOptions();

          boolean willFlag = willOpts.getTopic() != null && willOpts.getMessageBytes() != null;
          
          MqttConnectVariableHeader variableHeader = new MqttConnectVariableHeader(
            PROTOCOL_NAME,
            options.getVersion(),
            options.hasUsername(),
            options.hasPassword(),
            willOpts.isRetain(),
            willOpts.getQos(),
            willFlag,
            options.isCleanSession(),
            options.getKeepAliveInterval(),
            props);

          MqttProperties willProperties = MqttProperties.NO_PROPERTIES;
          if (options.getVersion() == 5 && willFlag) {
            willProperties = new MqttProperties();
            if (willOpts.getWillDelayInterval() != null)
              willProperties.add(new IntegerProperty(MqttProperties.MqttPropertyType.WILL_DELAY_INTERVAL.value(), (int) willOpts.getWillDelayInterval().longValue()));
            if (willOpts.getPayloadFormatIndicator() != null)
              willProperties.add(new IntegerProperty(MqttProperties.MqttPropertyType.PAYLOAD_FORMAT_INDICATOR.value(), willOpts.getPayloadFormatIndicator()));
            if (willOpts.getContentType() != null)
              willProperties.add(new MqttProperties.StringProperty(MqttProperties.MqttPropertyType.CONTENT_TYPE.value(), willOpts.getContentType()));
            if (willOpts.getResponseTopic() != null)
              willProperties.add(new MqttProperties.StringProperty(MqttProperties.MqttPropertyType.RESPONSE_TOPIC.value(), willOpts.getResponseTopic()));
            if (willOpts.getCorrelationData() != null)
              willProperties.add(new BinaryProperty(MqttProperties.MqttPropertyType.CORRELATION_DATA.value(), willOpts.getCorrelationData().getBytes()));
            if (willOpts.getUserProperties() != null && !willOpts.getUserProperties().isEmpty()) {
              Collection<StringPair> pairs = willOpts.getUserProperties().entrySet().stream()
                  .map(e -> new StringPair(e.getKey(), e.getValue()))
                  .collect(Collectors.toList());
              willProperties.add(new UserProperties(pairs));
            }
          }

          MqttConnectPayload payload = new MqttConnectPayload(
            options.getClientId() == null ? "" : options.getClientId(),
            willProperties,
            willOpts.getTopic(),
            willOpts.getMessageBytes() != null ? willOpts.getMessageBytes().getBytes() : null,
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
    return disconnect(null, MqttProperties.NO_PROPERTIES);
  }

  @Override
  public Future<Void> disconnect(MqttDisconnectReasonCode code, MqttProperties properties) {

    NetSocketInternal connection;
    Status status;
    Future<Void> fut;
    synchronized (this) {
      status = this.status;
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
      fut = this.disconnectPromise.future();
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

        MqttReasonCodeAndPropertiesVariableHeader variableHeader = null;
        if (options.getVersion() == 5) {
          variableHeader = new MqttReasonCodeAndPropertiesVariableHeader(
              code == null ? MqttDisconnectReasonCode.NORMAL.value() : code.value(),
              properties == null ? MqttProperties.NO_PROPERTIES : properties);
        }
        io.netty.handler.codec.mqtt.MqttMessage  disconnect = MqttMessageFactory.newMessage(fixedHeader, variableHeader, null);
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
   * See {@link MqttClient#authenticationExchange(MqttAuthenticateReasonCode, MqttProperties)} for more details
   */
  @Override
  public Future<Void> authenticationExchange(MqttAuthenticateReasonCode reasonCode, MqttProperties properties) {

    if (options.getVersion() != 5) {
      return Future.failedFuture(new IllegalStateException("AUTH packet requires MQTT 5.0"));
    }

    synchronized (this) {
      if (this.status != Status.CONNECTED && this.status != Status.CONNECTING) {
        return Future.failedFuture(new IllegalStateException("Client not connected"));
      }
    }

    MqttFixedHeader fixedHeader = new MqttFixedHeader(
      MqttMessageType.AUTH, false, AT_MOST_ONCE, false, 0);
    MqttReasonCodeAndPropertiesVariableHeader variableHeader =
      new MqttReasonCodeAndPropertiesVariableHeader(
        reasonCode == null ? MqttAuthenticateReasonCode.SUCCESS.value() : reasonCode.value(),
        properties == null ? MqttProperties.NO_PROPERTIES : properties);
    io.netty.handler.codec.mqtt.MqttMessage auth =
      MqttMessageFactory.newMessage(fixedHeader, variableHeader, null);
    return this.write(auth);
  }

  /**
   * See {@link MqttClient#publish(String, Buffer, MqttQoS, boolean, boolean)} for more details
   */
  @Override
  public Future<Integer> publish(String topic, Buffer payload, MqttQoS qosLevel, boolean isDup, boolean isRetain) {
    return publish(topic, payload, qosLevel, isDup, isRetain, MqttProperties.NO_PROPERTIES);
  }

  @Override
  public Future<Integer> publish(String topic, Buffer payload, MqttQoS qosLevel, boolean isDup, boolean isRetain, MqttProperties properties) {

    if (MqttQoS.FAILURE == qosLevel) {
      throw new IllegalArgumentException("QoS level must be one of AT_MOST_ONCE, AT_LEAST_ONCE or EXACTLY_ONCE");
    }

    io.netty.handler.codec.mqtt.MqttMessage publish;
    MqttPublishVariableHeader variableHeader;
    synchronized (this) {
      // MQTT 5.0: reject if QoS exceeds server's advertised Maximum QoS
      if (options.getVersion() == 5 && qosLevel.value() > serverMaxQos) {
        String msg = String.format("Server does not support QoS %d (server maximum QoS is %d)", qosLevel.value(), serverMaxQos);
        log.error(msg);
        return ctx.failedFuture(new MqttException(MqttException.MQTT_QOS_UNSUPPORTED, msg));
      }

      // MQTT 5.0: reject if server's Receive Maximum is already reached
      if (options.getVersion() == 5 && qosLevel != AT_MOST_ONCE && countInflightQueue >= serverReceiveMaximum) {
        String msg = String.format("Server Receive Maximum of %d in-flight messages reached", serverReceiveMaximum);
        log.error(msg);
        return ctx.failedFuture(new MqttException(MqttException.MQTT_INFLIGHT_QUEUE_FULL, msg));
      }

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

      // MQTT 5.0 §3.2.2.3.6: client MUST NOT send a packet exceeding server's Maximum Packet Size
      if (options.getVersion() == 5 && serverMaximumPacketSize != Long.MAX_VALUE) {
        byte[] topicBytes = topic.getBytes(StandardCharsets.UTF_8);
        long estimatedSize = 5L // fixed header (1) + max remaining-length VBI (4)
          + 2 + topicBytes.length       // topic name: 2-byte length prefix + UTF-8 bytes
          + (qosLevel != AT_MOST_ONCE ? 2 : 0)  // packet identifier (QoS 1/2 only)
          + estimatePropertiesEncodedSize(properties) // MQTT5 properties
          + (payload != null ? payload.length() : 0);
        if (estimatedSize > serverMaximumPacketSize) {
          String msg = String.format(
            "Packet size estimate %d exceeds server Maximum Packet Size of %d",
            estimatedSize, serverMaximumPacketSize);
          log.error(msg);
          return ctx.failedFuture(new MqttException(MqttException.MQTT_PACKET_TOO_LARGE, msg));
        }
      }

      MqttFixedHeader fixedHeader = new MqttFixedHeader(
        MqttMessageType.PUBLISH,
        isDup,
        qosLevel,
        isRetain,
        0
      );
      ByteBuf buf = Unpooled.copiedBuffer(payload.getBytes());
      String wireTopicName = topic;
      MqttProperties effectiveProperties;
      if (options.getVersion() == 5) {
        effectiveProperties = new MqttProperties();
        // Copy caller-provided properties into the new instance
        if (properties != null && properties != MqttProperties.NO_PROPERTIES) {
          for (MqttProperties.MqttProperty<?> p : properties.listAll()) {
            effectiveProperties.add(p);
          }
        }
        // Automatic topic alias management (MQTT 5.0 spec §3.3.2.3.4)
        if (serverTopicAliasMaximum > 0) {
          Integer alias = topicAlias.get(topic);
          if (alias != null) {
            // Already mapped: send empty topic name + alias (bandwidth saving)
            wireTopicName = "";
            effectiveProperties.add(new IntegerProperty(MqttProperties.MqttPropertyType.TOPIC_ALIAS.value(), alias));
          } else if (topicAlias.size() < serverTopicAliasMaximum) {
            // New topic: assign next alias, send full topic name + alias
            int newAlias = topicAlias.size() + 1;
            topicAlias.put(topic, newAlias);
            effectiveProperties.add(new IntegerProperty(MqttProperties.MqttPropertyType.TOPIC_ALIAS.value(), newAlias));
          }
          // else: exhausted all aliases, send full topic name without alias
        }
      } else {
        effectiveProperties = MqttProperties.NO_PROPERTIES;
      }
      variableHeader = new MqttPublishVariableHeader(wireTopicName, nextMessageId(), effectiveProperties);
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
   * {@inheritDoc}
   */
  @Override
  public MqttClient publishAckMessageHandler(Handler<MqttPubAckMessage> handler) {
    this.publishAckMessageHandler = handler;
    return this;
  }

  private synchronized Handler<MqttPubAckMessage> publishAckMessageHandler() {
    return this.publishAckMessageHandler;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public MqttClient publishRecMessageHandler(Handler<MqttPubRecMessage> handler) {
    this.publishRecMessageHandler = handler;
    return this;
  }

  private synchronized Handler<MqttPubRecMessage> publishRecMessageHandler() {
    return this.publishRecMessageHandler;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public MqttClient publishCompMessageHandler(Handler<MqttPubCompMessage> handler) {
    this.publishCompMessageHandler = handler;
    return this;
  }

  private synchronized Handler<MqttPubCompMessage> publishCompMessageHandler() {
    return this.publishCompMessageHandler;
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
    return subscribe(topics, MqttProperties.NO_PROPERTIES);
  }

  @Override
  public Future<Integer> subscribe(Map<String, Integer> topics, MqttProperties properties) {

    // MQTT 5.0 §3.3.4: Subscription Identifier may only be used with MQTT 5.0 and only
    // when the server has not explicitly disabled it (CONNACK SUBSCRIPTION_IDENTIFIER_AVAILABLE=0).
    if (properties != null && properties.getProperty(MqttProperties.MqttPropertyType.SUBSCRIPTION_IDENTIFIER.value()) != null) {
      if (options.getVersion() != 5) {
        String msg = "Subscription Identifier is only available in MQTT 5.0";
        log.error(msg);
        return ctx.failedFuture(new MqttException(MqttException.MQTT_SUBSCRIPTION_IDENTIFIERS_NOT_SUPPORTED, msg));
      }
      if (!serverSubscriptionIdentifierAvailable) {
        String msg = "Server does not support Subscription Identifiers (CONNACK SUBSCRIPTION_IDENTIFIER_AVAILABLE=0)";
        log.error(msg);
        return ctx.failedFuture(new MqttException(MqttException.MQTT_SUBSCRIPTION_IDENTIFIERS_NOT_SUPPORTED, msg));
      }
    }

    // MQTT 5.0 §3.2.2.3.11: Wildcard Subscriptions not supported when server sent WILDCARD_SUBSCRIPTION_AVAILABLE=0
    if (!serverWildcardSubscriptionAvailable && topics.keySet().stream().anyMatch(t -> t.contains("+") || t.contains("#"))) {
      String msg = "Server does not support Wildcard Subscriptions (CONNACK WILDCARD_SUBSCRIPTION_AVAILABLE=0)";
      log.error(msg);
      return ctx.failedFuture(new MqttException(MqttException.MQTT_WILDCARD_SUBSCRIPTIONS_NOT_SUPPORTED, msg));
    }

    // MQTT 5.0 §3.2.2.3.14: Shared Subscriptions not supported when server sent SHARED_SUBSCRIPTION_AVAILABLE=0
    if (!serverSharedSubscriptionAvailable && topics.keySet().stream().anyMatch(t -> t.startsWith("$share/"))) {
      String msg = "Server does not support Shared Subscriptions (CONNACK SHARED_SUBSCRIPTION_AVAILABLE=0)";
      log.error(msg);
      return ctx.failedFuture(new MqttException(MqttException.MQTT_SHARED_SUBSCRIPTIONS_NOT_SUPPORTED, msg));
    }

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

    MqttMessageIdVariableHeader variableHeader;
    if (options.getVersion() == 5) {
      variableHeader = new MqttMessageIdAndPropertiesVariableHeader(nextMessageId(), properties == null ? MqttProperties.NO_PROPERTIES : properties);
    } else {
      variableHeader = MqttMessageIdVariableHeader.from(nextMessageId());
    }
    List<MqttTopicSubscription> subscriptions = topics.entrySet()
      .stream()
      .map(e -> new MqttTopicSubscription(e.getKey(), valueOf(e.getValue())))
      .collect(Collectors.toList());

    MqttSubscribePayload payload = new MqttSubscribePayload(subscriptions);

    io.netty.handler.codec.mqtt.MqttMessage subscribe = MqttMessageFactory.newMessage(fixedHeader, variableHeader, payload);

    return this.write(subscribe).map(variableHeader.messageId());
  }

  @Override
  public Future<Integer> subscribe(List<MqttTopicSubscription> subscriptions, MqttProperties properties) {

    // MQTT 5.0 §3.3.4: Subscription Identifier may only be used with MQTT 5.0 and only
    // when the server has not explicitly disabled it (CONNACK SUBSCRIPTION_IDENTIFIER_AVAILABLE=0).
    if (properties != null && properties.getProperty(MqttProperties.MqttPropertyType.SUBSCRIPTION_IDENTIFIER.value()) != null) {
      if (options.getVersion() != 5) {
        String msg = "Subscription Identifier is only available in MQTT 5.0";
        log.error(msg);
        return ctx.failedFuture(new MqttException(MqttException.MQTT_SUBSCRIPTION_IDENTIFIERS_NOT_SUPPORTED, msg));
      }
      if (!serverSubscriptionIdentifierAvailable) {
        String msg = "Server does not support Subscription Identifiers (CONNACK SUBSCRIPTION_IDENTIFIER_AVAILABLE=0)";
        log.error(msg);
        return ctx.failedFuture(new MqttException(MqttException.MQTT_SUBSCRIPTION_IDENTIFIERS_NOT_SUPPORTED, msg));
      }
    }

    // MQTT 5.0 §3.2.2.3.11: Wildcard Subscriptions not supported when server sent WILDCARD_SUBSCRIPTION_AVAILABLE=0
    if (!serverWildcardSubscriptionAvailable && subscriptions.stream().map(MqttTopicSubscription::topicName).anyMatch(t -> t.contains("+") || t.contains("#"))) {
      String msg = "Server does not support Wildcard Subscriptions (CONNACK WILDCARD_SUBSCRIPTION_AVAILABLE=0)";
      log.error(msg);
      return ctx.failedFuture(new MqttException(MqttException.MQTT_WILDCARD_SUBSCRIPTIONS_NOT_SUPPORTED, msg));
    }

    // MQTT 5.0 §3.2.2.3.14: Shared Subscriptions not supported when server sent SHARED_SUBSCRIPTION_AVAILABLE=0
    if (!serverSharedSubscriptionAvailable && subscriptions.stream().map(MqttTopicSubscription::topicName).anyMatch(t -> t.startsWith("$share/"))) {
      String msg = "Server does not support Shared Subscriptions (CONNACK SHARED_SUBSCRIPTION_AVAILABLE=0)";
      log.error(msg);
      return ctx.failedFuture(new MqttException(MqttException.MQTT_SHARED_SUBSCRIPTIONS_NOT_SUPPORTED, msg));
    }

    List<String> invalidTopics = subscriptions.stream()
      .map(MqttTopicSubscription::topicName)
      .filter(t -> !isValidTopicFilter(t))
      .collect(Collectors.toList());

    if (!invalidTopics.isEmpty()) {
      String msg = String.format("Invalid Topic Filters: %s", invalidTopics);
      log.error(msg);
      return ctx.failedFuture(new MqttException(MqttException.MQTT_INVALID_TOPIC_FILTER, msg));
    }

    MqttFixedHeader fixedHeader = new MqttFixedHeader(
      MqttMessageType.SUBSCRIBE,
      false,
      AT_LEAST_ONCE,
      false,
      0);

    MqttMessageIdVariableHeader variableHeader;
    if (options.getVersion() == 5) {
      variableHeader = new MqttMessageIdAndPropertiesVariableHeader(nextMessageId(), properties == null ? MqttProperties.NO_PROPERTIES : properties);
    } else {
      variableHeader = MqttMessageIdVariableHeader.from(nextMessageId());
    }

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

  @Override
  public MqttClient unsubscribeCompletionMessageHandler(Handler<io.vertx.mqtt.messages.MqttUnsubAckMessage> unsubscribeCompletionMessageHandler) {
    this.unsubscribeCompletionMessageHandler = unsubscribeCompletionMessageHandler;
    return this;
  }

  /**
   * Unsubscribe from receiving messages on given topic
   *
   * @param topic Topic you want to unsubscribe from
   * @return a {@code Future} completed after UNSUBSCRIBE packet sent with packetid
   */
  @Override
  public Future<Integer> unsubscribe(String topic) {
    return unsubscribe(Collections.singletonList(topic));
  }

  private synchronized Handler<Integer> unsubscribeCompletionHandler() {

    return this.unsubscribeCompletionHandler;
  }

  private synchronized Handler<io.vertx.mqtt.messages.MqttUnsubAckMessage> unsubscribeCompletionMessageHandler() {
    return this.unsubscribeCompletionMessageHandler;
  }

  /**
   * See {@link MqttClient#unsubscribe(String, Handler)} )} for more details
   */
  @Override
  public MqttClient unsubscribe(String topic, Handler<AsyncResult<Integer>> unsubscribeSentHandler) {

    Future<Integer> fut = unsubscribe(Collections.singletonList(topic));
    if (unsubscribeSentHandler != null) {
      fut.onComplete(unsubscribeSentHandler);
    }
    return this;
  }

  /**
   * See {@link MqttClient#unsubscribe(List<String>)} )} for more details
   */
  @Override
  public Future<Integer> unsubscribe(List<String> topics) {
    return unsubscribe(topics, MqttProperties.NO_PROPERTIES);
  }

  @Override
  public Future<Integer> unsubscribe(List<String> topics, MqttProperties properties) {

    MqttFixedHeader fixedHeader = new MqttFixedHeader(
      MqttMessageType.UNSUBSCRIBE,
      false,
      AT_LEAST_ONCE,
      false,
      0);

    MqttMessageIdVariableHeader variableHeader;
    if (options.getVersion() == 5) {
      variableHeader = new MqttMessageIdAndPropertiesVariableHeader(nextMessageId(), properties == null ? MqttProperties.NO_PROPERTIES : properties);
    } else {
      variableHeader = MqttMessageIdVariableHeader.from(nextMessageId());
    }

    MqttUnsubscribePayload payload = new MqttUnsubscribePayload(topics);

    io.netty.handler.codec.mqtt.MqttMessage unsubscribe = MqttMessageFactory.newMessage(fixedHeader, variableHeader, payload);

    this.write(unsubscribe);

    return ctx.succeededFuture(variableHeader.messageId());
  }

  /**
   * Unsubscribe from receiving messages on given topics
   *
   * @param topics                 Topics you want to unsubscribe from
   * @param unsubscribeSentHandler handler called after UNSUBSCRIBE packet sent
   * @return current MQTT client instance
   */
  @Override
  public MqttClient unsubscribe(List<String> topics,
    Handler<AsyncResult<Integer>> unsubscribeSentHandler) {
    Future<Integer> fut = unsubscribe(topics);
    if (unsubscribeSentHandler != null) {
      fut.onComplete(unsubscribeSentHandler);
    }
    return this;
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
   * {@inheritDoc}
   */
  @Override
  public synchronized MqttClient disconnectMessageHandler(Handler<MqttDisconnectMessage> handler) {
    this.disconnectMessageHandler = handler;
    return this;
  }

  private synchronized Handler<MqttDisconnectMessage> disconnectMessageHandler() {
    return this.disconnectMessageHandler;
  }

  @Override
  public synchronized MqttClient authenticationExchangeHandler(Handler<MqttAuthenticationExchangeMessage> handler) {
    this.authenticationExchangeHandler = handler;
    return this;
  }

  private synchronized Handler<MqttAuthenticationExchangeMessage> authenticationExchangeHandler() {
    return this.authenticationExchangeHandler;
  }

  private class Ping {
    final long id;
    private Ping(long id) {
      this.id = id;
    }
    void ack() {
      vertx.cancelTimer(id);
    }
    void cancel() {
      vertx.cancelTimer(id);
    }
  }

  /**
   * See {@link MqttClient#ping()} for more details
   */
  @Override
  public MqttClient ping() {
    ctx.execute(() -> {
      MqttFixedHeader fixedHeader =
        new MqttFixedHeader(MqttMessageType.PINGREQ, false, MqttQoS.AT_MOST_ONCE, false, 0);

      io.netty.handler.codec.mqtt.MqttMessage pingreq = MqttMessageFactory.newMessage(fixedHeader, null, null);

      long id = vertx.setTimer(keepAliveTimeout, _id -> {
        disconnect();
      });

      pings.add(new Ping(id));

      this.write(pingreq);
    });
    return this;
  }

  @Override
  public synchronized void pause() {
    connOption.setAutoRead(false);
  }

  public synchronized boolean isPaused() {
    return !connOption.isAutoRead();
  }

  @Override
  public synchronized void resume() {
    connOption.setAutoRead(true);
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

  @Override
  public Future<Void> publishAcknowledge(int publishMessageId, MqttPubAckReasonCode reasonCode, MqttProperties properties) {
    Promise<Void> promise = vertx.getOrCreateContext().promise();
    if (this.status != Status.CONNECTED) {
      promise.fail(new IllegalStateException("Client not connected"));
      return promise.future();
    }
    MqttFixedHeader fixedHeader =
        new MqttFixedHeader(MqttMessageType.PUBACK, false, AT_MOST_ONCE, false, 0);

    io.netty.handler.codec.mqtt.MqttMessage puback;
    if (options.getVersion() == 5) {
      MqttPubReplyMessageVariableHeader variableHeader = new MqttPubReplyMessageVariableHeader(
          publishMessageId,
          reasonCode == null ? MqttPubAckReasonCode.SUCCESS.value() : reasonCode.value(),
          properties == null ? MqttProperties.NO_PROPERTIES : properties);
      puback = MqttMessageFactory.newMessage(fixedHeader, variableHeader, null);
    } else {
      MqttMessageIdVariableHeader variableHeader =
          MqttMessageIdVariableHeader.from(publishMessageId);
      puback = MqttMessageFactory.newMessage(fixedHeader, variableHeader, null);
    }

    this.write(puback).onComplete(promise);
    return promise.future();
  }

  @Override
  public Future<Void> publishReceived(int publishMessageId, MqttPubRecReasonCode reasonCode, MqttProperties properties) {
    Promise<Void> promise = vertx.getOrCreateContext().promise();
    if (this.status != Status.CONNECTED) {
      promise.fail(new IllegalStateException("Client not connected"));
      return promise.future();
    }
    MqttFixedHeader fixedHeader =
        new MqttFixedHeader(MqttMessageType.PUBREC, false, AT_MOST_ONCE, false, 0);

    io.netty.handler.codec.mqtt.MqttMessage pubrec;
    if (options.getVersion() == 5) {
      MqttPubReplyMessageVariableHeader variableHeader = new MqttPubReplyMessageVariableHeader(
          publishMessageId,
          reasonCode == null ? MqttPubRecReasonCode.SUCCESS.value() : reasonCode.value(),
          properties == null ? MqttProperties.NO_PROPERTIES : properties);
      pubrec = MqttMessageFactory.newMessage(fixedHeader, variableHeader, null);
    } else {
      MqttMessageIdVariableHeader variableHeader =
          MqttMessageIdVariableHeader.from(publishMessageId);
      pubrec = MqttMessageFactory.newMessage(fixedHeader, variableHeader, null);
    }
    this.write(pubrec).onComplete(promise);
    return promise.future();
  }

  @Override
  public Future<Void> publishComplete(int publishMessageId, MqttPubCompReasonCode reasonCode, MqttProperties properties) {
    Promise<Void> promise = vertx.getOrCreateContext().promise();
    if (this.status != Status.CONNECTED) {
      promise.fail(new IllegalStateException("Client not connected"));
      return promise.future();
    }
    MqttFixedHeader fixedHeader =
        new MqttFixedHeader(MqttMessageType.PUBCOMP, false, AT_MOST_ONCE, false, 0);

    io.netty.handler.codec.mqtt.MqttMessage pubcomp;
    if (options.getVersion() == 5) {
      MqttPubReplyMessageVariableHeader variableHeader = new MqttPubReplyMessageVariableHeader(
          publishMessageId,
          reasonCode == null ? MqttPubCompReasonCode.SUCCESS.value() : reasonCode.value(),
          properties == null ? MqttProperties.NO_PROPERTIES : properties);
      pubcomp = MqttMessageFactory.newMessage(fixedHeader, variableHeader, null);
    } else {
      MqttMessageIdVariableHeader variableHeader =
          MqttMessageIdVariableHeader.from(publishMessageId);
      pubcomp = MqttMessageFactory.newMessage(fixedHeader, variableHeader, null);
    }
    this.write(pubcomp).onComplete(promise);
    return promise.future();
  }

  @Override
  public Future<Void> publishRelease(int publishMessageId, MqttPubRelReasonCode reasonCode, MqttProperties properties) {
    Promise<Void> promise = vertx.getOrCreateContext().promise();
    if (this.status != Status.CONNECTED) {
      promise.fail(new IllegalStateException("Client not connected"));
      return promise.future();
    }
    MqttFixedHeader fixedHeader =
        new MqttFixedHeader(MqttMessageType.PUBREL, false, MqttQoS.AT_LEAST_ONCE, false, 0);

    io.netty.handler.codec.mqtt.MqttMessage pubrel;
    if (options.getVersion() == 5) {
      MqttPubReplyMessageVariableHeader variableHeader = new MqttPubReplyMessageVariableHeader(
          publishMessageId,
          reasonCode == null ? MqttPubRelReasonCode.SUCCESS.value() : reasonCode.value(),
          properties == null ? MqttProperties.NO_PROPERTIES : properties);
      pubrel = MqttMessageFactory.newMessage(fixedHeader, variableHeader, null);
    } else {
      MqttMessageIdVariableHeader variableHeader =
          MqttMessageIdVariableHeader.from(publishMessageId);
      pubrel = MqttMessageFactory.newMessage(fixedHeader, variableHeader, null);
    }

    this.write(pubrel).onComplete(promise);
    return promise.future();
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
        new IdleStateHandler(0, keepAliveInterval, 0) {
          @Override
          protected void channelIdle(ChannelHandlerContext ctx, IdleStateEvent evt) {
            if (evt.state() == IdleState.WRITER_IDLE) {
              // verify that server is still connected (e.g. when only publishing QoS-0 messages)
              ping();
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
    if (log.isDebugEnabled()) {
      log.debug(String.format("Sending packet %s", mqttMessage));
    }
    return this.connection().writeMessage(mqttMessage);
  }

  /**
   * Used for calling the close handler when the remote MQTT server closes the connection
   */
  private void handleClosed() {
    String pendingRedirect;
    MqttDisconnectMessage pendingDisconnectMessage;
    Promise<MqttConnAckMessage> connectPromise;
    Promise<Void> disconnectPromise;
    NetClient client;
    Deque<Ping> pings;
    synchronized (this) {
      pendingRedirect = this.pendingRedirect;
      this.pendingRedirect = null;
      pendingDisconnectMessage = this.pendingDisconnectMessage;
      this.pendingDisconnectMessage = null;
      client = this.client;
      connectPromise = this.connectPromise;
      disconnectPromise = this.disconnectPromise;
      pings = this.pings;
      this.disconnectPromise = null;
      this.status = Status.CLOSED;
      this.connection = null;
      this.connOption = null;
      this.ctx = null;
      this.client = null;
      this.pings = new ArrayDeque<>();
    }

    // Cleanup pending pings
    pings.forEach(ping -> {
      ping.cancel();
    });

    // MQTT 5.0 server redirect: reconnect transparently to the referenced server
    if (pendingRedirect != null) {
      String[] target = pickServer(pendingRedirect);
      if (target != null) {
        int redirectPort = Integer.parseInt(target[1]);
        String redirectHost = target[0];
        log.info("DISCONNECT SERVER_REFERENCE redirect to " + redirectHost + ":" + redirectPort);
        disconnectPromise.complete();
        client.close();
        this.connect(redirectPort, redirectHost);
        return; // do NOT fire the user's closeHandler
      }
    }

    if (pendingDisconnectMessage != null) {
      Handler<MqttDisconnectMessage> disconnectHandler = disconnectMessageHandler();
      if (disconnectHandler != null) {
        disconnectHandler.handle(pendingDisconnectMessage);
      }
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

      if (log.isDebugEnabled()) {
        log.debug(String.format("Incoming packet %s", msg));
      }

      switch (mqttMessage.fixedHeader().messageType()) {

        case CONNACK:

          io.netty.handler.codec.mqtt.MqttConnAckMessage connack = (io.netty.handler.codec.mqtt.MqttConnAckMessage) mqttMessage;

          MqttConnAckMessage mqttConnAckMessage = MqttConnAckMessage.create(
            connack.variableHeader().connectReturnCode(),
            connack.variableHeader().isSessionPresent(),
            connack.variableHeader().properties());
          handleConnack(mqttConnAckMessage);
          break;

        case PUBLISH:

          io.netty.handler.codec.mqtt.MqttPublishMessage publish = (io.netty.handler.codec.mqtt.MqttPublishMessage) mqttMessage;
          ByteBuf newBuf = VertxHandler.safeBuffer(publish.payload());

          // MQTT 5.0 §3.3.2.3.4 – resolve incoming topic alias (server→client direction)
          String resolvedTopic = publish.variableHeader().topicName();
          MqttProperties.MqttProperty<?> incomingAliasProp =
            publish.variableHeader().properties().getProperty(MqttProperties.MqttPropertyType.TOPIC_ALIAS.value());
          if (incomingAliasProp != null) {
            int alias = (Integer) incomingAliasProp.value();
            int clientMax = options.getTopicAliasMaximum() != null ? options.getTopicAliasMaximum() : 0;
            if (alias < 1 || alias > clientMax) {
              // Alias out of range – protocol error
              disconnect(MqttDisconnectReasonCode.TOPIC_ALIAS_INVALID, MqttProperties.NO_PROPERTIES);
              return;
            }
            if (!resolvedTopic.isEmpty()) {
              // New mapping or overwrite
              synchronized (this) { serverTopicAlias.put(alias, resolvedTopic); }
            } else {
              // Alias-only: look up stored mapping
              synchronized (this) { resolvedTopic = serverTopicAlias.get(alias); }
              if (resolvedTopic == null) {
                // Alias used before being defined – protocol error
                disconnect(MqttDisconnectReasonCode.TOPIC_ALIAS_INVALID, MqttProperties.NO_PROPERTIES);
                return;
              }
            }
          }

          MqttPublishMessage mqttPublishMessage = MqttPublishMessage.create(
            publish.variableHeader().packetId(),
            publish.fixedHeader().qosLevel(),
            publish.fixedHeader().isDup(),
            publish.fixedHeader().isRetain(),
            resolvedTopic,
            newBuf,
            publish.variableHeader().properties());
          handlePublish(mqttPublishMessage);
          break;

        case PUBACK: {
          MqttMessageIdVariableHeader pubackVh = (MqttMessageIdVariableHeader) mqttMessage.variableHeader();
          if (options.getVersion() == 5 && pubackVh instanceof MqttPubReplyMessageVariableHeader) {
            MqttPubReplyMessageVariableHeader rich = (MqttPubReplyMessageVariableHeader) pubackVh;
            handlePuback(rich.messageId(), MqttPubAckReasonCode.valueOf((byte) rich.reasonCode()), rich.properties());
          } else {
            handlePuback(pubackVh.messageId(), MqttPubAckReasonCode.SUCCESS, MqttProperties.NO_PROPERTIES);
          }
          break;
        }

        case PUBREC: {
          MqttMessageIdVariableHeader pubrecVh = (MqttMessageIdVariableHeader) mqttMessage.variableHeader();
          if (options.getVersion() == 5 && pubrecVh instanceof MqttPubReplyMessageVariableHeader) {
            MqttPubReplyMessageVariableHeader rich = (MqttPubReplyMessageVariableHeader) pubrecVh;
            handlePubrec(rich.messageId(), MqttPubRecReasonCode.valueOf((byte) rich.reasonCode()), rich.properties());
          } else {
            handlePubrec(pubrecVh.messageId(), MqttPubRecReasonCode.SUCCESS, MqttProperties.NO_PROPERTIES);
          }
          break;
        }

        case PUBREL:
          handlePubrel(((MqttMessageIdVariableHeader) mqttMessage.variableHeader()).messageId());
          break;

        case PUBCOMP: {
          MqttMessageIdVariableHeader pubcompVh = (MqttMessageIdVariableHeader) mqttMessage.variableHeader();
          if (options.getVersion() == 5 && pubcompVh instanceof MqttPubReplyMessageVariableHeader) {
            MqttPubReplyMessageVariableHeader rich = (MqttPubReplyMessageVariableHeader) pubcompVh;
            handlePubcomp(rich.messageId(), MqttPubCompReasonCode.valueOf((byte) rich.reasonCode()), rich.properties());
          } else {
            handlePubcomp(pubcompVh.messageId(), MqttPubCompReasonCode.SUCCESS, MqttProperties.NO_PROPERTIES);
          }
          break;
        }

        case SUBACK:

          io.netty.handler.codec.mqtt.MqttSubAckMessage suback = (io.netty.handler.codec.mqtt.MqttSubAckMessage) mqttMessage;

          io.netty.handler.codec.mqtt.MqttProperties subackProps = io.netty.handler.codec.mqtt.MqttProperties.NO_PROPERTIES;
          if (suback.variableHeader() instanceof io.netty.handler.codec.mqtt.MqttMessageIdAndPropertiesVariableHeader) {
              subackProps = ((io.netty.handler.codec.mqtt.MqttMessageIdAndPropertiesVariableHeader) suback.variableHeader()).properties();
          }

          MqttSubAckMessage mqttSubAckMessage = MqttSubAckMessage.create(
            suback.variableHeader().messageId(),
            suback.payload().grantedQoSLevels(),
            subackProps);
          handleSuback(mqttSubAckMessage);
          break;

        case UNSUBACK:
          handleUnsuback(mqttMessage);
          break;

        case PINGRESP:
          handlePingresp();
          break;

        case DISCONNECT:
          // MQTT 5.0: server-initiated DISCONNECT – capture reason code and check for SERVER_REFERENCE redirect
          if (options.getVersion() == 5
              && mqttMessage.variableHeader() instanceof MqttReasonCodeAndPropertiesVariableHeader) {
            MqttReasonCodeAndPropertiesVariableHeader disconnVarHeader =
              (MqttReasonCodeAndPropertiesVariableHeader) mqttMessage.variableHeader();
            MqttDisconnectReasonCode disconnectReasonCode =
              MqttDisconnectReasonCode.valueOf((byte) disconnVarHeader.reasonCode());
            MqttDisconnectMessage disconnectMsg =
              MqttDisconnectMessage.create(disconnectReasonCode, disconnVarHeader.properties());
            synchronized (this) {
              this.pendingDisconnectMessage = disconnectMsg;
              if (options.isAutoServerRedirect()) {
                MqttProperties.MqttProperty<?> serverRefProp =
                  disconnVarHeader.properties().getProperty(MqttProperties.MqttPropertyType.SERVER_REFERENCE.value());
                if (serverRefProp != null) {
                  this.pendingRedirect = (String) serverRefProp.value();
                }
              }
            }
          }
          break;

        case AUTH:
          // MQTT 5.0: server-sent AUTH (Enhanced Authentication §3.15)
          if (options.getVersion() == 5
              && mqttMessage.variableHeader() instanceof MqttReasonCodeAndPropertiesVariableHeader) {
            MqttReasonCodeAndPropertiesVariableHeader authVarHeader =
              (MqttReasonCodeAndPropertiesVariableHeader) mqttMessage.variableHeader();
            MqttAuthenticateReasonCode authReasonCode =
              MqttAuthenticateReasonCode.valueOf((byte) authVarHeader.reasonCode());
            MqttAuthenticationExchangeMessage authMsg =
              MqttAuthenticationExchangeMessage.create(authReasonCode, authVarHeader.properties());
            Handler<MqttAuthenticationExchangeMessage> authHandler = this.authenticationExchangeHandler();
            if (authHandler != null) {
              authHandler.handle(authMsg);
            }
          }
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

    Ping ping = pings.poll();
    if (ping != null) {
      ping.ack();
    }

    Handler<Void> handler = pingResponseHandler();
    if (handler != null) {
      handler.handle(null);
    }
  }

  /**
   * Used for calling the unsuback handler when the server acks an unsubscribe
   *
   * @param msg message acknowledged by the server
   */
  private void handleUnsuback(io.netty.handler.codec.mqtt.MqttMessage msg) {

    int unsubackMessageId;
    MqttProperties properties = MqttProperties.NO_PROPERTIES;

    if (msg.variableHeader() instanceof io.netty.handler.codec.mqtt.MqttMessageIdAndPropertiesVariableHeader) {
      io.netty.handler.codec.mqtt.MqttMessageIdAndPropertiesVariableHeader variableHeader =
        (io.netty.handler.codec.mqtt.MqttMessageIdAndPropertiesVariableHeader) msg.variableHeader();
      unsubackMessageId = variableHeader.messageId();
      properties = variableHeader.properties();
    } else {
      unsubackMessageId = ((io.netty.handler.codec.mqtt.MqttMessageIdVariableHeader) msg.variableHeader()).messageId();
    }

    java.util.List<Short> reasonCodes = java.util.Collections.emptyList();
    if (msg.payload() instanceof io.netty.handler.codec.mqtt.MqttUnsubAckPayload) {
      reasonCodes = ((io.netty.handler.codec.mqtt.MqttUnsubAckPayload) msg.payload()).unsubscribeReasonCodes();
    }

    synchronized (this) {
      Handler<MqttUnsubAckMessage> messageHandler = unsubscribeCompletionMessageHandler();
      if (messageHandler != null) {
        messageHandler.handle(MqttUnsubAckMessage.create(unsubackMessageId, reasonCodes, properties));
      }

      Handler<Integer> handler = unsubscribeCompletionHandler();
      if (handler != null) {
        handler.handle(unsubackMessageId);
      }
    }
  }

  /**
   * Used for calling the puback handler when the server acknowledge a QoS 1 message with puback
   *
   * @param pubackMessageId identifier of the message acknowledged by the server
   * @param reasonCode MQTT5 reason code (SUCCESS for MQTT3)
   * @param properties MQTT5 properties (NO_PROPERTIES for MQTT3)
   */
  private void handlePuback(int pubackMessageId, MqttPubAckReasonCode reasonCode, MqttProperties properties) {

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
    Handler<MqttPubAckMessage> ackHandler = publishAckMessageHandler();
    if (ackHandler != null) {
      ackHandler.handle(MqttPubAckMessage.create(pubackMessageId, reasonCode, properties));
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
      countInflightQueue--;
    }
    Handler<Integer> handler = publishCompletionExpirationHandler();
    if (handler != null) {
      handler.handle(expiredMessage.packetId);
    }
  }

  /**
   * Used for calling the pubcomp handler when the server client acknowledge a QoS 2 message with pubcomp
   *
   * @param pubcompMessageId identifier of the message acknowledged by the server
   * @param reasonCode MQTT5 reason code (SUCCESS for MQTT3)
   * @param properties MQTT5 properties (NO_PROPERTIES for MQTT3)
   */
  private void handlePubcomp(int pubcompMessageId, MqttPubCompReasonCode reasonCode, MqttProperties properties) {

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
    Handler<MqttPubCompMessage> compHandler = publishCompMessageHandler();
    if (compHandler != null) {
      compHandler.handle(MqttPubCompMessage.create(pubcompMessageId, reasonCode, properties));
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
      countInflightQueue--;
    }
    Handler<Integer> handler = publishCompletionExpirationHandler();
    if (handler != null) {
      handler.handle(expiredMessage.packetId);
    }
  }

  /**
   * Used for sending the pubrel when a pubrec is received from the server
   *
   * @param pubrecMessageId identifier of the message acknowledged by server
   * @param reasonCode MQTT5 reason code (SUCCESS for MQTT3)
   * @param properties MQTT5 properties (NO_PROPERTIES for MQTT3)
   */
  private void handlePubrec(int pubrecMessageId, MqttPubRecReasonCode reasonCode, MqttProperties properties) {

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
    Handler<MqttPubRecMessage> recHandler = publishRecMessageHandler();
    if (recHandler != null) {
      recHandler.handle(MqttPubRecMessage.create(pubrecMessageId, reasonCode, properties));
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
      countInflightQueue--;
    }
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

    Handler<MqttPublishMessage> handler = this.publishHandler();

    switch (msg.qosLevel()) {

      case AT_MOST_ONCE:
        if (handler != null) {
          handler.handle(msg);
        }
        break;

      case AT_LEAST_ONCE:
        if (options.isAutoAck()) {
          this.publishAcknowledge(msg.messageId());
        } else {
          ((MqttPublishMessageImpl) msg).setAckCallback(() -> this.publishAcknowledge(msg.messageId()));
        }
        if (handler != null) {
          handler.handle(msg);
        }
        break;

      case EXACTLY_ONCE:
        this.publishReceived(msg);
        // we will handle the PUBCOMP when a PUBREL comes
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
    }

    if (options.isAutoAck()) {
      this.publishComplete(pubrelMessageId);
    } else {
      ((MqttPublishMessageImpl) message).setAckCallback(() -> publishComplete(pubrelMessageId));
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

    if (msg.code() == MqttConnectReturnCode.CONNECTION_ACCEPTED) {
      // Apply MQTT 5.0 server-assigned properties before completing the promise
      if (options.getVersion() == 5) {
        applyConnAckProperties(msg);
      }
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
      // Check for MQTT 5.0 server redirect before resetting state
      String serverRef = null;
      if (options.getVersion() == 5 && options.isAutoServerRedirect()) {
        serverRef = msg.serverReference();
      }

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
        this.connOption = null;
        this.client = null;
      }
      connection.closeHandler(null);

      if (serverRef != null) {
        String[] target = pickServer(serverRef);
        if (target != null) {
          int redirectPort = Integer.parseInt(target[1]);
          String redirectHost = target[0];
          log.info("CONNACK SERVER_REFERENCE redirect to " + redirectHost + ":" + redirectPort);
          disconnectPromise.complete();
          client.close();
          this.connect(redirectPort, redirectHost)
            .onComplete(ar -> {
              if (ar.succeeded()) connectPromise.complete(ar.result());
              else connectPromise.fail(ar.cause());
            });
          return;
        }
      }

      MqttConnectionException exception = new MqttConnectionException(msg.code());
      log.error(String.format("Connection refused by the server - code: %s", msg.code()));
      connectPromise.fail(exception);
      disconnectPromise.complete();
      client.close();
    }
  }

  /**
   * Applies MQTT 5.0 CONNACK properties sent by the server to the local client state.
   * <p>
   * Per the MQTT 5.0 specification, certain server-provided values MUST override
   * what the client requested in the CONNECT packet:
   * <ul>
   *   <li>Assigned Client Identifier: stored in options so {@link #clientId()} reflects it</li>
   *   <li>Server Keep Alive: replaces the keep-alive interval the client requested</li>
   * </ul>
   * Other properties (Receive Maximum, Max Packet Size, Max QoS, Retain Available,
   * Topic Alias Maximum, Reason String, User Properties, Response Information,
   * Server Reference, Auth Method/Data) are available to the caller via the
   * {@link MqttConnAckMessage} returned from the connect Future.
   */
  private void applyConnAckProperties(MqttConnAckMessage msg) {
    // Receive Maximum: max concurrent QoS1/2 in-flight messages the server can handle
    Integer receiveMaximum = msg.receiveMaximum();
    synchronized (this) {
      serverReceiveMaximum = (receiveMaximum != null && receiveMaximum > 0) ? receiveMaximum : Integer.MAX_VALUE;
    }
    log.debug("CONNACK serverReceiveMaximum=" + serverReceiveMaximum);

    // Maximum QoS: highest QoS level the server accepts
    Integer maximumQos = msg.maximumQos();
    Long maximumPacketSize = msg.maximumPacketSize();
    synchronized (this) {
      serverMaxQos = (maximumQos != null) ? maximumQos : 2;
      serverMaximumPacketSize = (maximumPacketSize != null) ? maximumPacketSize : Long.MAX_VALUE;
    }
    log.debug("CONNACK serverMaxQos=" + serverMaxQos);
    log.debug("CONNACK serverMaximumPacketSize=" + serverMaximumPacketSize);

    // Assigned Client Identifier: server assigned us an ID (we sent empty ClientID)
    String assignedClientId = msg.assignedClientIdentifier();
    if (assignedClientId != null) {
      options.setClientId(assignedClientId);
      log.debug("Server assigned client identifier: " + assignedClientId);
    }

    // Server Keep Alive: MUST use this value instead of what we sent
    Integer serverKeepAlive = msg.serverKeepAlive();
    if (serverKeepAlive != null && options.getKeepAliveInterval() != serverKeepAlive) {
      options.setKeepAliveInterval(serverKeepAlive);
      log.debug("Server assigned keep alive: " + serverKeepAlive + "s");
      
      // Update the IdleStateHandler in the pipeline with the new keep-alive interval
      ChannelPipeline pipeline = connection.channelHandlerContext().pipeline();
      if (pipeline.get("idle") != null) {
        pipeline.remove("idle");
        pipeline.addBefore("handler", "idle",
            new IdleStateHandler(0, serverKeepAlive, 0) {
              @Override
              protected void channelIdle(ChannelHandlerContext ctx, IdleStateEvent evt) {
                if (evt.state() == IdleState.WRITER_IDLE) {
                  // verify that server is still connected (e.g. when only publishing QoS-0 messages)
                  ping();
                }
              }
            });
      }
    }

    // Topic Alias Maximum: store how many topic aliases the server accepts
    Integer topicAliasMaximum = msg.topicAliasMaximum();
    // Subscription Identifier Available: absent or 1 means available; 0 means NOT available
    Boolean subIdAvailable = msg.subscriptionIdentifierAvailable();
    // Wildcard Subscription Available: absent or 1 means available; 0 means NOT available
    Boolean wildcardAvailable = msg.wildcardSubscriptionAvailable();
    // Shared Subscription Available: absent or 1 means available; 0 means NOT available
    Boolean sharedAvailable = msg.sharedSubscriptionAvailable();
    synchronized (this) {
      serverTopicAliasMaximum = (topicAliasMaximum != null) ? topicAliasMaximum : 0;
      topicAlias.clear();
      serverTopicAlias.clear();
      serverSubscriptionIdentifierAvailable = subIdAvailable == null || subIdAvailable;
      serverWildcardSubscriptionAvailable = wildcardAvailable == null || wildcardAvailable;
      serverSharedSubscriptionAvailable = sharedAvailable == null || sharedAvailable;
    }
    log.debug("CONNACK topicAliasMaximum=" + serverTopicAliasMaximum);
    log.debug("CONNACK subscriptionIdentifierAvailable=" + serverSubscriptionIdentifierAvailable);
    log.debug("CONNACK wildcardSubscriptionAvailable=" + serverWildcardSubscriptionAvailable);
    log.debug("CONNACK sharedSubscriptionAvailable=" + serverSharedSubscriptionAvailable);

    // Log informational properties for debug purposes
    if (log.isDebugEnabled()) {
      if (msg.sessionExpiryInterval() != null)
        log.debug("CONNACK sessionExpiryInterval=" + msg.sessionExpiryInterval());
      if (msg.receiveMaximum() != null)
        log.debug("CONNACK receiveMaximum=" + msg.receiveMaximum());
      if (msg.maximumQos() != null)
        log.debug("CONNACK maximumQos=" + msg.maximumQos());
      if (msg.retainAvailable() != null)
        log.debug("CONNACK retainAvailable=" + msg.retainAvailable());
      if (msg.maximumPacketSize() != null)
        log.debug("CONNACK maximumPacketSize=" + msg.maximumPacketSize());
      if (msg.reasonString() != null)
        log.debug("CONNACK reasonString=" + msg.reasonString());
      if (msg.responseInformation() != null)
        log.debug("CONNACK responseInformation=" + msg.responseInformation());
      if (msg.serverReference() != null)
        log.debug("CONNACK serverReference=" + msg.serverReference());
      if (msg.authenticationMethod() != null)
        log.debug("CONNACK authenticationMethod=" + msg.authenticationMethod());
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
   * Parses a SERVER_REFERENCE string (comma-separated "host:port" or "host" entries)
   * and returns {host, port} for a randomly chosen entry.
   * IPv6 addresses enclosed in brackets are supported (e.g. "[::1]:1883").
   *
   * @return String array {host, portString}, or {@code null} if the string is blank/unparseable
   */
  private String[] pickServer(String serverReference) {
    if (serverReference == null || serverReference.trim().isEmpty()) return null;
    String[] entries = serverReference.split(",");
    String entry = entries[ThreadLocalRandom.current().nextInt(entries.length)].trim();
    if (entry.isEmpty()) return null;
    // IPv6 bracket notation: "[::1]:1883" or just "[::1]"
    if (entry.startsWith("[")) {
      int bracketEnd = entry.indexOf(']');
      if (bracketEnd < 0) return new String[]{entry, String.valueOf(MqttClientOptions.DEFAULT_PORT)};
      String host = entry.substring(1, bracketEnd);
      String rest = entry.substring(bracketEnd + 1);
      int port = rest.startsWith(":") ? Integer.parseInt(rest.substring(1)) : MqttClientOptions.DEFAULT_PORT;
      return new String[]{host, String.valueOf(port)};
    }
    // Regular "host:port" or "host"
    int colonIdx = entry.lastIndexOf(':');
    if (colonIdx > 0) {
      return new String[]{entry.substring(0, colonIdx), entry.substring(colonIdx + 1)};
    }
    return new String[]{entry, String.valueOf(MqttClientOptions.DEFAULT_PORT)};
  }

  /**
   * Estimates the encoded size of MQTT properties for a packet size check.
   * Intentionally over-estimates to be conservative.
   *
   * @param properties the properties to estimate (may be null)
   * @return estimated encoded byte count including the properties length field
   */
  private int estimatePropertiesEncodedSize(MqttProperties properties) {
    if (properties == null || properties == MqttProperties.NO_PROPERTIES) {
      return 1; // 1 byte for zero-length Variable Byte Integer
    }
    int size = 4; // max Variable Byte Integer for the properties length field
    for (MqttProperties.MqttProperty<?> p : properties.listAll()) {
      size += 1; // property type ID byte
      if (p instanceof MqttProperties.IntegerProperty) {
        size += 4;
      } else if (p instanceof MqttProperties.StringProperty) {
        size += 2 + ((String) p.value()).getBytes(StandardCharsets.UTF_8).length;
      } else if (p instanceof MqttProperties.BinaryProperty) {
        size += 2 + ((byte[]) p.value()).length;
      } else if (p instanceof MqttProperties.UserProperties) {
        for (MqttProperties.StringPair pair : ((MqttProperties.UserProperties) p).value()) {
          size += 1 + 2 + pair.key.getBytes(StandardCharsets.UTF_8).length
                      + 2 + pair.value.getBytes(StandardCharsets.UTF_8).length;
        }
      } else {
        size += 8; // generous fallback
      }
    }
    return size;
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
