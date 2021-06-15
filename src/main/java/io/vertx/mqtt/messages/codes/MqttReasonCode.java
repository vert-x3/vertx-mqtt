package io.vertx.mqtt.messages.codes;

/**
 * Common interface for MQTT messages reason codes enums
 */
public interface MqttReasonCode {
    byte value();

    default boolean isError() {
        return (value() & 0x80) != 0;
    }

}
