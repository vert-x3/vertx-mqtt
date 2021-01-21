/*
 * Copyright 2016 Red Hat Inc.
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

package io.vertx.mqtt.it;

import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.Before;
import org.junit.Rule;
import org.junit.runner.RunWith;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * MQTT client testing about connection
 */
@RunWith(VertxUnitRunner.class)
public abstract class MqttClientBaseIT {

  @Rule
  public GenericContainer redis = new GenericContainer(DockerImageName.parse("ansi/mosquitto"))
    .withExposedPorts(1883);

  protected int port;
  protected String host;

  @Before
  public void setUp() {
    port = redis.getMappedPort(1883);
    host = redis.getHost();
  }
}
