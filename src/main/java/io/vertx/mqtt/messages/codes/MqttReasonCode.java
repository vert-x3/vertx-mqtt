package io.vertx.mqtt.messages.codes;

public interface MqttReasonCode {
    byte value();

    default boolean isError() {
        return (value() & 0x80) != 0;
    }

}
