<div align="center">
<h1>walt.id Libraries</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>Comprehensive collection of multiplatform libraries for digital identity, credentials, and cryptography</p>

<a href="https://walt.id/community">
<img src="https://img.shields.io/badge/Join-The Community-blue.svg?style=flat" alt="Join community!" />
</a>
<a href="https://www.linkedin.com/company/walt-id/">
<img src="https://img.shields.io/badge/-LinkedIn-0072b1?style=flat&logo=linkedin" alt="Follow walt_id" />
</a>
</div>

## Overview

This directory contains all the core libraries that power the walt.id identity ecosystem. These libraries are organized by domain and provide multiplatform support (JVM, JavaScript, and iOS) for building digital identity applications, credential management systems, cryptographic operations, and authentication services.

## Library Categories

### [Authentication & Authorization](./auth/)
Libraries for implementing authentication and authorization systems:
- **[waltid-ktor-authnz](./auth/waltid-ktor-authnz)**: Flexible authentication and authorization framework for Ktor applications with multi-method support
- **[waltid-permissions](./auth/waltid-permissions)**: Permission management system for access control
- **[waltid-idpkit](./auth/waltid-idpkit)**: Identity Provider toolkit for OIDC-based authentication with verifiable credentials

### [Cryptography](./crypto/)
Libraries for cryptographic operations and key management:
- **[waltid-crypto](./crypto/waltid-crypto)**: Multiplatform cryptographic library with support for multiple algorithms and KMS backends
- **[waltid-crypto2](./crypto/waltid-crypto2)**: Modern cryptographic library implementation
- **[waltid-cose](./crypto/waltid-cose)**: CBOR Object Signing and Encryption (COSE) implementation
- **[waltid-x509](./crypto/waltid-x509)**: X.509 certificate handling and validation
- **[waltid-crypto-android](./crypto/waltid-crypto-android)**: Android-specific cryptographic implementations
- **[waltid-crypto-ios](./crypto/waltid-crypto-ios)**: iOS-specific cryptographic implementations
- **[waltid-crypto-aws](./crypto/waltid-crypto-aws)**: AWS Key Management Service (KMS) integration
- **[waltid-crypto-oci](./crypto/waltid-crypto-oci)**: Oracle Cloud Infrastructure (OCI) KMS integration
- **[waltid-target-ios](./crypto/waltid-target-ios)**: iOS target support for multiplatform crypto libraries

### [Credentials](./credentials/)
Libraries for working with digital credentials across multiple formats:
- **[waltid-digital-credentials](./credentials/waltid-digital-credentials)**: Unified abstraction layer for parsing and verifying credentials (W3C, SD-JWT, mdoc)
- **[waltid-w3c-credentials](./credentials/waltid-w3c-credentials)**: W3C Verifiable Credentials implementation (v1.1 and v2.0)
- **[waltid-mdoc-credentials](./credentials/waltid-mdoc-credentials)**: ISO/IEC 18013-5:2021 mdoc implementation
- **[waltid-mdoc-credentials2](./credentials/waltid-mdoc-credentials2)**: Modern mdoc credentials implementation
- **[waltid-dcql](./credentials/waltid-dcql)**: Digital Credentials Query Language (DCQL) for OpenID4VP 1.0
- **[waltid-dif-definitions-parser](./credentials/waltid-dif-definitions-parser)**: DIF Presentation Definition parser
- **[waltid-verification-policies](./credentials/waltid-verification-policies)**: Legacy verification policy system for draft implementations
- **[waltid-verification-policies2](./credentials/waltid-verification-policies2)**: Modern verification policy system for OpenID4VP 1.0
- **[waltid-holder-policies](./credentials/waltid-holder-policies)**: Policy-based access control for wallet holders
- **[waltid-digital-credentials-examples](./credentials/waltid-digital-credentials-examples)**: Example credentials for testing and development
- **[waltid-vical](./credentials/waltid-vical)**: VICAL trust list validation for mdoc credentials

### [Protocols](./protocols/)
Libraries implementing identity and credential protocols:
- **[waltid-openid4vc](./protocols/waltid-openid4vc)**: OpenID for Verifiable Credentials (OID4VCI, OID4VP, SIOPv2) - draft implementations
- **[waltid-openid4vp](./protocols/waltid-openid4vp)**: Core OpenID4VP 1.0 library with DCQL support
- **[waltid-openid4vp-verifier](./protocols/waltid-openid4vp-verifier)**: OpenID4VP 1.0 verifier implementation
- **[waltid-openid4vp-wallet](./protocols/waltid-openid4vp-wallet)**: OpenID4VP 1.0 wallet implementation
- **[waltid-openid4vp-clientidprefix](./protocols/waltid-openid4vp-clientidprefix)**: Client ID prefix parsing and authentication for OpenID4VP
- **[waltid-openid4vp-verifier-openapi](./protocols/waltid-openid4vp-verifier-openapi)**: OpenAPI schema generation for OpenID4VP verifier endpoints

### [SD-JWT](./sdjwt/)
Libraries for Selective Disclosure JWT (SD-JWT) credentials:
- **[waltid-sdjwt](./sdjwt/waltid-sdjwt)**: Multiplatform SD-JWT implementation with selective disclosure support
- **[waltid-sdjwt-ios](./sdjwt/waltid-sdjwt-ios)**: iOS-specific SD-JWT implementations

### [Web](./web/)
Libraries for web server functionality:
- **[waltid-ktor-notifications-core](./web/waltid-ktor-notifications-core)**: Core library for Ktor server notifications (SSE, webhooks)
- **[waltid-ktor-notifications](./web/waltid-ktor-notifications)**: Ktor plugin for session notifications

### Core Libraries
Standalone libraries providing core functionality:
- **[waltid-did](./waltid-did)**: Decentralized Identifier (DID) library with support for multiple DID methods
- **[waltid-core-wallet](./waltid-core-wallet)**: Core wallet helpers for building wallets on top of OpenID4VC
- **[waltid-library-commons](./waltid-library-commons)**: Common utilities and shared code for walt.id libraries
- **[waltid-java-compat](./waltid-java-compat)**: Java compatibility layer for Kotlin libraries

## Getting Started

Each library has its own README with detailed documentation. For quick reference:

- **Building a wallet?** Start with [waltid-core-wallet](./waltid-core-wallet) and [waltid-openid4vc](./protocols/waltid-openid4vc)
- **Working with credentials?** See [waltid-digital-credentials](./credentials/waltid-digital-credentials) for the unified abstraction
- **Need cryptography?** Check [waltid-crypto](./crypto/waltid-crypto) for key management and operations
- **Building authentication?** See [waltid-ktor-authnz](./auth/waltid-ktor-authnz) for flexible auth systems
- **Implementing protocols?** Review [waltid-openid4vc](./protocols/waltid-openid4vc) for credential issuance and presentation

## Multiplatform Support

Most libraries in this directory support multiple platforms:
- **JVM**: Full support for Kotlin/Java applications
- **JavaScript**: Browser and Node.js support
- **iOS**: Native iOS support (enabled via `enableIosBuild=true` Gradle property)

Platform-specific implementations are provided where needed (e.g., `waltid-crypto-android`, `waltid-crypto-ios`).

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues ](https://github.com/walt-id/waltid-identity/issues)

## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

