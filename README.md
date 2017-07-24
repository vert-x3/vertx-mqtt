[![Build Status](https://vertx.ci.cloudbees.com/buildStatus/icon?job=vert.x3-mqtt-server)](https://vertx.ci.cloudbees.com/view/vert.x-3/job/vert.x3-mqtt-server/)

# Vert.x MQTT

This project provides the following two different components :

* **server** : it's able to handle connections, communication and messages exchange with remote [MQTT](http://mqtt.org/) clients. 
Its API provides a bunch of events related to raw protocol messages received by clients and exposes some functionalities in order to send messages to them.
It's not a fully featured MQTT broker but can be used for building something like that or for protocol translation (MQTT <--> ?).
* **client** : it's an [MQTT](http://mqtt.org/) client which is compliant with the 3.1.1 spec. Its API provides a bunch of methods 
for connecting/disconnecting to a broker, publishing messages (with all three different levels of QoS) and subscribing to topics.

See the in-source docs for more details:
- [Java](src/main/asciidoc/java/index.adoc).
- [JavaScript](src/main/asciidoc/js/index.adoc).
- [Ruby](src/main/asciidoc/ruby/index.adoc).
- [Groovy](src/main/asciidoc/groovy/index.adoc).
- [Kotlin](src/main/asciidoc/kotlin/index.adoc).

Some examples are available for getting started with the server under the [vertx-examples](https://github.com/vert-x3/vertx-examples/tree/master/mqtt-server-examples) project.

**Note: This module has Tech Preview status, this means the API can change between versions.**
