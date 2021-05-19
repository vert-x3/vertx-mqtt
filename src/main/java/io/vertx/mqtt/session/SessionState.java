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

/**
 * The state of the session.
 */
public enum SessionState {
  /**
   * The session is disconnected.
   * <p>
   * A re-connect timer may be pending.
   */
  DISCONNECTED,
  /**
   * The session started to connect.
   * <p>
   * This may include re-subscribing to any topics after the connect call was successful.
   */
  CONNECTING,
  /**
   * The session is connected.
   */
  CONNECTED,
  /**
   * The session is in the process of an orderly disconnect.
   */
  DISCONNECTING,
}
