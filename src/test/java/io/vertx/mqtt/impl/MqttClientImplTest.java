/*
 * Copyright 2020 Bosch.IO GmbH
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

import static io.netty.handler.codec.mqtt.MqttQoS.AT_MOST_ONCE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.mqtt.MqttFixedHeader;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageFactory;
import io.netty.handler.codec.mqtt.MqttMessageIdVariableHeader;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.impl.future.PromiseInternal;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.impl.NetSocketInternal;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.mqtt.MqttClientOptions;
import io.vertx.mqtt.messages.MqttConnAckMessage;

/**
 * Tests verifying behavior of {@link MqttClientImpl}.
 *
 */
@RunWith(VertxUnitRunner.class)
public class MqttClientImplTest {

  private VertxInternal vertx;
  private ContextInternal context;
  private NetClient netClient;
  private NetSocketInternal netSocket;

  /**
   * Sets up the fixture.
   */
  @SuppressWarnings("unchecked")
  @Before
  public void setUp() {
    netClient = mock(NetClient.class);
    vertx = mock(VertxInternal.class);
    context = mock(ContextInternal.class);
    when(context.owner()).thenReturn(vertx);
    when(context.failedFuture(any(Throwable.class))).thenAnswer(invocation -> {
      return Future.failedFuture(invocation.getArgument(0, Throwable.class));
    });
    doAnswer(invocation -> {
      Handler<Void> handler = invocation.getArgument(0);
      handler.handle(null);
      return null;
    }).when(context).runOnContext(any(Handler.class));
    when(vertx.getOrCreateContext()).thenReturn(context);
    when(vertx.createNetClient(any(NetClientOptions.class))).thenReturn(netClient);
  }

  /**
   * Verifies that the client invokes the registered completion expiration handler
   * if no PUBACK is received from the server for a QoS 1 message.
   */
  @SuppressWarnings("unchecked")
  @Test
  public void testPublishQos1InvokesHandlerOnPubackTimeout() {

    // GIVEN an MQTT client with expiration and phantom handlers set
    Handler<Integer> expirationHandler = mock(Handler.class);
    Handler<Integer> phantomHandler = mock(Handler.class);
    MqttClientImpl client = givenAConnectedClient();
    client.publishCompletionExpirationHandler(expirationHandler);
    client.publishCompletionPhantomHandler(phantomHandler);
    ArgumentCaptor<Handler<Object>> messageHandler = ArgumentCaptor.forClass(Handler.class);
    verify(netSocket).messageHandler(messageHandler.capture());

    // WHEN the client publishes a message using QoS 1
    Promise<Integer> sendHandler = Promise.promise();
    client.publish("topic", Buffer.buffer("Hello"), MqttQoS.AT_LEAST_ONCE, false, false, sendHandler);

    // and no PUBACK is received before the ACK timeout is reached
    assertTrue(sendHandler.future().succeeded());
    ArgumentCaptor<Handler<Long>> timerTask = ArgumentCaptor.forClass(Handler.class);
    verify(vertx).setTimer(anyLong(), timerTask.capture());
    timerTask.getValue().handle(1L);

    // THEN the expiration handler is invoked
    verify(expirationHandler).handle(eq(sendHandler.future().result()));
    // and the message is removed from the outbound queue
    assertEquals(0, client.getInFlightMessagesCount());

    // and when a PUBACK for the message arrives
    messageHandler.getValue().handle(createAckMessage(MqttMessageType.PUBACK, sendHandler.future().result()));
    // THEN the phantom handler is invoked
    verify(phantomHandler).handle(eq(sendHandler.future().result()));
  }

  /**
   * Verifies that the client invokes the registered completion expiration handler
   * if no PUBREC is received from the server for a QoS 2 message.
   */
  @SuppressWarnings("unchecked")
  @Test
  public void testPublishQos2InvokesHandlerOnPubrecTimeout() {

    // GIVEN an MQTT client with expiration and phantom handlers set
    Handler<Integer> expirationHandler = mock(Handler.class);
    Handler<Integer> phantomHandler = mock(Handler.class);
    MqttClientImpl client = givenAConnectedClient();
    client.publishCompletionExpirationHandler(expirationHandler);
    client.publishCompletionPhantomHandler(phantomHandler);
    ArgumentCaptor<Handler<Object>> messageHandler = ArgumentCaptor.forClass(Handler.class);
    verify(netSocket).messageHandler(messageHandler.capture());

    // WHEN the client publishes a message using QoS 2
    Promise<Integer> sendHandler = Promise.promise();
    client.publish("topic", Buffer.buffer("Hello"), MqttQoS.EXACTLY_ONCE, false, false, sendHandler);

    // and no PUBREC is received before the ACK timeout is reached
    assertTrue(sendHandler.future().succeeded());
    ArgumentCaptor<Handler<Long>> timerTask = ArgumentCaptor.forClass(Handler.class);
    verify(vertx).setTimer(anyLong(), timerTask.capture());
    timerTask.getValue().handle(1L);

    // THEN the expiration handler is invoked
    verify(expirationHandler).handle(eq(sendHandler.future().result()));
    // and the message is removed from the outbound queue
    assertEquals(0, client.getInFlightMessagesCount());

    // and when a PUBREC for the message arrives
    messageHandler.getValue().handle(createAckMessage(MqttMessageType.PUBREC, sendHandler.future().result()));
    // THEN the phantom handler is invoked
    verify(phantomHandler).handle(eq(sendHandler.future().result()));
  }

