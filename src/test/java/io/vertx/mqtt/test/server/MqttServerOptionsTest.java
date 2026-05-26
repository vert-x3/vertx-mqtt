/*
 * Copyright 2026 Red Hat Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.vertx.mqtt.test.server;

import io.vertx.mqtt.MqttServerOptions;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MqttServerOptionsTest {

  @Test
  public void testCopyWebSocketNoContextOptions() {
    boolean allowServerNoContext = false;
    boolean preferredClientNoContext = true;
    MqttServerOptions options = new MqttServerOptions()
      .setWebSocketAllowServerNoContext(allowServerNoContext)
      .setWebSocketPreferredClientNoContext(preferredClientNoContext);

    MqttServerOptions copy = new MqttServerOptions(options);

    assertEquals(allowServerNoContext, copy.isWebSocketAllowServerNoContext());
    assertEquals(preferredClientNoContext, copy.isWebSocketPreferredClientNoContext());

    boolean otherAllowServerNoContext = true;
    boolean otherPreferredClientNoContext = false;
    MqttServerOptions otherOptions = new MqttServerOptions()
      .setWebSocketAllowServerNoContext(otherAllowServerNoContext)
      .setWebSocketPreferredClientNoContext(otherPreferredClientNoContext);

    MqttServerOptions otherCopy = new MqttServerOptions(otherOptions);

    assertEquals(otherAllowServerNoContext, otherCopy.isWebSocketAllowServerNoContext());
    assertEquals(otherPreferredClientNoContext, otherCopy.isWebSocketPreferredClientNoContext());
  }
}
