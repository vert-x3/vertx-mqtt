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

package io.vertx.mqtt.test;

import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetSocket;

/**
 * Proxy class for emulate networking issues
 * (NOTE: works with just one MQTT connection from remote client)
 */
public class Proxy {

  private static final Logger log = LoggerFactory.getLogger(Proxy.class);

  protected static final String SERVER_HOST = "localhost";
  protected static final int SERVER_PORT = 18830;

  private Vertx vertx;
  private String mqttServerHost;
  private int mqttServerPort;

  // server side listening for the MQTT client
  private NetServer server;
  // client side connecting to the MQTT server
  private NetClient client;
  // sockets for handling communication with both sides
  private NetSocket serverSocket;
  private NetSocket clientSocket;

  private boolean paused = false;

  /**
   * Constructor
   *
   * @param vertx Vert.x instance
   * @param mqttServerHost  MQTT server host to connect to
   * @param mqttServerPort  MQTT server port to connect to
   */
  public Proxy(Vertx vertx, String mqttServerHost, int mqttServerPort) {
    this.vertx = vertx;
    this.mqttServerHost = mqttServerHost;
    this.mqttServerPort = mqttServerPort;
  }

  /**
   * Start the proxy
   */
  public void start() {

    this.server = this.vertx.createNetServer();
    this.client = this.vertx.createNetClient();

    // handling incoming connection from the MQTT client
    this.server.connectHandler(socket -> {

      this.serverSocket = socket;

      // handling message from the MQTT client to the MQTT server
      this.serverSocket.handler(buffer -> {

        if (!this.paused) {

          log.info(String.format("%s:%d ---> %s:%d",
            this.clientSocket.localAddress().host(),
            this.clientSocket.localAddress().port(),
            this.clientSocket.remoteAddress().host(),
            this.clientSocket.remoteAddress().port()));

          this.clientSocket.write(buffer);
        }
      });

      // if MQTT client closes connection THEN close connection with MQTT server
      this.serverSocket.closeHandler(v -> {
        this.clientSocket.close();
      });
    });

    this.server.listen(SERVER_PORT, SERVER_HOST, done -> {
      if (done.succeeded()) {
        log.info(String.format("Proxy server started on port %d", done.result().actualPort()));
      } else {
        log.info("Error starting proxy server", done.cause());
      }
    });

    // connect to the MQTT server
    this.client.connect(this.mqttServerPort, this.mqttServerHost, done -> {

      if (done.succeeded()) {

        this.clientSocket = done.result();

        log.info(String.format("Proxy client connected to %s:%d",
          this.clientSocket.remoteAddress().host(),
          this.clientSocket.remoteAddress().port()));

        // handling message from the MQTT server to the MQTT client
        this.clientSocket.handler(buffer -> {

          log.info(String.format("%s:%d <--- %s:%d",
            this.serverSocket.localAddress().host(),
            this.serverSocket.localAddress().port(),
            this.serverSocket.remoteAddress().host(),
            this.serverSocket.remoteAddress().port()));

          this.serverSocket.write(buffer);
        });

        // if MQTT server closes connection THEN close connection with MQTT client
        this.clientSocket.closeHandler(v -> {
          this.serverSocket.close();
        });

      } else {
        log.info("Error connecting proxy client", done.cause());
      }

    });

  }

  /**
   * Stop the proxy
   */
  public void stop() {

    this.server.close();
    this.client.close();
    log.info("Proxy server stopped");
  }

  /**
   * Pause routing traffic from MQTT client to MQTT server
   */
  public void pause() {
    this.paused = true;
  }

  /**
   * Resume routing traffic from MQTT clent to MQTT server
   */
  public void resume() {
    this.paused = false;
  }
}
