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
module io.vertx.mqtt {

  requires static io.vertx.docgen;
  requires static io.vertx.codegen.api;
  requires static io.vertx.codegen.json;

  requires io.netty.common;
  requires io.netty.buffer;
  requires io.netty.handler;
  requires io.netty.transport;
  requires io.netty.codec;
  requires io.netty.codec.mqtt;
  requires io.netty.codec.compression;
  requires io.netty.codec.http;
  requires io.vertx.core;
  requires io.vertx.core.logging;

  exports io.vertx.mqtt;
  exports io.vertx.mqtt.messages;
  exports io.vertx.mqtt.messages.codes;

}
