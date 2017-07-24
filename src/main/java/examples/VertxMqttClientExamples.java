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

package examples;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;

public class VertxMqttClientExamples {

  /**
   * Example for demonstration of how {@link MqttClient#connect()} and  {@link MqttClient#disconnect()} methods
   * should be used
   *
   * @param vertx
   */
  public void example1(Vertx vertx) {
    MqttClientOptions options = new MqttClientOptions()
      .setHost("iot.eclipse.org")
      .setPort(1883);

    MqttClient client = MqttClient.create(vertx, options);

    client.connect(s -> client.disconnect());
  }

  /**
   * Example for handling publish messages from server
   *
   * @param client
   */
  public void example2(MqttClient client) {
    client.publishHandler(s -> {
      System.out.println("There are new message in topic: " + s.topicName());
      System.out.println("Content(as string) of the message: " + s.payload().toString());
      System.out.println("QoS: " + s.qosLevel());
    })
      .subscribe("rpi2/temp", 2);
  }

  /**
   * Example for sending publish message
   *
   * @param client
   */
  public void example3(MqttClient client) {
    client.publish("temperature",
      Buffer.buffer("hello".getBytes()),
      MqttQoS.AT_LEAST_ONCE,
      false,
      false);
  }

  /**
   * Example for disabling keepAlive feature
   *
   * @param options
   */
  public void example4(MqttClientOptions options) {
    options.setAutoKeepAlive(false);
  }

  /**
   * Example for publishCompleteHandler method demonstration
   *
   * @param client
   */
  public void example5(MqttClient client) {
    client.publishCompleteHandler(id -> {
      System.out.println("Id of just received PUBACK or PUBCOMP packet is " + id);
    })
      // The line of code below will trigger publishCompleteHandler (QoS 2)
      .publish("hello", Buffer.buffer("hello".getBytes()), MqttQoS.EXACTLY_ONCE, false, false)
      // The line of code below will trigger publishCompleteHandler (QoS is 1)
      .publish("hello", Buffer.buffer("hello".getBytes()), MqttQoS.AT_LEAST_ONCE, false, false)
      // The line of code below does not trigger because QoS value is 0
      .publish("hello", Buffer.buffer("hello".getBytes()), MqttQoS.AT_LEAST_ONCE, false, false);

  }

  /**
   * Example for subscribeCompleteHandler method demonstration
   *
   * @param client
   */
  public void example6(MqttClient client) {
    client.subscribeCompleteHandler(mqttSubAckMessage -> {
      System.out.println("Id of just received SUBACK packet is " + mqttSubAckMessage.messageId());
      mqttSubAckMessage.grantedQoSLevels().stream().forEach(s -> {
        if (s.byteValue() == 0x80) {
          System.out.println("Failure");
        } else {
          System.out.println("Success. Maximum QoS is " + s.byteValue());
        }
      });
    })
      .subscribe("temp", 1)
      .subscribe("temp2", 2);
  }

  /**
   * Example for unsubscribeCompleteHandler method demonstration
   *
   * @param client
   */
  public void example7(MqttClient client) {
    client
      .unsubscribeCompleteHandler(id -> System.out.println("Id of just received UNSUBACK packet is " + id))
      .subscribe("temp", 1)
      .unsubscribe("temp");
  }

  /**
   * Example for unsubscribe method demonstration
   *
   * @param client
   */
  public void example8(MqttClient client) {
    client
      .subscribe("temp", 1)
      .unsubscribe("temp", id -> System.out.println("Id of just sent UNSUBSCRIBE packet is " + id));
  }

  /**
   * Example for pingResponseHandler method demonstration
   *
   * @param client
   */
  public void example9(MqttClient client) {
    client.pingResponseHandler(s -> {
      //The handler will be called time to time by default
      System.out.println("We have just received PINGRESP packet");
    });
  }
}
