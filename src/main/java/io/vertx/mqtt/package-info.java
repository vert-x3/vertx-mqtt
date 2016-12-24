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
 *
 * === Handling client subscription/unsubscription request
 *
 * After a connection is established between client and server, the client can send a subscription request for a topic
 * using the SUBSCRIBE message. The {@link io.vertx.mqtt.MqttEndpoint} class allows to specify an handler for the
 * incoming subscription request using the {@link io.vertx.mqtt.MqttEndpoint#subscribeHandler(io.vertx.core.Handler)} method.
 * Such handler receives an instance of the {@link io.vertx.mqtt.messages.MqttSubscribeMessage} class which brings
 * the list of topics with related QoS levels as desired by the client.
 * Finally, the endpoint instance provides the {@link io.vertx.mqtt.MqttEndpoint#subscribeAcknowledge(int, java.util.List)} method
 * for replying to the client with the related SUBACK message containing the granted QoS levels.
 *
 * [source,$lang]
 * ----
 * {@link examples.VertxMqttServerExamples#example3}
 * ----
 *
 * In the same way, it's possible to use the {@link io.vertx.mqtt.MqttEndpoint#unsubscribeHandler(io.vertx.core.Handler)} method
 * on the endpoint in order to specify the handler called when the client sends an UNSUBSCRIBE message. This handler receives
 * an instance of the {@link io.vertx.mqtt.messages.MqttUnsubscribeMessage} class as parameter with the list of topics to unsubscribe.
 * Finally, the endpoint instance provides the {@link io.vertx.mqtt.MqttEndpoint#unsubscribeAcknowledge(int)} method
 * for replying to the client with the related UNSUBACK message.
 *
 * [source,$lang]
 * ----
 * {@link examples.VertxMqttServerExamples#example4}
 * ----
 *
 * === Handling client published message
 *
 * In order to handle incoming messages published by the remote client, the {@link io.vertx.mqtt.MqttEndpoint} class provides
 * the {@link io.vertx.mqtt.MqttEndpoint#publishHandler(io.vertx.core.Handler)} method for specifying the handler called
 * when the client sends a PUBLISH message. This handler receives an instance of the {@link io.vertx.mqtt.messages.MqttPublishMessage}
 * class as parameter with the payload, the QoS level, the duplicate and retain flags.
 *
 * If the QoS level is 0 (AT_MOST_ONCE), there is no need from the endpoint to reply the client.
 *
 * If the QoS level is 1 (AT_LEAST_ONCE), the endpoind needs to reply with a PUBACK message using the
 * available {@link io.vertx.mqtt.MqttEndpoint#publishAcknowledge(int)} method.
 *
 * If the QoS level is 2 (EXACTLY_ONCE), the endpoint needs to reply with a PUBREC message using the
 * available {@link io.vertx.mqtt.MqttEndpoint#publishReceived(int)} method; in this case the same endpoint should handle
 * the PUBREL message received from the client as well (the remote client sends it after receiving the PUBREC from the endpoint)
 * and it can do that specifying the handler through the {@link io.vertx.mqtt.MqttEndpoint#publishReleaseHandler(io.vertx.core.Handler)} method.
 * In order to close the QoS level 2 delivery, the endpoint can use the {@link io.vertx.mqtt.MqttEndpoint#publishComplete(int)} method
 * for sending the PUBCOMP message to the client.
 *
 * [source,$lang]
 * ----
 * {@link examples.VertxMqttServerExamples#example5}
 * ----
 */
@Document(fileName = "index.adoc")
@ModuleGen(name = "vertx-mqtt-server", groupPackage = "io.vertx")
package io.vertx.mqtt;

import io.vertx.codegen.annotations.ModuleGen;
import io.vertx.docgen.Document;
