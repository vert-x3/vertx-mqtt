package io.vertx.mqtt.messages.codes;

/**
 * Reason codes for PUBREL MQTT message
 */
public enum MqttPubRelReasonCode implements MqttReasonCode {
    SUCCESS((byte)0x0),
    PACKET_IDENTIFIER_NOT_FOUND((byte)0x92);

    MqttPubRelReasonCode(byte byteValue) {
        this.byteValue = byteValue;
    }

    private final byte byteValue;

    @Override
    public byte value() {
        return byteValue;
    }

    public static MqttPubRelReasonCode valueOf(byte b) {
        if(b == SUCCESS.byteValue) {
            return SUCCESS;
        } else if(b == PACKET_IDENTIFIER_NOT_FOUND.byteValue) {
            return PACKET_IDENTIFIER_NOT_FOUND;
        } else {
            throw new IllegalArgumentException("unknown PUBREL reason code: " + b);
        }
    }
}
