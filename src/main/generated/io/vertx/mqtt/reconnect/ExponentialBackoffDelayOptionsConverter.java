package io.vertx.mqtt.reconnect;

import io.vertx.core.json.JsonObject;

/**
 * Converter and mapper for {@link io.vertx.mqtt.reconnect.ExponentialBackoffDelayOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.mqtt.reconnect.ExponentialBackoffDelayOptions}
 * original class using Vert.x codegen.
 */
public class ExponentialBackoffDelayOptionsConverter {

  public static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, ExponentialBackoffDelayOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
      }
    }
  }

  public static void toJson(ExponentialBackoffDelayOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

  public static void toJson(ExponentialBackoffDelayOptions obj, java.util.Map<String, Object> json) {
  }
}
