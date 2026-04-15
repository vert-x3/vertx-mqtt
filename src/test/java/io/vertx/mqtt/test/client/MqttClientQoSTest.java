package io.vertx.mqtt.test.client;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.mqtt.*;
import io.vertx.mqtt.messages.MqttPublishMessage;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.Assert.*;

@RunWith(VertxUnitRunner.class)
public class MqttClientQoSTest {

  private Vertx vertx;
  private MqttServer server;
  private MqttClient client;

  @Before
  public void before() {
    vertx = Vertx.vertx();
    server = MqttServer.create(vertx);
  }

  @After
  public void after() {
    server.close().await();
    vertx.close().await();
  }

  @Test
  public void testQoS1SendDuplicateShouldFail() throws Exception {
    testSendDuplicateShouldFail(MqttQoS.AT_LEAST_ONCE);
  }

  @Test
  public void testQoS2SendDuplicateShouldFail() throws Exception {
    testSendDuplicateShouldFail(MqttQoS.EXACTLY_ONCE);
  }

  /**
   * The client should not accept to send a PUBLISH message with a duplicate ID.
   */
  private void testSendDuplicateShouldFail(MqttQoS qos) throws Exception {
    AtomicInteger receivedCount = new AtomicInteger();
    server.endpointHandler(endpoint -> {
      endpoint.publishHandler(msg -> {
        receivedCount.incrementAndGet();
      });
      endpoint.accept();
    });
    startServer();
    client = MqttClient.create(vertx, new MqttClientOptions().setAutoAck(false).setAckTimeout(10));
    client.connect(MqttServerOptions.DEFAULT_PORT, MqttServerOptions.DEFAULT_HOST).await();
    Integer id = client.publish("test", Buffer.buffer(), qos, false, false).await();
    try {
      client.publish(id, "test", Buffer.buffer(), qos, false, false).await();
      fail();
    } catch (Throwable e) {
      MqttException mqttEx = (MqttException) e;
      assertEquals(MqttException.MQTT_INVALID_MESSAGE_ID, mqttEx.code());
    }
    assertTrue(() -> receivedCount.get() < 2, Duration.ofSeconds(2));
  }

  @Test
  public void testQoS1SendExpiredDuplicate() throws Exception {
    testSendExpiredDuplicate(MqttQoS.AT_LEAST_ONCE);
  }

  @Test
  public void testQoS2SendExpiredDuplicate() throws Exception {
    testSendExpiredDuplicate(MqttQoS.EXACTLY_ONCE);
  }

  /**
   * It should be possible to resent an expired PUBLISH message with the client (retry).
   * <p/>
   * Simulates the use case of PUBLISH message loss or PUBACK/PUBREC loss:
   * - client sends PUBLISH
   * - we pretend server does not receive the message, the client expires the message ID
   * - client sends PUBLISH
   * - server sends PUBACK/PUBREC
   * - QoS2: client sends PUBREL
   * - QoS2: server sends PUBCOMP
   */
  private void testSendExpiredDuplicate(MqttQoS qos) throws Exception {
    AtomicInteger receivedCount = new AtomicInteger();
    server.endpointHandler(endpoint -> {
      endpoint.publishHandler(msg -> {
        switch (receivedCount.getAndIncrement()) {
          case 1:
            msg.ack();
            break;
        }
      });
      endpoint.publishReleaseHandler(msgId -> endpoint.publishComplete(msgId));
      endpoint.accept();
    });
    startServer();
    client = MqttClient.create(vertx, new MqttClientOptions().setAutoAck(false).setAckTimeout(1));
    List<Integer> completionIds = Collections.synchronizedList(new ArrayList<>());
    List<Integer> expiredIds = Collections.synchronizedList(new ArrayList<>());
    client.publishCompletionHandler(completionIds::add);
    client.publishCompletionExpirationHandler(expiredIds::add);
    client.connect(MqttServerOptions.DEFAULT_PORT, MqttServerOptions.DEFAULT_HOST).await();
    Integer id = client.publish("test", Buffer.buffer(), qos, false, false).await();
    assertWaitUntil(() -> expiredIds.size() == 1);
    Integer newId = client.publish(id, "test", Buffer.buffer(), qos, false, false).await();
    assertEquals(id, newId);
    assertWaitUntil(() -> completionIds.size() == 1);
  }

