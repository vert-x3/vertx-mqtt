package io.vertx.mqtt.messages.codes;

public enum MqttPubCompReasonCode implements MqttReasonCode {
    SUCCESS((byte)0x0),
    PACKET_IDENTIFIER_NOT_FOUND((byte)0x92);

    MqttPubCompReasonCode(byte byteValue) {
        this.byteValue = byteValue;
    }

    private final byte byteValue;

    @Override
    public byte value() {
        return byteValue;
    }

    public static MqttPubCompReasonCode valueOf(byte b) {
        if(b == SUCCESS.byteValue) {
            return SUCCESS;
        } else if(b == PACKET_IDENTIFIER_NOT_FOUND.byteValue) {
            return PACKET_IDENTIFIER_NOT_FOUND;
        } else {
            throw new IllegalArgumentException("unknown PUBCOMP reason code: " + b);
        }
    }
}
