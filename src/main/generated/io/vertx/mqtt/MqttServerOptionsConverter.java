package io.vertx.mqtt;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Converter and mapper for {@link io.vertx.mqtt.MqttServerOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.mqtt.MqttServerOptions} original class using Vert.x codegen.
 */
public class MqttServerOptionsConverter {

   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, MqttServerOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "maxMessageSize":
          if (member.getValue() instanceof Number) {
            obj.setMaxMessageSize(((Number)member.getValue()).intValue());
          }
          break;
        case "autoClientId":
          if (member.getValue() instanceof Boolean) {
            obj.setAutoClientId((Boolean)member.getValue());
          }
          break;
        case "maxClientIdLength":
          if (member.getValue() instanceof Number) {
            obj.setMaxClientIdLength(((Number)member.getValue()).intValue());
          }
          break;
        case "timeoutOnConnect":
          if (member.getValue() instanceof Number) {
            obj.setTimeoutOnConnect(((Number)member.getValue()).intValue());
          }
          break;
        case "useWebSocket":
          if (member.getValue() instanceof Boolean) {
            obj.setUseWebSocket((Boolean)member.getValue());
          }
          break;
        case "webSocketMaxFrameSize":
          if (member.getValue() instanceof Number) {
            obj.setWebSocketMaxFrameSize(((Number)member.getValue()).intValue());
          }
          break;
        case "perFrameWebSocketCompressionSupported":
          if (member.getValue() instanceof Boolean) {
            obj.setPerFrameWebSocketCompressionSupported((Boolean)member.getValue());
          }
          break;
        case "perMessageWebSocketCompressionSupported":
          if (member.getValue() instanceof Boolean) {
            obj.setPerMessageWebSocketCompressionSupported((Boolean)member.getValue());
          }
          break;
        case "webSocketCompressionLevel":
          if (member.getValue() instanceof Number) {
            obj.setWebSocketCompressionLevel(((Number)member.getValue()).intValue());
          }
          break;
        case "webSocketAllowServerNoContext":
          if (member.getValue() instanceof Boolean) {
            obj.setWebSocketAllowServerNoContext((Boolean)member.getValue());
          }
          break;
        case "webSocketPreferredClientNoContext":
          if (member.getValue() instanceof Boolean) {
            obj.setWebSocketPreferredClientNoContext((Boolean)member.getValue());
          }
          break;
      }
    }
  }

   static void toJson(MqttServerOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(MqttServerOptions obj, java.util.Map<String, Object> json) {
    json.put("maxMessageSize", obj.getMaxMessageSize());
    json.put("autoClientId", obj.isAutoClientId());
    json.put("maxClientIdLength", obj.getMaxClientIdLength());
    json.put("useWebSocket", obj.isUseWebSocket());
    json.put("webSocketMaxFrameSize", obj.getWebSocketMaxFrameSize());
    json.put("perFrameWebSocketCompressionSupported", obj.isPerFrameWebSocketCompressionSupported());
    json.put("perMessageWebSocketCompressionSupported", obj.isPerMessageWebSocketCompressionSupported());
    json.put("webSocketCompressionLevel", obj.getWebSocketCompressionLevel());
    json.put("webSocketAllowServerNoContext", obj.isWebSocketAllowServerNoContext());
    json.put("webSocketPreferredClientNoContext", obj.isWebSocketPreferredClientNoContext());
  }
}
