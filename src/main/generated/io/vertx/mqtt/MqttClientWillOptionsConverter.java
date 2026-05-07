package io.vertx.mqtt;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.impl.JsonUtil;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * Converter and mapper for {@link io.vertx.mqtt.MqttClientWillOptions}.
 * NOTE: This class has been automatically generated from the {@link io.vertx.mqtt.MqttClientWillOptions} original class using Vert.x codegen.
 */
public class MqttClientWillOptionsConverter {


  private static final Base64.Decoder BASE64_DECODER = JsonUtil.BASE64_DECODER;
  private static final Base64.Encoder BASE64_ENCODER = JsonUtil.BASE64_ENCODER;

   static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, MqttClientWillOptions obj) {
    for (java.util.Map.Entry<String, Object> member : json) {
      switch (member.getKey()) {
        case "contentType":
          if (member.getValue() instanceof String) {
            obj.setContentType((String)member.getValue());
          }
          break;
        case "correlationData":
          if (member.getValue() instanceof String) {
            obj.setCorrelationData(io.vertx.core.buffer.Buffer.buffer(BASE64_DECODER.decode((String)member.getValue())));
          }
          break;
        case "messageBytes":
          if (member.getValue() instanceof String) {
            obj.setMessageBytes(io.vertx.core.buffer.Buffer.buffer(BASE64_DECODER.decode((String)member.getValue())));
          }
          break;
        case "payloadFormatIndicator":
          if (member.getValue() instanceof Number) {
            obj.setPayloadFormatIndicator(((Number)member.getValue()).intValue());
          }
          break;
        case "qos":
          if (member.getValue() instanceof Number) {
            obj.setQos(((Number)member.getValue()).intValue());
          }
          break;
        case "responseTopic":
          if (member.getValue() instanceof String) {
            obj.setResponseTopic((String)member.getValue());
          }
          break;
        case "retain":
          if (member.getValue() instanceof Boolean) {
            obj.setRetain((Boolean)member.getValue());
          }
          break;
        case "topic":
          if (member.getValue() instanceof String) {
            obj.setTopic((String)member.getValue());
          }
          break;
        case "userProperties":
          if (member.getValue() instanceof JsonObject) {
            java.util.Map<String, java.lang.String> map = new java.util.LinkedHashMap<>();
            ((Iterable<java.util.Map.Entry<String, Object>>)member.getValue()).forEach(entry -> {
              if (entry.getValue() instanceof String)
                map.put(entry.getKey(), (String)entry.getValue());
            });
            obj.setUserProperties(map);
          }
          break;
        case "userPropertys":
          if (member.getValue() instanceof JsonObject) {
            ((Iterable<java.util.Map.Entry<String, Object>>)member.getValue()).forEach(entry -> {
              if (entry.getValue() instanceof String)
                obj.addUserProperty(entry.getKey(), (String)entry.getValue());
            });
          }
          break;
        case "willDelayInterval":
          if (member.getValue() instanceof Number) {
            obj.setWillDelayInterval(((Number)member.getValue()).longValue());
          }
          break;
      }
    }
  }

   static void toJson(MqttClientWillOptions obj, JsonObject json) {
    toJson(obj, json.getMap());
  }

   static void toJson(MqttClientWillOptions obj, java.util.Map<String, Object> json) {
    if (obj.getContentType() != null) {
      json.put("contentType", obj.getContentType());
    }
    if (obj.getCorrelationData() != null) {
      json.put("correlationData", BASE64_ENCODER.encodeToString(obj.getCorrelationData().getBytes()));
    }
    if (obj.getMessageBytes() != null) {
      json.put("messageBytes", BASE64_ENCODER.encodeToString(obj.getMessageBytes().getBytes()));
    }
    if (obj.getPayloadFormatIndicator() != null) {
      json.put("payloadFormatIndicator", obj.getPayloadFormatIndicator());
    }
    json.put("qos", obj.getQos());
    if (obj.getResponseTopic() != null) {
      json.put("responseTopic", obj.getResponseTopic());
    }
    json.put("retain", obj.isRetain());
    if (obj.getTopic() != null) {
      json.put("topic", obj.getTopic());
    }
    if (obj.getUserProperties() != null) {
      JsonObject map = new JsonObject();
      obj.getUserProperties().forEach((key, value) -> map.put(key, value));
      json.put("userProperties", map);
    }
    if (obj.getWillDelayInterval() != null) {
      json.put("willDelayInterval", obj.getWillDelayInterval());
    }
  }
}
