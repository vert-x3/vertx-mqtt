package io.vertx.mqtt.messages.codes;

/**
 * Reason codes for PUBACK MQTT message
 */
public enum MqttPubAckReasonCode implements MqttReasonCode {
    SUCCESS((byte)0x0),
    NO_MATCHING_SUBSCRIBERS((byte)0x10),
    UNSPECIFIED_ERROR((byte)0x80),
    IMPLEMENTATION_SPECIFIC_ERROR((byte)0x83),
    NOT_AUTHORIZED((byte)0x87),
    TOPIC_NAME_INVALID((byte)0x90),
    PACKET_IDENTIFIER_IN_USE((byte)0x91),
    QUOTA_EXCEEDED((byte)0x97),
    PAYLOAD_FORMAT_INVALID((byte)0x99);

    MqttPubAckReasonCode(byte byteValue) {
        this.byteValue = byteValue;
    }

    private final byte byteValue;

    private static final MqttPubAckReasonCode[] VALUES = new MqttPubAckReasonCode[0x9A];

    static {
        ReasonCodeUtils.fillValuesByCode(VALUES, values());
    }

    public static MqttPubAckReasonCode valueOf(byte b) {
        return ReasonCodeUtils.codeLoopkup(VALUES, b, "PUBACK");
    }

    @Override
    public byte value() {
        return byteValue;
    }

    public boolean isError() {
        return (byteValue & 0x80) != 0;
    }

}
