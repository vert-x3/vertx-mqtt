package io.vertx.mqtt.it;

import org.eclipse.paho.client.mqttv3.internal.ClientState;
import org.eclipse.paho.client.mqttv3.internal.CommsSender;
import org.eclipse.paho.client.mqttv3.logging.Logger;

import java.util.ResourceBundle;

public class CustomPahoLogger implements Logger {

  @Override
  public void initialise(ResourceBundle messageCatalog, String loggerID, String resourceName) {

  }

  @Override
  public void setResourceName(String logContext) {

  }

  @Override
  public boolean isLoggable(int level) {
    return false;
  }

  @Override
  public void severe(String sourceClass, String sourceMethod, String msg) {

  }

  @Override
  public void severe(String sourceClass, String sourceMethod, String msg, Object[] inserts) {

  }

  @Override
  public void severe(String sourceClass, String sourceMethod, String msg, Object[] inserts, Throwable thrown) {

  }

  @Override
  public void warning(String sourceClass, String sourceMethod, String msg) {

  }

  @Override
  public void warning(String sourceClass, String sourceMethod, String msg, Object[] inserts) {

  }

  @Override
  public void warning(String sourceClass, String sourceMethod, String msg, Object[] inserts, Throwable thrown) {

  }

  @Override
  public void info(String sourceClass, String sourceMethod, String msg) {

  }

  @Override
  public void info(String sourceClass, String sourceMethod, String msg, Object[] inserts) {

  }

  @Override
  public void info(String sourceClass, String sourceMethod, String msg, Object[] inserts, Throwable thrown) {

  }

  @Override
  public void config(String sourceClass, String sourceMethod, String msg) {

  }

  @Override
  public void config(String sourceClass, String sourceMethod, String msg, Object[] inserts) {

  }

  @Override
  public void config(String sourceClass, String sourceMethod, String msg, Object[] inserts, Throwable thrown) {

  }

  @Override
  public void fine(String sourceClass, String sourceMethod, String msg) {

  }

  @Override
  public void fine(String sourceClass, String sourceMethod, String msg, Object[] inserts) {

    if (sourceClass.equals(ClientState.class.getName())) {
      System.out.println(sourceClass + "." + sourceMethod);
    }


  }

  @Override
  public void fine(String sourceClass, String sourceMethod, String msg, Object[] inserts, Throwable ex) {

  }

  @Override
  public void finer(String sourceClass, String sourceMethod, String msg) {

  }

  @Override
  public void finer(String sourceClass, String sourceMethod, String msg, Object[] inserts) {

  }

  @Override
  public void finer(String sourceClass, String sourceMethod, String msg, Object[] inserts, Throwable ex) {

  }

  @Override
  public void finest(String sourceClass, String sourceMethod, String msg) {

  }

  @Override
  public void finest(String sourceClass, String sourceMethod, String msg, Object[] inserts) {

  }

  @Override
  public void finest(String sourceClass, String sourceMethod, String msg, Object[] inserts, Throwable ex) {

  }

  @Override
  public void log(int level, String sourceClass, String sourceMethod, String msg, Object[] inserts, Throwable thrown) {

  }

  @Override
  public void trace(int level, String sourceClass, String sourceMethod, String msg, Object[] inserts, Throwable ex) {

  }

  @Override
  public String formatMessage(String msg, Object[] inserts) {
    return null;
  }

  @Override
  public void dumpTrace() {

  }
}
