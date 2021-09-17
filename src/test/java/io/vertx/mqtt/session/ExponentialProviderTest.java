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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.time.Duration;

import org.junit.Test;

public class ExponentialProviderTest {

  @Test
  public void testDefaults() {
    ExponentialBackoffDelayOptions opts = new ExponentialBackoffDelayOptions();
    ReconnectDelayProvider provider = opts.createProvider();
    assertNotNull(provider);
  }

  @Test(expected = Throwable.class)
  public void testMinGreaterThanMax() {
    ExponentialBackoffDelayOptions opts = new ExponentialBackoffDelayOptions();
    opts.setMinimum(Duration.ofSeconds(10));
    opts.setMaximum(Duration.ofSeconds(5));
    opts.createProvider();
  }

  @Test(expected = Throwable.class)
  public void testNegativeMin() {
    ExponentialBackoffDelayOptions opts = new ExponentialBackoffDelayOptions();
    opts.setMinimum(Duration.ofSeconds(-10));
    opts.createProvider();
  }

  @Test(expected = Throwable.class)
  public void testNegativeInc() {
    ExponentialBackoffDelayOptions opts = new ExponentialBackoffDelayOptions();
    opts.setIncrement(Duration.ofSeconds(-10));
    opts.createProvider();
  }

  @Test(expected = Throwable.class)
  public void testNegativeMax() {
    ExponentialBackoffDelayOptions opts = new ExponentialBackoffDelayOptions();
    opts.setIncrement(Duration.ofSeconds(-10));
    opts.createProvider();
  }

  @Test
  public void testMaximumDefault() {
    ExponentialBackoffDelayOptions opts = new ExponentialBackoffDelayOptions();
    ReconnectDelayProvider provider = opts.createProvider();
    assertEquals(Duration.ofSeconds(1+0), provider.nextDelay());
    assertEquals(Duration.ofSeconds(1+1), provider.nextDelay());
    assertEquals(Duration.ofSeconds(1+2), provider.nextDelay());
    assertEquals(Duration.ofSeconds(1+4), provider.nextDelay());
    assertEquals(Duration.ofSeconds(1+8), provider.nextDelay());
    assertEquals(Duration.ofSeconds(1+16), provider.nextDelay());
    assertEquals(Duration.ofSeconds(1+32), provider.nextDelay());
    assertEquals(Duration.ofSeconds(1+64), provider.nextDelay());
    assertEquals(Duration.ofSeconds(1+128), provider.nextDelay());
    assertEquals(Duration.ofSeconds(1+256), provider.nextDelay());
    assertEquals(Duration.ofSeconds(300), provider.nextDelay());
    assertEquals(Duration.ofSeconds(300), provider.nextDelay());
    assertEquals(Duration.ofSeconds(300), provider.nextDelay());
  }

  @Test
  public void testMaximumZeroInitial() {
    ExponentialBackoffDelayOptions opts = new ExponentialBackoffDelayOptions();
    opts.setMinimum(Duration.ZERO);
    ReconnectDelayProvider provider = opts.createProvider();

    assertEquals(Duration.ofSeconds(0), provider.nextDelay());
    assertEquals(Duration.ofSeconds(1), provider.nextDelay());
    assertEquals(Duration.ofSeconds(2), provider.nextDelay());
    assertEquals(Duration.ofSeconds(4), provider.nextDelay());
    assertEquals(Duration.ofSeconds(8), provider.nextDelay());
    assertEquals(Duration.ofSeconds(16), provider.nextDelay());
    assertEquals(Duration.ofSeconds(32), provider.nextDelay());
    assertEquals(Duration.ofSeconds(64), provider.nextDelay());
    assertEquals(Duration.ofSeconds(128), provider.nextDelay());
    assertEquals(Duration.ofSeconds(256), provider.nextDelay());
    assertEquals(Duration.ofSeconds(300), provider.nextDelay());
    assertEquals(Duration.ofSeconds(300), provider.nextDelay());
    assertEquals(Duration.ofSeconds(300), provider.nextDelay());
  }

  @Test
  public void testMaximumIncreased() {
    ExponentialBackoffDelayOptions opts = new ExponentialBackoffDelayOptions();
    opts.setMinimum(Duration.ZERO);
    opts.setIncrement(Duration.ofSeconds(4));
    ReconnectDelayProvider provider = opts.createProvider();

    assertEquals(Duration.ofSeconds(0), provider.nextDelay());
    assertEquals(Duration.ofSeconds(4), provider.nextDelay());
    assertEquals(Duration.ofSeconds(8), provider.nextDelay());
    assertEquals(Duration.ofSeconds(16), provider.nextDelay());
    assertEquals(Duration.ofSeconds(32), provider.nextDelay());
    assertEquals(Duration.ofSeconds(64), provider.nextDelay());
    assertEquals(Duration.ofSeconds(128), provider.nextDelay());
    assertEquals(Duration.ofSeconds(256), provider.nextDelay());
    assertEquals(Duration.ofSeconds(300), provider.nextDelay());
    assertEquals(Duration.ofSeconds(300), provider.nextDelay());
    assertEquals(Duration.ofSeconds(300), provider.nextDelay());
  }
}
