package io.vertx.mqtt.messages.codes;

/**
 * Reason codes for PUBREC MQTT message
 */
public enum MqttPubRecReasonCode implements MqttReasonCode {
    SUCCESS((byte)0x0),
    NO_MATCHING_SUBSCRIBERS((byte)0x10),
    UNSPECIFIED_ERROR((byte)0x80),
    IMPLEMENTATION_SPECIFIC_ERROR((byte)0x83),
    NOT_AUTHORIZED((byte)0x87),
    TOPIC_NAME_INVALID((byte)0x90),
    PACKET_IDENTIFIER_IN_USE((byte)0x91),
    QUOTA_EXCEEDED((byte)0x97),
    PAYLOAD_FORMAT_INVALID((byte)0x99);

    MqttPubRecReasonCode(byte byteValue) {
        this.byteValue = byteValue;
    }

    private final byte byteValue;

    @Override
    public byte value() {
        return byteValue;
    }

    public boolean isError() {
        return Byte.toUnsignedInt(byteValue) >= Byte.toUnsignedInt(UNSPECIFIED_ERROR.byteValue);
    }

    private static final MqttPubRecReasonCode[] VALUES = new MqttPubRecReasonCode[0x9A];

    static {
        ReasonCodeUtils.fillValuesByCode(VALUES, values());
    }

    public static MqttPubRecReasonCode valueOf(byte b) {
        return ReasonCodeUtils.codeLoopkup(VALUES, b, "PUBREC");
    }

}
