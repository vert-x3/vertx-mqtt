# Vert.x MQTT server

This component provides a server which is able to handle connections, communication and messages exchange with remote [MQTT](http://mqtt.org/) clients. Its API provides a bunch of events related to raw protocol messages received by clients and exposes some functionalities in order to send messages to them.

It's not a fully featured MQTT broker but can be used for building something like that or for protocol translation (MQTT <--> ?).

See the in-source docs for more details:
- [Java](src/main/asciidoc/java/index.adoc).

**Note: This module has Tech Preview status, this means the API can change between versions.**
