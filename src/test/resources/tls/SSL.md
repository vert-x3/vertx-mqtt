# SSL Certificates for MQTT Testing

## Notes

Tests in vertx-mqtt library are not meant to exercise SSL/TLS correctness for MQTT endpoints as this is a job of
vert.x core's SSL/TLS tests. Instead tests just verify that client or server is trusted or not trusted and
proper information is available for vertx-mqtt library's users.

In order to allow tests to pass for a while extra long expiration dates is being used.

For testing MQTT Server functionality Eclipse Paho MQTT client is used and for that purpose JKS keystore stores are
needed.

MQTT Client functionality testing needs different kinds of certificate stores.

In this document there are instructions on how to recreate certificates and stores in case there is a need.

## SSL Server Certificates

Server certificates are used by client to verify that it is connecting to trusted server.

### Root CA for SSL Server Certificates

Create Server Root CA:
```
mkdir server-root-ca
openssl req -x509 -sha256 -nodes -days 36500 -newkey rsa:2048 -subj "/O=Server Root CA" -keyout server-root-ca/ca-key.pem -out server-root-ca/ca-cert.pem
touch server-root-ca/index.txt
echo 01 > server-root-ca/serial
echo 1000 > server-root-ca/crlnumber
echo "unique_subject = no" > server-root-ca/index.txt.attr
```

### SSL Server Certificate

Create private key and certificate chain with _Server Root CA_.
```
# Generate private key and CSR
openssl req -sha256 -out server-csr.pem -new -newkey rsa:2048 -subj "/CN=localhost" -keyout server.key -passout pass:wibble

# Convert private key to pkcs8 so that vert.x understands it
openssl pkcs8 -topk8 -inform pem -in server.key -outform pem -passin pass:wibble -out server-key.pem -nocrypt

# To decode CSR
# openssl req -text -noout -verify -in server-csr.pem

# Create CA signed certificate
openssl ca -config openssl.cnf -name CA_server -keyfile server-root-ca/ca-key.pem -cert server-root-ca/ca-cert.pem -in server-csr.pem -batch | openssl x509 -out server-cert.pem -outform PEM

# To decode certificate
# openssl x509 -in server-cert.pem -text -noout
```

### Client Trust Store (JKS)

Client trust store is used by client to verify that server is trusted. Client trust store just contains _Server Root CA_
certificate.

```
keytool -import -trustcacerts -alias server-root-ca -noprompt -file server-root-ca/ca-cert.pem -keystore client-truststore.jks -keypass wibble -storepass wibble

# To look inside keystore
# keytool -list -v -keystore client-truststore.jks -storepass wibble
```

## SSL Client Certificates

Client certificates are used by server to verify that connecting client is trusted and to provide identity for client.

Client certificates and private keys are all stored in PEM format and in JKS format.

### Trusted Root CA for SSL Client Certificates

Create Client Trusted Root CA:
```
mkdir client-trusted-root-ca
openssl req -x509 -sha256 -nodes -days 36500 -newkey rsa:2048 -subj "/O=Client Trusted Root CA" -keyout client-trusted-root-ca/ca-key.pem -out client-trusted-root-ca/ca-cert.pem
touch client-trusted-root-ca/index.txt
echo 01 > client-trusted-root-ca/serial
echo 1000 > client-trusted-root-ca/crlnumber
echo "unique_subject = no" > client-trusted-root-ca/index.txt.attr
```

### Trusted SSL Client Certificate

Create private key and certificate chain with _Client Trusted Root CA_.
```
# Generate private key and CSR
openssl req -sha256 -out client-trusted-csr.pem -new -newkey rsa:2048 -subj "/CN=Client Trusted Device" -keyout client-trusted.key -passout pass:wibble

# Convert private key to pkcs8 so that vert.x understands it
openssl pkcs8 -topk8 -inform pem -in client-trusted.key -outform pem -passin pass:wibble -out client-trusted-key.pem -nocrypt

# To decode CSR
# openssl req -text -noout -verify -in client-trusted-csr.pem

# Create CA signed certificate
openssl ca -config openssl.cnf -name CA_client_trusted -keyfile client-trusted-root-ca/ca-key.pem -cert client-trusted-root-ca/ca-cert.pem -in client-trusted-csr.pem -batch | openssl x509 -out client-trusted-cert.pem -outform PEM

# To decode certificate
# openssl x509 -in client-trusted-cert.pem -text -noout
```

Create JKS keystore for Eclipse Paho MQTT client:
```
# Create PKCS12 keystore from private key and certificate chain
openssl pkcs12 -export -name client-cert -in client-trusted-cert.pem -certfile client-trusted-root-ca/ca-cert.pem -inkey client-trusted-key.pem -passin pass:wibble -out client-trusted-store.p12 -password pass:wibble

# Convert PKCS12 keystore into a JKS keystore
keytool -importkeystore -destkeystore client-trusted-store.jks -deststorepass wibble -srckeystore client-trusted-store.p12 -srcstoretype pkcs12 -srcstorepass wibble -alias client-cert

# To look inside keystore
# keytool -list -v -keystore client-trusted-store.jks -storepass wibble
```

### Untrusted Root CA for SSL Client Certificates

Create Client Untrusted Root CA:
```
mkdir client-untrusted-root-ca
openssl req -x509 -sha256 -nodes -days 36500 -newkey rsa:2048 -subj "/O=Client Untrusted Root CA" -keyout client-untrusted-root-ca/ca-key.pem -out client-untrusted-root-ca/ca-cert.pem
touch client-untrusted-root-ca/index.txt
echo 01 > client-untrusted-root-ca/serial
echo 1000 > client-untrusted-root-ca/crlnumber
echo "unique_subject = no" > client-untrusted-root-ca/index.txt.attr
```

### Untrusted SSL Client Certificate

Create private key and certificate chain with _Client Untrusted Root CA_.
```
# Generate private key and CSR
openssl req -sha256 -out client-untrusted-csr.pem -new -newkey rsa:2048 -subj "/CN=Client Untrusted Device" -keyout client-untrusted.key -passout pass:wibble

# Convert private key to pkcs8 so that vert.x understands it
openssl pkcs8 -topk8 -inform pem -in client-untrusted.key -outform pem -passin pass:wibble -out client-untrusted-key.pem -nocrypt

# To decode CSR
# openssl req -text -noout -verify -in client-untrusted-csr.pem

# Create CA signed certificate
openssl ca -config openssl.cnf -name CA_client_untrusted -keyfile client-untrusted-root-ca/ca-key.pem -cert client-untrusted-root-ca/ca-cert.pem -in client-untrusted-csr.pem -batch | openssl x509 -out client-untrusted-cert.pem -outform PEM

# To decode certificate
# openssl x509 -in client-untrusted-cert.pem -text -noout
```

Create JKS keystore for Eclipse Paho MQTT client:
```
# Create PKCS12 keystore from private key and certificate chain
openssl pkcs12 -export -name client-cert -in client-untrusted-cert.pem -certfile client-untrusted-root-ca/ca-cert.pem -inkey client-untrusted-key.pem -passin pass:wibble -out client-untrusted-store.p12 -password pass:wibble

# Convert PKCS12 keystore into a JKS keystore
keytool -importkeystore -destkeystore client-untrusted-store.jks -deststorepass wibble -srckeystore client-untrusted-store.p12 -srcstoretype pkcs12 -srcstorepass wibble -alias client-cert

# To look inside keystore
# keytool -list -v -keystore client-untrusted-store.jks -storepass wibble
```
