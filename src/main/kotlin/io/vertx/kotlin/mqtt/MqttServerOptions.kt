package io.vertx.kotlin.mqtt

import io.vertx.mqtt.MqttServerOptions
import io.vertx.core.http.ClientAuth
import io.vertx.core.net.JdkSSLEngineOptions
import io.vertx.core.net.JksOptions
import io.vertx.core.net.OpenSSLEngineOptions
import io.vertx.core.net.PemKeyCertOptions
import io.vertx.core.net.PemTrustOptions
import io.vertx.core.net.PfxOptions

/**
 * A function providing a DSL for building [io.vertx.mqtt.MqttServerOptions] objects.
 *
 * Represents options used by the MQTT server
 *
 * @param acceptBacklog 
 * @param clientAuth 
 * @param clientAuthRequired 
 * @param crlPaths 
 * @param crlValues 
 * @param enabledCipherSuites 
 * @param enabledSecureTransportProtocols 
 * @param host 
 * @param idleTimeout 
 * @param jdkSslEngineOptions 
 * @param keyStoreOptions 
 * @param logActivity 
 * @param maxMessageSize  Set max MQTT message size
 * @param openSslEngineOptions 
 * @param pemKeyCertOptions 
 * @param pemTrustOptions 
 * @param pfxKeyCertOptions 
 * @param pfxTrustOptions 
 * @param port 
 * @param receiveBufferSize 
 * @param reuseAddress 
 * @param sendBufferSize 
 * @param soLinger 
 * @param ssl 
 * @param tcpKeepAlive 
 * @param tcpNoDelay 
 * @param trafficClass 
 * @param trustStoreOptions 
 * @param useAlpn 
 * @param usePooledBuffers 
 *
 * <p/>
 * NOTE: This function has been automatically generated from the [io.vertx.mqtt.MqttServerOptions original] using Vert.x codegen.
 */
fun MqttServerOptions(
  acceptBacklog: Int? = null,
  clientAuth: ClientAuth? = null,
  clientAuthRequired: Boolean? = null,
  crlPaths: Iterable<String>? = null,
  crlValues: Iterable<io.vertx.core.buffer.Buffer>? = null,
  enabledCipherSuites: Iterable<String>? = null,
  enabledSecureTransportProtocols: Iterable<String>? = null,
  host: String? = null,
  idleTimeout: Int? = null,
  jdkSslEngineOptions: io.vertx.core.net.JdkSSLEngineOptions? = null,
  keyStoreOptions: io.vertx.core.net.JksOptions? = null,
  logActivity: Boolean? = null,
  maxMessageSize: Int? = null,
  openSslEngineOptions: io.vertx.core.net.OpenSSLEngineOptions? = null,
  pemKeyCertOptions: io.vertx.core.net.PemKeyCertOptions? = null,
  pemTrustOptions: io.vertx.core.net.PemTrustOptions? = null,
  pfxKeyCertOptions: io.vertx.core.net.PfxOptions? = null,
  pfxTrustOptions: io.vertx.core.net.PfxOptions? = null,
  port: Int? = null,
  receiveBufferSize: Int? = null,
  reuseAddress: Boolean? = null,
  sendBufferSize: Int? = null,
  soLinger: Int? = null,
  ssl: Boolean? = null,
  tcpKeepAlive: Boolean? = null,
  tcpNoDelay: Boolean? = null,
  trafficClass: Int? = null,
  trustStoreOptions: io.vertx.core.net.JksOptions? = null,
  useAlpn: Boolean? = null,
  usePooledBuffers: Boolean? = null): MqttServerOptions = io.vertx.mqtt.MqttServerOptions().apply {

  if (acceptBacklog != null) {
    this.setAcceptBacklog(acceptBacklog)
  }
  if (clientAuth != null) {
    this.setClientAuth(clientAuth)
  }
  if (clientAuthRequired != null) {
    this.setClientAuthRequired(clientAuthRequired)
  }
  if (crlPaths != null) {
    for (item in crlPaths) {
      this.addCrlPath(item)
    }
  }
  if (crlValues != null) {
    for (item in crlValues) {
      this.addCrlValue(item)
    }
  }
  if (enabledCipherSuites != null) {
    for (item in enabledCipherSuites) {
      this.addEnabledCipherSuite(item)
    }
  }
  if (enabledSecureTransportProtocols != null) {
    for (item in enabledSecureTransportProtocols) {
      this.addEnabledSecureTransportProtocol(item)
    }
  }
  if (host != null) {
    this.setHost(host)
  }
  if (idleTimeout != null) {
    this.setIdleTimeout(idleTimeout)
  }
  if (jdkSslEngineOptions != null) {
    this.setJdkSslEngineOptions(jdkSslEngineOptions)
  }
  if (keyStoreOptions != null) {
    this.setKeyStoreOptions(keyStoreOptions)
  }
  if (logActivity != null) {
    this.setLogActivity(logActivity)
  }
  if (maxMessageSize != null) {
    this.setMaxMessageSize(maxMessageSize)
  }
  if (openSslEngineOptions != null) {
    this.setOpenSslEngineOptions(openSslEngineOptions)
  }
  if (pemKeyCertOptions != null) {
    this.setPemKeyCertOptions(pemKeyCertOptions)
  }
  if (pemTrustOptions != null) {
    this.setPemTrustOptions(pemTrustOptions)
  }
  if (pfxKeyCertOptions != null) {
    this.setPfxKeyCertOptions(pfxKeyCertOptions)
  }
  if (pfxTrustOptions != null) {
    this.setPfxTrustOptions(pfxTrustOptions)
  }
  if (port != null) {
    this.setPort(port)
  }
  if (receiveBufferSize != null) {
    this.setReceiveBufferSize(receiveBufferSize)
  }
  if (reuseAddress != null) {
    this.setReuseAddress(reuseAddress)
  }
  if (sendBufferSize != null) {
    this.setSendBufferSize(sendBufferSize)
  }
  if (soLinger != null) {
    this.setSoLinger(soLinger)
  }
  if (ssl != null) {
    this.setSsl(ssl)
  }
  if (tcpKeepAlive != null) {
    this.setTcpKeepAlive(tcpKeepAlive)
  }
  if (tcpNoDelay != null) {
    this.setTcpNoDelay(tcpNoDelay)
  }
  if (trafficClass != null) {
    this.setTrafficClass(trafficClass)
  }
  if (trustStoreOptions != null) {
    this.setTrustStoreOptions(trustStoreOptions)
  }
  if (useAlpn != null) {
    this.setUseAlpn(useAlpn)
  }
  if (usePooledBuffers != null) {
    this.setUsePooledBuffers(usePooledBuffers)
  }
}

