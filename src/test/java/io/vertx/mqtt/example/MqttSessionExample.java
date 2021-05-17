package io.vertx.mqtt.example;

import io.vertx.core.Vertx;
import io.vertx.mqtt.MqttClientSession;
import io.vertx.mqtt.MqttClientSessionOptions;

public class MqttSessionExample {

  public static void main(String[] args) {
    Vertx vertx = Vertx.vertx();

    MqttClientSessionOptions options = new MqttClientSessionOptions();

    MqttClientSession session = MqttClientSession.create(vertx, options)
      .sessionStateHandler(state -> System.out.format("State changed - state: %s, cause: %s%n", state.getSessionState(), state.getCause()))
      .subscriptionStateHandler(state -> System.out.format("Subscription changed [%s] - state: %s, QoS: %s%n", state.getTopic(), state.getSubscriptionState(), state.getQos()))
      .messageHandler(message -> System.out.format("Message received: %s%n", message))
      .subscribe(MqttClientSession.RequestedQoS.QOS_1, "foo", "bar", "baz/#");

    session.start();

  }

}
