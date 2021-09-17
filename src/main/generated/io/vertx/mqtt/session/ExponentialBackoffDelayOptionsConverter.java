package io.vertx.mqtt.session;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.impl.JsonUtil;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Converter and mapper for {@link io.vertx.mqtt.session.ExponentialBackoffDelayOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.mqtt.session.ExponentialBackoffDelayOptions} original class using Vert.x codegen.
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
