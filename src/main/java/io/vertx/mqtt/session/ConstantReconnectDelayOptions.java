/*
 * Copyright 2021 Red Hat Inc.
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

package io.vertx.mqtt.session;

import java.time.Duration;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.Fluent;
import io.vertx.core.json.JsonObject;

@DataObject(generateConverter = true)
public class ConstantReconnectDelayOptions implements ReconnectDelayOptions {

  private static final Duration DEFAULT_DELAY = Duration.ofSeconds(10);

  private Duration delay = DEFAULT_DELAY;

  public ConstantReconnectDelayOptions() {
  }

  /**
   * Create an instance of ConstantReconnectDelayOptions from JSON
   *
   * @param json the JSON
   */
  public ConstantReconnectDelayOptions(JsonObject json) {
    ConstantReconnectDelayOptionsConverter.fromJson(json, this);
  }

  @Fluent
  public ConstantReconnectDelayOptions setDelay(Duration delay) {
    this.delay = delay;
    return this;
  }

  public Duration getDelay() {
    return this.delay;
  }

  @Override
  public ReconnectDelayProvider createProvider() {

    final Duration delay = this.delay;

    return new ReconnectDelayProvider() {

      @Override
      public Duration nextDelay() {
        return delay;
      }

      @Override
      public void reset() {
        // no-op
      }
    };

  }

  @Override
  public ReconnectDelayOptions copy() {
    ConstantReconnectDelayOptions result = new ConstantReconnectDelayOptions();
    result.delay = this.delay;
    return result;
  }
}
