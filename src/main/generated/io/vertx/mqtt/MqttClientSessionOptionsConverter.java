package io.vertx.mqtt;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.impl.JsonUtil;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Converter and mapper for {@link io.vertx.mqtt.MqttClientSessionOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.mqtt.MqttClientSessionOptions} original class using Vert.x codegen.
 */
public class MqttClientSessionOptionsConverter {


  public static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, MqttClientSessionOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "hostname":
          if (member.getValue() instanceof String) {
            obj.setHostname((String)member.getValue());
          }
          break;
        case "port":
          if (member.getValue() instanceof Number) {
            obj.setPort(((Number)member.getValue()).intValue());
          }
          break;
      }
    }
  }

  public static void toJson(MqttClientSessionOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

  public static void toJson(MqttClientSessionOptions obj, java.util.Map<String, Object> json) {
    if (obj.getHostname() != null) {
      json.put("hostname", obj.getHostname());
    }
    json.put("port", obj.getPort());
  }
}
