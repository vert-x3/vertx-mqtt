/*
 * Copyright 2024 Red Hat Inc.
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
open module io.vertx.tests.mqtt {
  requires io.netty.common;
  requires io.netty.buffer;
  requires io.netty.handler;
  requires io.netty.transport;
  requires io.netty.codec.mqtt;
  requires io.netty.codec.compression;
  requires io.netty.codec;
  requires io.vertx.core.logging;
  requires io.vertx.core;
  requires io.vertx.mqtt;
  requires io.vertx.testing.unit;
  requires io.vertx.core.tests;
  requires junit;
  requires org.eclipse.paho.client.mqttv3;
  requires org.eclipse.paho.mqttv5.client;
  requires testcontainers;
}
