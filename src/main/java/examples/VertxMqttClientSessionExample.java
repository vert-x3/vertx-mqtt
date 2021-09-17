/*
 * Copyright 2021 Red Hat Inc.
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

import io.vertx.core.Vertx;
import io.vertx.mqtt.MqttClientSession;
import io.vertx.mqtt.MqttClientSessionOptions;
import io.vertx.mqtt.session.RequestedQoS;

public class VertxMqttClientSessionExample {

  public static void main(String[] args) {
    Vertx vertx = Vertx.vertx();

    MqttClientSessionOptions options = new MqttClientSessionOptions();

    MqttClientSession session = MqttClientSession.create(vertx, options)
      .sessionStateHandler(state -> System.out.format("State changed - state: %s, cause: %s%n", state.getSessionState(), state.getCause()))
      .subscriptionStateHandler(state -> System.out.format("Subscription changed [%s] - state: %s, QoS: %s%n", state.getTopic(), state.getSubscriptionState(), state.getQos()))
      .messageHandler(message -> System.out.format("Message received: %s%n", message))
      .subscribe(RequestedQoS.QOS_1, "foo", "bar", "baz/#");

    session.start();

  }

}
