package io.vertx.mqtt.messages.codes;

/**
 * Utilities for MQTT message codes enums
 */
public class ReasonCodeUtils {
    static <C extends MqttReasonCode> void fillValuesByCode(C[] valuesByCode, C[] values) {
        for (C code : values) {
            final int unsignedByte = code.value() & 0xFF;
            valuesByCode[unsignedByte] = code;
        }
    }

    static <C> C codeLoopkup(C[] valuesByCode, byte b, String codeType) {
        final int unsignedByte = b & 0xFF;
        C reasonCode = null;
        try {
            reasonCode = valuesByCode[unsignedByte];
        } catch (ArrayIndexOutOfBoundsException ignored) {
            // no op
        }
        if (reasonCode == null) {
            throw new IllegalArgumentException("unknown " + codeType + " reason code: " + unsignedByte);
        }
        return reasonCode;
    }

}
