package io.vertx.kotlin.mqtt

import io.vertx.mqtt.MqttClientOptions
import io.vertx.core.net.JdkSSLEngineOptions
import io.vertx.core.net.JksOptions
import io.vertx.core.net.OpenSSLEngineOptions
import io.vertx.core.net.PemKeyCertOptions
import io.vertx.core.net.PemTrustOptions
import io.vertx.core.net.PfxOptions
import io.vertx.core.net.ProxyOptions

/**
 * A function providing a DSL for building [io.vertx.mqtt.MqttClientOptions] objects.
 *
 * Represents options used by the MQTT client.
 *
 * @param autoGeneratedClientId  Set if the MQTT client must generate clientId automatically (default is true)
 * @param autoKeepAlive  Set if the MQTT client must handle PINGREQ automatically (default is true)
 * @param cleanSession  Set to start with a clean session (or not)
 * @param clientId  Set the client identifier
 * @param connectTimeout 
 * @param crlPaths 
 * @param crlValues 
 * @param enabledCipherSuites 
 * @param enabledSecureTransportProtocols 
 * @param hostnameVerificationAlgorithm 
 * @param idleTimeout 
 * @param jdkSslEngineOptions 
 * @param keepAliveTimeSeconds  Set the keep alive timeout in seconds
 * @param keyStoreOptions 
 * @param localAddress 
 * @param logActivity 
 * @param maxInflightQueue  Set max count of unacknowledged messages
 * @param maxMessageSize  Set max MQTT message size
 * @param metricsName 
 * @param openSslEngineOptions 
 * @param password  Set the password
 * @param pemKeyCertOptions 
 * @param pemTrustOptions 
 * @param pfxKeyCertOptions 
 * @param pfxTrustOptions 
 * @param proxyOptions 
 * @param receiveBufferSize 
 * @param reconnectAttempts 
 * @param reconnectInterval 
 * @param reuseAddress 
 * @param reusePort 
 * @param sendBufferSize 
 * @param soLinger 
 * @param ssl 
 * @param tcpCork 
 * @param tcpFastOpen 
 * @param tcpKeepAlive 
 * @param tcpNoDelay 
 * @param tcpQuickAck 
 * @param trafficClass 
 * @param trustAll 
 * @param trustStoreOptions 
 * @param useAlpn 
 * @param usePooledBuffers 
 * @param username  Set the username
 * @param willFlag  Set if will information are provided on connection
 * @param willMessage  Set the content of the will message
 * @param willQoS  Set the QoS level for the will message
 * @param willRetain  Set if the will message must be retained
 * @param willTopic  Set the topic on which the will message will be published
 *
 * <p/>
 * NOTE: This function has been automatically generated from the [io.vertx.mqtt.MqttClientOptions original] using Vert.x codegen.
 */
