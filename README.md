# Vert.x MQTT server

This component provides a server which is able to handle connections and communication with remote [MQTT](http://mqtt.org/) clients. It raises a bunch of events related to raw messages protocol received by clients and exposes some functionalities in order to send messages to them.

It's not a fully featured MQTT broker but can be used for building something like that or for protocol translation (MQTT <-> ?).