  @Test
  public void testQos1UnknownCompletion() throws Exception {
    testUnknownCompletion(MqttQoS.AT_LEAST_ONCE);
  }

  @Test
  public void testQos2UnknownCompletion() throws Exception {
    testUnknownCompletion(MqttQoS.EXACTLY_ONCE);
  }

  /**
   * Simple test checking that receiving an unknown message ID from server triggers
   * the unknown packet id handler.
   */
  private void testUnknownCompletion(MqttQoS qos) throws Exception {
    AtomicReference<MqttPublishMessage> msgRef = new AtomicReference<>();
    server.endpointHandler(endpoint -> {
      endpoint.publishHandler(msg -> msgRef.set(msg));
      endpoint.accept();
    });
    startServer();
    client = MqttClient.create(vertx, new MqttClientOptions().setAutoAck(false).setAckTimeout(1));
    List<Integer> expiredIds = Collections.synchronizedList(new ArrayList<>());
    List<Integer> unknownIds = Collections.synchronizedList(new ArrayList<>());
    client.publishCompletionExpirationHandler(expiredIds::add);
    client.publishCompletionUnknownPacketIdHandler(unknownIds::add);
    client.connect(MqttServerOptions.DEFAULT_PORT, MqttServerOptions.DEFAULT_HOST).await();
    Integer id = client.publish("test", Buffer.buffer(), qos, false, false).await();
    assertWaitUntil(() -> expiredIds.size() == 1);
    assertEquals(0, unknownIds.size());
    assertSame(id, msgRef.get().messageId());
    msgRef.get().ack();
    assertWaitUntil(() -> unknownIds.size() == 1);
  }

  /**
   * It should be possible to resent an expired PUBREL message with the client (retry).
   * <p/>
   * This simulates the use case of PUBREL message loss or PUBCOMP loss:
   * - client sends PUBLISH
   * - server sends PUBREC
   * - client sends PUBREL
   * - client PUBREL expires and retransmit it
   * - server sends PUBCOMP
   */
  @Test
  public void testQos2CompRetransmit() throws Exception {
    AtomicInteger publishCount = new AtomicInteger();
    AtomicInteger releaseCount = new AtomicInteger();
    server.endpointHandler(endpoint -> {
      endpoint.publishHandler(msg -> {
        publishCount.incrementAndGet();
        msg.ack();
      });
      endpoint.publishReleaseHandler(msgId -> {
        switch (releaseCount.getAndIncrement()) {
          case 1:
            endpoint.publishComplete(msgId);
            break;
        }
      });
      endpoint.accept();
    });
    startServer();
    client = MqttClient.create(vertx, new MqttClientOptions().setAutoAck(false).setAckTimeout(1));
    List<Integer> completionIds = Collections.synchronizedList(new ArrayList<>());
    List<Integer> expiredIds = Collections.synchronizedList(new ArrayList<>());
    client.publishCompletionHandler(completionIds::add);
    client.publishCompletionExpirationHandler(expiredIds::add);
    client.connect(MqttServerOptions.DEFAULT_PORT, MqttServerOptions.DEFAULT_HOST).await();
    Integer id = client.publish("test", Buffer.buffer(), MqttQoS.EXACTLY_ONCE, false, false).await();
    assertWaitUntil(() -> expiredIds.size() == 1);
    assertWaitUntil(() -> publishCount.get() == 1);
    assertWaitUntil(() -> releaseCount.get() == 1);
    client.publishRelease(id).await();
    assertWaitUntil(() -> releaseCount.get() == 2);
    assertWaitUntil(() -> completionIds.size() == 1);
    assertTrue(() -> expiredIds.size() == 1 && publishCount.get() == 1, Duration.ofSeconds(1));
  }

  private void startServer() throws Exception {
    server.listen().await(20, TimeUnit.SECONDS);
  }

  private void assertWaitUntil(BooleanSupplier cond) throws Exception {
    long now = System.currentTimeMillis();
    while (!cond.getAsBoolean()) {
      Assert.assertTrue(System.currentTimeMillis() - now < 20_000);
      Thread.sleep(10);
    }
  }

  private void assertTrue(BooleanSupplier cond, Duration duration) throws Exception {
    long now = System.currentTimeMillis();
    while (System.currentTimeMillis() - now < duration.toMillis()) {
      Assert.assertTrue(cond.getAsBoolean());
    }
  }
}
