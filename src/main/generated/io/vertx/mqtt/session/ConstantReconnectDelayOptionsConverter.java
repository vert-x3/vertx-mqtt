package io.vertx.mqtt.session;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.impl.JsonUtil;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Converter and mapper for {@link io.vertx.mqtt.session.ConstantReconnectDelayOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.mqtt.session.ConstantReconnectDelayOptions} original class using Vert.x codegen.
 */
public class ConstantReconnectDelayOptionsConverter {


  public static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, ConstantReconnectDelayOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
      }
    }
  }

  public static void toJson(ConstantReconnectDelayOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

  public static void toJson(ConstantReconnectDelayOptions obj, java.util.Map<String, Object> json) {
  }
}
