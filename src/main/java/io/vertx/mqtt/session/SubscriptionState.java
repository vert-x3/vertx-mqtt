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
 * The state of a subscription.
 * <p>
 * Subscriptions established when a new topic gets added, or the connection was established. If the subscribe call
 * returns an error for the subscription, the state will remain {@link #FAILED} and it will not try to re-subscribe
 * while the connection is active.
 * <p>
 * When the session (connection) disconnects, all subscriptions will automatically be reset to {@link #UNSUBSCRIBED}.
 */
public enum SubscriptionState {
  /**
   * The topic is not subscribed.
   */
  UNSUBSCRIBED,
  /**
   * The topic is in the process of subscribing.
   */
  SUBSCRIBING,
  /**
   * The topic is subscribed.
   */
  SUBSCRIBED,
  /**
   * The topic could not be subscribed.
   */
  FAILED,
}
