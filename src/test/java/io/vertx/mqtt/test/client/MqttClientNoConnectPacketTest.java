package io.vertx.mqtt.test.client;

import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;
import io.vertx.mqtt.MqttServer;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * MQTT client testing about behaviour of the client when server don't response with CONNACK.
 */
@RunWith(VertxUnitRunner.class)
public class MqttClientNoConnectPacketTest {

  private final static Integer REPRODUCER_SERVER_PORT = 1884;
  private final static String REPRODUCER_SERVER_HOST = "0.0.0.0";

  @Test
  public void connectionTimeoutIfNoPacketSent(TestContext context) {
    int connackTimeoutSeconds = 2;
    int serverResponseInSeconds = 2;
    Vertx vertx = Vertx.vertx();
    Async async = context.async(1);
    MqttServer mqttServer = MqttServer.create(vertx);
    mqttServer.endpointHandler(mqttEndpoint -> {
      vertx.setTimer(serverResponseInSeconds * 1000, action -> mqttEndpoint.close());
    }).listen(REPRODUCER_SERVER_PORT);
    MqttClient mqttClient = MqttClient.create(vertx, new MqttClientOptions().setConnackTimeoutSeconds(connackTimeoutSeconds));
    mqttClient.connect(REPRODUCER_SERVER_PORT, REPRODUCER_SERVER_HOST, connected -> {
      if (connected.succeeded()) {
        context.fail("Connect handles should not succeed without receiving ack");
      } else {
        async.countDown();
      }
    });
    async.await((connackTimeoutSeconds + 2) * 1_000);
  }
}
