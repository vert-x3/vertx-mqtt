package io.vertx.mqtt.test.server;

import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.WebSocket;
import io.vertx.core.http.WebSocketConnectOptions;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.mqtt.MqttEndpoint;
import io.vertx.mqtt.MqttServer;
import io.vertx.mqtt.MqttServerOptions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;


@RunWith(VertxUnitRunner.class)
public class MqttServerWebSocketPermessageDeflateTest {

  protected static final String MQTT_SERVER_HOST = "localhost";
  protected static final int MQTT_SERVER_PORT = 1883;

  private Vertx vertx;

  @Before
  public void before() {
    this.vertx = Vertx.vertx();
  }

  @After
  public void after(TestContext context) {
    this.vertx.close().onComplete(context.asyncAssertSuccess());
  }

  @Test
  public void testPermessageDeflate(TestContext context) {
    MqttServerOptions options = new MqttServerOptions()
      .setHost(MQTT_SERVER_HOST)
      .setPort(MQTT_SERVER_PORT)
      .setUseWebSocket(true);
    MqttServer server = MqttServer.create(vertx, options);
    Async done = context.async();
    server.endpointHandler(MqttEndpoint::accept);
    Async listen = context.async();
    server.listen().onComplete(context.asyncAssertSuccess(s -> listen.complete()));
    listen.awaitSuccess(15_000);

    vertx.createWebSocketClient()
      .connect(new WebSocketConnectOptions()
        .setPort(MQTT_SERVER_PORT)
        .setHost(MQTT_SERVER_HOST)
        .setURI("/mqtt")
        .setHeaders(MultiMap.caseInsensitiveMultiMap().add("sec-websocket-extensions", "permessage-deflate"))
      )
      .onComplete(handler -> {
        if (handler.succeeded()) {
          WebSocket webSocket = handler.result();
          MultiMap handshakeHeaders = webSocket.headers();

          context.assertEquals("permessage-deflate", handshakeHeaders.get("sec-websocket-extensions"));
          done.complete();
        } else {
          context.fail();
        }
      });
  }
}
