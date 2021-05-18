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

package io.vertx.mqtt.reconnect;

import java.time.Duration;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.Fluent;
import io.vertx.core.json.JsonObject;

@DataObject(generateConverter = true)
public class ExponentialBackoffDelayOptions implements ReconnectDelayOptions {

  private static final Duration DEFAULT_MINIMUM = Duration.ofSeconds(1);
  private static final Duration DEFAULT_INCREMENT = Duration.ofSeconds(1);
  private static final Duration DEFAULT_MAXIMUM = Duration.ofMinutes(5);

  private Duration minimum = DEFAULT_MINIMUM;
  private Duration increment = DEFAULT_INCREMENT;
  private Duration maximum = DEFAULT_MAXIMUM;

  public ExponentialBackoffDelayOptions() {
  }

  /**
   * Create an instance of ConstantReconnectDelayOptions from JSON
   *
   * @param json the JSON
   */
  public ExponentialBackoffDelayOptions(JsonObject json) {
    ExponentialBackoffDelayOptionsConverter.fromJson(json, this);
  }

  @Fluent
  public ExponentialBackoffDelayOptions setIncrement(Duration increment) {
    this.increment = increment;
    return this;
  }

  public Duration getIncrement() {
    return this.increment;
  }

  @Fluent
  public ExponentialBackoffDelayOptions setMaximum(Duration maximum) {
    this.maximum = maximum;
    return this;
  }

  public Duration getMaximum() {
    return this.maximum;
  }

  @Fluent
  public ExponentialBackoffDelayOptions setMinimum(Duration minimum) {
    this.minimum = minimum;
    return this;
  }

  public Duration getMinimum() {
    return this.minimum;
  }

  private void validate() {
    if (this.minimum.isNegative() ) {
      throw new IllegalArgumentException("'minimum' must be a positive or zero duration");
    }
    if (this.increment.isNegative() || this.increment.isZero()) {
      throw new IllegalArgumentException("'increment' must be a positive duration");
    }
    if (this.maximum.isNegative() || this.maximum.isZero()) {
      throw new IllegalArgumentException("'maximum' must be a positive duration");
    }
    if (this.maximum.compareTo(this.minimum) < 0) {
      throw new IllegalArgumentException("'minimum' must be less than (or equal) to the maximum");
    }
  }

  @Override
  public ReconnectDelayProvider createProvider() {
    validate();

    long num = this.maximum.minus(this.minimum).toMillis() / this.increment.toMillis();
    long max = (long)(Math.log(num) / Math.log(2))+1;

    return new Provider(this.minimum, this.increment, this.maximum, max);
  }

  @Override
  public ReconnectDelayOptions copy() {
    ExponentialBackoffDelayOptions result = new ExponentialBackoffDelayOptions();
    result.minimum = this.minimum;
    result.increment = this.increment;
    result.maximum = this.maximum;
    return result;
  }

  private static class Provider implements ReconnectDelayProvider {

    private final Duration minimum;
    private final Duration increment;
    private final Duration maximum;
    private final long max;

    private long count;

    Provider(Duration minimum, Duration increment, Duration maximum, long max) {
      this.minimum = minimum;
      this.increment = increment;
      this.maximum = maximum;
      this.max = max;
    }

    @Override
    public Duration nextDelay() {

      if (this.count <= this.max) {

        Duration delay = this.minimum;
        if (this.count > 0) {
          delay = delay.plus(this.increment.multipliedBy((long)Math.pow(2, this.count-1)));
        }

        this.count += 1;

        return delay;
      } else {
        return this.maximum;
      }

    }

    @Override
    public void reset() {
      this.count = 0;
    }
  }
}
