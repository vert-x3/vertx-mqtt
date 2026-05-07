package io.vertx.mqtt.messages.codes;

public enum MqttAuthenticateReasonCode implements MqttReasonCode {

  SUCCESS((byte) 0x0),

  CONTINUE_AUTHENTICATION((byte) 0x18),

  RE_AUTHENTICATE((byte) 0x19);

  MqttAuthenticateReasonCode(byte byteValue) {
    this.byteValue = byteValue;
  }

  private final byte byteValue;

  @Override
  public byte value() {
    return byteValue;
  }

  public static MqttAuthenticateReasonCode valueOf(byte b) {
    for (MqttAuthenticateReasonCode code : MqttAuthenticateReasonCode.values()) {
      if (code.byteValue == b) {
        return code;
      }
    }
    throw new IllegalArgumentException("unknown AUTHENTICATE reason code: " + b);
  }
}
