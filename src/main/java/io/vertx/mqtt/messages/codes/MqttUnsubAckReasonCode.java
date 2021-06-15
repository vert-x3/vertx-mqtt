package io.vertx.mqtt.messages.codes;

/**
 * Reason codes for UNSUBACK MQTT message
 */
public enum MqttUnsubAckReasonCode {
    SUCCESS((byte)0x0),
    NO_SUBSCRIPTION_EXISTED((byte)0x11),
    UNSPECIFIED_ERROR((byte)0x80),
    IMPLEMENTATION_SPECIFIC_ERROR((byte)0x83),
    NOT_AUTHORIZED((byte)0x87),
    TOPIC_FILTER_INVALID((byte)0x8F),
    PACKET_IDENTIFIER_IN_USE((byte)0x91);

    MqttUnsubAckReasonCode(byte byteValue) {
        this.byteValue = byteValue;
    }

    private final byte byteValue;

    public byte value() {
        return byteValue;
    }

}
