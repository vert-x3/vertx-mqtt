package io.vertx.mqtt.test.server;

import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.packet.MqttAuth;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;

import java.io.IOException;

/**
 * Cause the bug of paho mqtt client. It's a workaround solution.
 * See also: <a href="https://github.com/eclipse/paho.mqtt.java/issues/980">https://github.com/eclipse/paho.mqtt.java/issues/980</a>
 */
public class MqttAuthPacket extends MqttAuth {

  public MqttAuthPacket(int returnCode, MqttProperties properties) throws MqttException {
    super(returnCode, properties);
  }

  @Override
  protected byte getMessageInfo() {
    return 0;
  }
}
