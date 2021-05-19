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

package io.vertx.mqtt.session.impl;

import io.vertx.mqtt.session.SessionEvent;
import io.vertx.mqtt.session.SessionState;

/**
 * An event of a session state change.
 */
public class SessionEventImpl implements SessionEvent {

  private final SessionState sessionState;
  private final Throwable cause;

  public SessionEventImpl(final SessionState sessionState, final Throwable reason) {
    this.sessionState = sessionState;
    this.cause = reason;
  }

  /**
   * The new state of the session.
   *
   * @return The state.
   */
  @Override public SessionState getSessionState() {
    return this.sessionState;
  }

  /**
   * The (optional) cause of change.
   *
   * @return The throwable that causes the state change, or {@code null}, if there was none.
   */
  @Override public Throwable getCause() {
    return this.cause;
  }
}
