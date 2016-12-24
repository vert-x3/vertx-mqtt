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

/**
 * = Vert.x MQTT server
 *
 * This component provides a server which is able to handle connections, communication and messages exchange with remote
 * link:http://mqtt.org/[MQTT] clients. Its API provides a bunch of events related to raw protocol messages received by
 * clients and exposes some features in order to send messages to them.
 *
 * It's not a fully featured MQTT broker but can be used for building something like that or for protocol translation.
 *
 * WARNING: this module has the tech preview status, this means the API can change between versions.
 *
 * == Using Vert.x MQTT server
 *
 * As component not yet officially released in the Vert.x stack, to use the Vert.x MQTT server current snapshot version,
 * add the following repository under the _repositories_ section and the following dependency to the _dependencies_ section
 * of your build descriptor:
 *
 * * Maven (in your `pom.xml`):
 *
 * [source,xml,subs="+attributes"]
 * ----
 * <repository>
 *     <id>oss.sonatype.org-snapshot</id>
 *     <url>https://oss.sonatype.org/content/repositories/snapshots</url>
 * </repository>
 * ----
 *
 * [source,xml,subs="+attributes"]
 * ----
 * <dependency>
 *     <groupId>io.vertx</groupId>
 *     <artifactId>vertx-mqtt-server</artifactId>
 *     <version>3.4.0-SNAPSHOT</version>
 * </dependency>
 * ----
 *
 * * Gradle (in your `build.gradle` file):
 *
 * [source,groovy,subs="+attributes"]
 * ----
 * maven { url "https://oss.sonatype.org/content/repositories/snapshots" }
 * ----
 *
 * [source,groovy,subs="+attributes"]
 * ----
 * compile io.vertx:vertx-mqtt-server:3.4.0-SNAPSHOT
 * ----
 *
 * == Getting Started
 *
 * === Handling client connection/disconnection
 *
 * This example shows how it's possible to handle the connection request from a remote MQTT client. First, an
 * {@link io.vertx.mqtt.MqttServer} instance is created and the {@link io.vertx.mqtt.MqttServer#endpointHandler(io.vertx.core.Handler)} method is used to specify the handler called
 * when a remote client sends a CONNECT message for connecting to the server itself. The {@link io.vertx.mqtt.MqttEndpoint}
 * instance, provided as parameter to the handler, brings all main information related to the CONNECT message like client identifier,
 * username/password, "will" information, clean session flag, protocol version and the "keep alive" timeout.
 * Inside that handler, the _endpoint_ instance provides the {@link io.vertx.mqtt.MqttEndpoint#accept(boolean)} method
 * for replying to the remote client with the corresponding CONNACK message : in this way, the connection is established.
 * Finally, the server is started using the {@link io.vertx.mqtt.MqttServer#listen(io.vertx.core.Handler)} method with
 * the default behavior (on localhost and default MQTT port 1883). The same method allows to specify an handler in order
 * to check if the server is started properly or not.
 *
 * [source,$lang]
 * ----
 * {@link examples.VertxMqttServerExamples#example1}
 * ----
 *
 * The same _endpoint_ instance provides the {@link io.vertx.mqtt.MqttEndpoint#disconnectHandler(io.vertx.core.Handler)}
 * for specifying the handler called when the remote client sends a DISCONNECT message in order to disconnect from the server;
 * this handler takes no parameters.
 *
 * [source,$lang]
 * ----
 * {@link examples.VertxMqttServerExamples#example2}
 * ----
 */
@Document(fileName = "index.adoc")
@ModuleGen(name = "vertx-mqtt-server", groupPackage = "io.vertx")
package io.vertx.mqtt;

import io.vertx.codegen.annotations.ModuleGen;
import io.vertx.docgen.Document;