  /**
   * Verifies that the client invokes the registered completion expiration handler
   * if no PUBCOMP is received from the server for a QoS 2 message.
   */
  @SuppressWarnings("unchecked")
  @Test
  public void testPublishQos2InvokesHandlerOnPubcompTimeout() {

    // GIVEN an MQTT client with expiration and phantom handlers set
    Handler<Integer> expirationHandler = mock(Handler.class);
    Handler<Integer> phantomHandler = mock(Handler.class);
    MqttClientImpl client = givenAConnectedClient();
    client.publishCompletionExpirationHandler(expirationHandler);
    client.publishCompletionPhantomHandler(phantomHandler);
    ArgumentCaptor<Handler<Object>> messageHandler = ArgumentCaptor.forClass(Handler.class);
    verify(netSocket).messageHandler(messageHandler.capture());

    // WHEN the client publishes a message using QoS 2
    Promise<Integer> sendHandler = Promise.promise();
    client.publish("topic", Buffer.buffer("Hello"), MqttQoS.EXACTLY_ONCE, false, false, sendHandler);

    // and the server responds with a PUBREC
    assertTrue(sendHandler.future().succeeded());
    messageHandler.getValue().handle(createAckMessage(MqttMessageType.PUBREC, sendHandler.future().result()));
    assertEquals(1, client.getInFlightMessagesCount());

    // but no PUBCOMP is received before the ACK timeout is reached
    ArgumentCaptor<Handler<Long>> timerTask = ArgumentCaptor.forClass(Handler.class);
    verify(vertx, times(2)).setTimer(anyLong(), timerTask.capture());
    timerTask.getValue().handle(1L);
    // THEN the expiration handler is invoked
    verify(expirationHandler).handle(eq(sendHandler.future().result()));
    // and the message is removed from the outbound queue
    assertEquals(0, client.getInFlightMessagesCount());

    // and when a PUBCOMP for the message arrives
    messageHandler.getValue().handle(createAckMessage(MqttMessageType.PUBCOMP, sendHandler.future().result()));
    // THEN the phantom handler is invoked
    verify(phantomHandler).handle(eq(sendHandler.future().result()));
  }

  @SuppressWarnings("unchecked")
  private MqttClientImpl givenAConnectedClient() {
    MqttClientImpl client = new MqttClientImpl(vertx, new MqttClientOptions());
    PromiseInternal<Object> promise = mock(PromiseInternal.class);
    when(promise.future()).thenReturn(Future.succeededFuture(mock(MqttConnAckMessage.class)));
    when(context.promise()).thenReturn(promise);
    ChannelPipeline channelPipeline = mock(ChannelPipeline.class);
    ChannelHandlerContext channelHandlerContext = mock(ChannelHandlerContext.class);
    when(channelHandlerContext.pipeline()).thenReturn(channelPipeline);
    netSocket = mock(NetSocketInternal.class);
    when(netSocket.channelHandlerContext()).thenReturn(channelHandlerContext);
    when(netSocket.writeMessage(any())).thenReturn(Future.succeededFuture());
    when(netClient.connect(anyInt(), anyString(), any(), any(Handler.class))).thenAnswer(invocation -> {
      Handler<AsyncResult<NetSocket>> done = invocation.getArgument(3);
      done.handle(Future.succeededFuture(netSocket));
      return netClient;
    });

    client.connect(1883, "localhost", conAttempt -> {
    });
    return client;
  }

  private static MqttMessage createAckMessage(MqttMessageType type, int packetId) {
    MqttFixedHeader fixedHeader = new MqttFixedHeader(type, false, AT_MOST_ONCE, false, 0);

    MqttMessageIdVariableHeader variableHeader = MqttMessageIdVariableHeader.from(packetId);

    return MqttMessageFactory.newMessage(fixedHeader, variableHeader, null);
  }
}
