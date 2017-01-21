[![Build Status](https://vertx.ci.cloudbees.com/buildStatus/icon?job=vert.x3-mqtt-server)](https://vertx.ci.cloudbees.com/view/vert.x-3/job/vert.x3-mqtt-server/)

# Vert.x MQTT server

This component provides a server which is able to handle connections, communication and messages exchange with remote [MQTT](http://mqtt.org/) clients. Its API provides a bunch of events related to raw protocol messages received by clients and exposes some functionalities in order to send messages to them.

It's not a fully featured MQTT broker but can be used for building something like that or for protocol translation (MQTT <--> ?).

See the in-source docs for more details:
- [Java](src/main/asciidoc/java/index.adoc).
- [JavaScript](src/main/asciidoc/js/index.adoc).
- [Ruby](src/main/asciidoc/ruby/index.adoc).

Some examples are available for getting started with the server under the [_examples_](src/main/java/io/vertx/mqtt/examples) folder.

**Note: This module has Tech Preview status, this means the API can change between versions.**
