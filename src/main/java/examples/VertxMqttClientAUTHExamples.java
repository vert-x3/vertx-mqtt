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

import static io.netty.handler.codec.mqtt.MqttProperties.MqttPropertyType.AUTHENTICATION_DATA;
import static io.netty.handler.codec.mqtt.MqttProperties.MqttPropertyType.AUTHENTICATION_METHOD;

import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;
import io.vertx.mqtt.messages.codes.MqttAuthenticateReasonCode;

import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * End-to-end demo of MQTT 5.0 Enhanced Authentication (§4.12) using
 * SCRAM-SHA-256 (RFC 5802) against an EMQX broker.
 *
 * Usage:
 *   java examples.VertxMqttClientAUTHExamples [host] [port] [username] [password]
 * Defaults: localhost 1883 user public
 */
public class VertxMqttClientAUTHExamples {

  private static final String AUTH_METHOD = "SCRAM-SHA-256";
  private static final String GS2_HEADER = "n,,";

  public static void main(String[] args) {

    final String host = args.length > 0 ? args[0] : "localhost";
    final int port = args.length > 1 ? Integer.parseInt(args[1]) : 1883;
    final String username = args.length > 2 ? args[2] : "user";
    final String password = args.length > 3 ? args[3] : "public";

    final String clientNonce = generateNonce();
    final String clientFirstMessageBare = "n=" + saslName(username) + ",r=" + clientNonce;
    final byte[] clientFirstMessage = (GS2_HEADER + clientFirstMessageBare).getBytes(StandardCharsets.UTF_8);

    MqttClientOptions option = new MqttClientOptions();
    option.setVersion(5);
    option.setAuthenticationMethod(AUTH_METHOD);
    option.setAuthenticationData(Buffer.buffer(clientFirstMessage));

    Vertx vertx = Vertx.vertx();
    MqttClient client = MqttClient.create(vertx, option);

    client.authenticationExchangeHandler(msg -> {
      try {
        String serverFirstMessage = new String(msg.authenticationData().getBytes(), StandardCharsets.UTF_8);
        Map<String, String> fields = parseScramMessage(serverFirstMessage);
        String serverNonce = fields.get("r");
        byte[] salt = Base64.getDecoder().decode(fields.get("s"));
        int iterations = Integer.parseInt(fields.get("i"));

        // RFC 5802 §5.1: server MUST extend the client nonce — fail closed otherwise.
        if (serverNonce == null || !serverNonce.startsWith(clientNonce)) {
          throw new SecurityException("Invalid server nonce");
        }

        String channelBinding = Base64.getEncoder().encodeToString(GS2_HEADER.getBytes(StandardCharsets.UTF_8));
        String clientFinalNoProof = "c=" + channelBinding + ",r=" + serverNonce;
        String authMessage = clientFirstMessageBare + "," + serverFirstMessage + "," + clientFinalNoProof;

        byte[] saltedPassword = pbkdf2(password, salt, iterations);
        byte[] clientKey = hmacSha256(saltedPassword, "Client Key".getBytes(StandardCharsets.UTF_8));
        byte[] storedKey = MessageDigest.getInstance("SHA-256").digest(clientKey);
        byte[] clientSignature = hmacSha256(storedKey, authMessage.getBytes(StandardCharsets.UTF_8));
        byte[] clientProof = xor(clientKey, clientSignature);

        String clientFinalMessage = clientFinalNoProof
          + ",p=" + Base64.getEncoder().encodeToString(clientProof);

        MqttProperties props = new MqttProperties();
        props.add(new MqttProperties.StringProperty(AUTHENTICATION_METHOD.value(), AUTH_METHOD));
        props.add(new MqttProperties.BinaryProperty(AUTHENTICATION_DATA.value(),
          clientFinalMessage.getBytes(StandardCharsets.UTF_8)));

        client.authenticationExchange(MqttAuthenticateReasonCode.CONTINUE_AUTHENTICATION, props);
      } catch (Exception e) {
        e.printStackTrace();
      }
    });

    client.connect(port, host).onComplete(ar -> {
      if (ar.succeeded()) {
        System.out.println("Connected & authenticated as '" + username + "'");
        client.publish("temperature", Buffer.buffer("hello"), MqttQoS.AT_LEAST_ONCE, false, false)
          .onComplete(p -> client.disconnect().onComplete(d -> vertx.close()));
      } else {
        System.err.println("Connect failed: " + ar.cause());
        vertx.close();
      }
    });
  }

  private static String generateNonce() {
    byte[] bytes = new byte[24];
    new SecureRandom().nextBytes(bytes);
    // RFC 5802 §5.1: nonce is "printable" — strip base64 chars disallowed inside SCRAM attrs.
    return Base64.getEncoder().encodeToString(bytes).replace("=", "").replace(",", "");
  }

  private static String saslName(String name) {
    return name.replace("=", "=3D").replace(",", "=2C");
  }

  private static Map<String, String> parseScramMessage(String msg) {
    Map<String, String> out = new HashMap<>();
    for (String token : msg.split(",")) {
      int eq = token.indexOf('=');
      if (eq > 0) {
        out.put(token.substring(0, eq), token.substring(eq + 1));
      }
    }
    return out;
  }

  private static byte[] pbkdf2(String password, byte[] salt, int iterations) throws Exception {
    SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
    PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, 256);
    try {
      return skf.generateSecret(spec).getEncoded();
    } finally {
      spec.clearPassword();
    }
  }

  private static byte[] hmacSha256(byte[] key, byte[] data) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(key, "HmacSHA256"));
    return mac.doFinal(data);
  }

  private static byte[] xor(byte[] a, byte[] b) {
    byte[] out = new byte[a.length];
    for (int i = 0; i < a.length; i++) {
      out[i] = (byte) (a[i] ^ b[i]);
    }
    return out;
  }
}