fun MqttClientOptions(
  autoGeneratedClientId: Boolean? = null,
  autoKeepAlive: Boolean? = null,
  cleanSession: Boolean? = null,
  clientId: String? = null,
  connectTimeout: Int? = null,
  crlPaths: Iterable<String>? = null,
  crlValues: Iterable<io.vertx.core.buffer.Buffer>? = null,
  enabledCipherSuites: Iterable<String>? = null,
  enabledSecureTransportProtocols: Iterable<String>? = null,
  hostnameVerificationAlgorithm: String? = null,
  idleTimeout: Int? = null,
  jdkSslEngineOptions: io.vertx.core.net.JdkSSLEngineOptions? = null,
  keepAliveTimeSeconds: Int? = null,
  keyStoreOptions: io.vertx.core.net.JksOptions? = null,
  localAddress: String? = null,
  logActivity: Boolean? = null,
  maxInflightQueue: Int? = null,
  maxMessageSize: Int? = null,
  metricsName: String? = null,
  openSslEngineOptions: io.vertx.core.net.OpenSSLEngineOptions? = null,
  password: String? = null,
  pemKeyCertOptions: io.vertx.core.net.PemKeyCertOptions? = null,
  pemTrustOptions: io.vertx.core.net.PemTrustOptions? = null,
  pfxKeyCertOptions: io.vertx.core.net.PfxOptions? = null,
  pfxTrustOptions: io.vertx.core.net.PfxOptions? = null,
  proxyOptions: io.vertx.core.net.ProxyOptions? = null,
  receiveBufferSize: Int? = null,
  reconnectAttempts: Int? = null,
  reconnectInterval: Long? = null,
  reuseAddress: Boolean? = null,
  reusePort: Boolean? = null,
  sendBufferSize: Int? = null,
  soLinger: Int? = null,
  ssl: Boolean? = null,
  tcpCork: Boolean? = null,
  tcpFastOpen: Boolean? = null,
  tcpKeepAlive: Boolean? = null,
  tcpNoDelay: Boolean? = null,
  tcpQuickAck: Boolean? = null,
  trafficClass: Int? = null,
  trustAll: Boolean? = null,
  trustStoreOptions: io.vertx.core.net.JksOptions? = null,
  useAlpn: Boolean? = null,
  usePooledBuffers: Boolean? = null,
  username: String? = null,
  willFlag: Boolean? = null,
  willMessage: String? = null,
  willQoS: Int? = null,
  willRetain: Boolean? = null,
  willTopic: String? = null): MqttClientOptions = io.vertx.mqtt.MqttClientOptions().apply {

  if (autoGeneratedClientId != null) {
    this.setAutoGeneratedClientId(autoGeneratedClientId)
  }
  if (autoKeepAlive != null) {
    this.setAutoKeepAlive(autoKeepAlive)
  }
  if (cleanSession != null) {
    this.setCleanSession(cleanSession)
  }
  if (clientId != null) {
    this.setClientId(clientId)
  }
  if (connectTimeout != null) {
    this.setConnectTimeout(connectTimeout)
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
  if (hostnameVerificationAlgorithm != null) {
    this.setHostnameVerificationAlgorithm(hostnameVerificationAlgorithm)
  }
  if (idleTimeout != null) {
    this.setIdleTimeout(idleTimeout)
  }
  if (jdkSslEngineOptions != null) {
    this.setJdkSslEngineOptions(jdkSslEngineOptions)
  }
  if (keepAliveTimeSeconds != null) {
    this.setKeepAliveTimeSeconds(keepAliveTimeSeconds)
  }
  if (keyStoreOptions != null) {
    this.setKeyStoreOptions(keyStoreOptions)
  }
  if (localAddress != null) {
    this.setLocalAddress(localAddress)
  }
  if (logActivity != null) {
    this.setLogActivity(logActivity)
  }
  if (maxInflightQueue != null) {
    this.setMaxInflightQueue(maxInflightQueue)
  }
  if (maxMessageSize != null) {
    this.setMaxMessageSize(maxMessageSize)
  }
  if (metricsName != null) {
    this.setMetricsName(metricsName)
  }
  if (openSslEngineOptions != null) {
    this.setOpenSslEngineOptions(openSslEngineOptions)
  }
  if (password != null) {
    this.setPassword(password)
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
  if (proxyOptions != null) {
    this.setProxyOptions(proxyOptions)
  }
  if (receiveBufferSize != null) {
    this.setReceiveBufferSize(receiveBufferSize)
  }
  if (reconnectAttempts != null) {
    this.setReconnectAttempts(reconnectAttempts)
  }
  if (reconnectInterval != null) {
    this.setReconnectInterval(reconnectInterval)
  }
  if (reuseAddress != null) {
    this.setReuseAddress(reuseAddress)
  }
  if (reusePort != null) {
    this.setReusePort(reusePort)
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
  if (tcpCork != null) {
    this.setTcpCork(tcpCork)
  }
  if (tcpFastOpen != null) {
    this.setTcpFastOpen(tcpFastOpen)
  }
  if (tcpKeepAlive != null) {
    this.setTcpKeepAlive(tcpKeepAlive)
  }
  if (tcpNoDelay != null) {
    this.setTcpNoDelay(tcpNoDelay)
  }
  if (tcpQuickAck != null) {
    this.setTcpQuickAck(tcpQuickAck)
  }
  if (trafficClass != null) {
    this.setTrafficClass(trafficClass)
  }
  if (trustAll != null) {
    this.setTrustAll(trustAll)
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
  if (username != null) {
    this.setUsername(username)
  }
  if (willFlag != null) {
    this.setWillFlag(willFlag)
  }
  if (willMessage != null) {
    this.setWillMessage(willMessage)
  }
  if (willQoS != null) {
    this.setWillQoS(willQoS)
  }
  if (willRetain != null) {
    this.setWillRetain(willRetain)
  }
  if (willTopic != null) {
    this.setWillTopic(willTopic)
  }
}

