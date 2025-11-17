<div align="center">
<h1>walt.id Cryptography Libraries</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>Libraries for cryptographic operations, key management, and certificate handling</p>

<a href="https://walt.id/community">
<img src="https://img.shields.io/badge/Join-The Community-blue.svg?style=flat" alt="Join community!" />
</a>
<a href="https://www.linkedin.com/company/walt-id/">
<img src="https://img.shields.io/badge/-LinkedIn-0072b1?style=flat&logo=linkedin" alt="Follow walt_id" />
</a>
</div>

## Overview

This directory contains libraries for cryptographic operations, key management across multiple backends, and certificate handling. These libraries provide multiplatform support (JVM, JavaScript, iOS) for building secure identity applications. Generally, the `waltid-crypto` library is the main library to use for cryptographic operations, with the `waltid-cose` and `waltid-x509` libraries providing additional functionality for certain use cases.

## Libraries

### [waltid-crypto](./waltid-crypto)
Multiplatform cryptographic library with support for multiple algorithms (Ed25519, secp256k1, secp256r1, RSA) and key management backends (local, TSE/Vault, AWS KMS, OCI KMS). Provides unified APIs for key generation, signing, encryption, and key management operations.

**Use when:** You need cryptographic operations with support for multiple algorithms and KMS backends across JVM, JavaScript, and iOS platforms.

### WIP: [waltid-crypto2](./waltid-crypto2)
Modern cryptographic library implementation with improved API design and structure. This is a newer version of the crypto library that we are currently exploring and developing, however it is not yet ready for use.

**Use when:** Not recommended.

### [waltid-cose](./waltid-cose)
CBOR Object Signing and Encryption (COSE) implementation. Provides support for COSE message formats used in mdoc credentials and other CBOR-based cryptographic protocols.

**Use when:** You need to work with COSE-encoded messages, particularly for mdoc credentials or other CBOR-based cryptographic operations.

### [waltid-x509](./waltid-x509)
X.509 certificate handling and validation. Provides utilities for parsing, validating, and working with X.509 certificates and certificate chains.

**Use when:** You need to handle X.509 certificates, validate certificate chains, or work with PKI-based trust systems.

### [waltid-crypto-android](./waltid-crypto-android)
Android-specific cryptographic implementations. Provides Android platform optimizations and Android Keystore integration.

**Use when:** You're building Android applications and need platform-specific cryptographic optimizations or Android Keystore integration.

### [waltid-crypto-ios](./waltid-crypto-ios)
iOS-specific cryptographic implementations. Provides iOS platform support and native iOS cryptographic operations.

**Use when:** You're building iOS applications and need native iOS cryptographic support.

### [waltid-crypto-aws](./waltid-crypto-aws)
AWS Key Management Service (KMS) integration. Provides seamless integration with AWS KMS for key management and cryptographic operations.

**Use when:** You need to use AWS KMS for key management and cryptographic operations in cloud-based applications  that require the SDK over the REST API.

### [waltid-crypto-oci](./waltid-crypto-oci)
Oracle Cloud Infrastructure (OCI) KMS integration. Provides integration with OCI Key Management for key management and cryptographic operations.

**Use when:** You need to use Oracle Cloud Infrastructure KMS for key management in OCI-based deployments that require the SDK over the REST API.

### [waltid-target-ios](./waltid-target-ios)
iOS target support for multiplatform crypto libraries. Provides the necessary infrastructure and bindings for iOS platform support in multiplatform cryptographic libraries.

**Use when:** You're building multiplatform libraries that need iOS support and require iOS-specific implementations.

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues ](https://github.com/walt-id/waltid-identity/issues)

## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

