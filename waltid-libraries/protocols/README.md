# walt.id Protocol Libraries

by [walt.id](https://walt.id)

Libraries implementing identity and credential protocols (OpenID4VC, OpenID4VP, SIOPv2)



## Statuses Explained


|                                                                                                                                                                            |                                                                                                                                                                                     |
| -------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| *This project is being actively maintained by the development team at walt.id. Regular updates, bug fixes, and new features are being added.*                              | *This project is not actively maintained. Certain features may be outdated or not working as expected. We encourage users to contribute to the project to help keep it up to date.* |
| *This project is deprecated and no longer maintained. It should not be used in new projects. Please use our alternative libraries or migrate to recommended replacements.* | *This project is still supported by the development team at walt.id, but is planned for deprecation. We encourage users to migrate to using our alternative libraries.*             |


## Overview

This directory contains libraries implementing identity and credential protocols, including OpenID for Verifiable Credentials (OID4VCI, OID4VP) and Self-Issued OpenID Provider v2 (SIOPv2). These libraries provide multiplatform support for building issuers, verifiers, and wallets.

## Libraries

### Main Libraries:

### [🟠 waltid-openid4vc](./waltid-openid4vc)

Multiplatform library implementing OpenID for Verifiable Credentials specifications. Supports OID4VCI (Credential Issuance) Draft 11/13, OID4VP (Presentation) Draft 14/20, and SIOPv2. Provides data models, protocol flows, and client/server implementations for both issuance and presentation.

**Use when:** You're building issuers, verifiers, or wallets that need to support OpenID4VC draft specifications, or you need multiplatform protocol implementations.

### [🟢 waltid-openid4vci](./waltid-openid4vci)

OpenID4VCI 1.0 OAuth2 provider implementation. Provides a complete OAuth2 provider with authorization and token endpoints for credential issuance flows, enabling Issuers to issue verifiable credentials to Wallets.

**Use when:** You're building an issuer service that needs OAuth2 provider functionality for OpenID4VCI 1.0.

### [🟢 waltid-openid4vc-wallet-server](./waltid-openid4vc-wallet-server)

Ktor HTTP route handlers for the Core Wallet Module. Provides shared REST API endpoints with OpenAPI documentation for wallet management, credential issuance, and presentation flows.

**Use when:** You're building a wallet backend service and need ready-to-use HTTP endpoints.

### [🟢 waltid-openid4vp-verifier](./waltid-openid4vp-verifier)

OpenID4VP 1.0 verifier implementation. Provides integration-ready components for building verifier services that request verifiable presentations from wallets using OpenID4VP 1.0.

**Use when:** You're building a verifier service that needs to request verifiable presentations from wallets using OpenID4VP 1.0.



  
  


### Helper Libraries:

### [🟢 waltid-openid4vp](./waltid-openid4vp)

Core OpenID4VP 1.0 library with DCQL (Digital Credentials Query Language) support. Provides the core abstractions, data models, and protocol flow for OpenID4VP 1.0 verifiable presentation requests.

**Use when:** You need to understand or work with OpenID4VP 1.0 core concepts, DCQL queries, or the base protocol implementation.

### [🟢 waltid-openid4vp-clientidprefix](./waltid-openid4vp-clientidprefix)

Client ID prefix parsing and authentication for OpenID4VP. Provides utilities for parsing and validating client identifier prefixes (x509_san_dns, redirect_uri, did, web-origin, etc.) used in OpenID4VP flows.

**Use when:** You need to parse or validate client identifier prefixes in OpenID4VP flows, or implement dynamic verifier identification.

### [🟢 waltid-openid4vp-verifier-openapi](./waltid-openid4vp-verifier-openapi)

OpenAPI schema generation for OpenID4VP verifier endpoints. Provides tools for generating OpenAPI documentation and example request bodies for OpenID4VP verifier API endpoints.

**Use when:** You need to generate OpenAPI documentation for OpenID4VP verifier endpoints or provide API documentation for verifier services.

### [🟢 waltid-18013-7-verifier](./waltid-18013-7-verifier)

ISO/IEC 18013-7 Annex C (DC API / Apple Wallet) verifier support. Provides HPKE encryption/decryption and transcript building for browser-based mdoc verification flows.

**Use when:** You need to verify mdoc credentials via the Digital Credentials API (browser-based flows, Apple Wallet integration).

  
  


### Wallet Libraries:

### [🟢 waltid-openid4vc-wallet](./waltid-openid4vc-wallet)

Core Wallet Module for OpenID4VCI 1.0 issuance and OpenID4VP 1.0 presentation. Provides a complete, multiplatform wallet library with pluggable storage backends, supporting both pre-authorized code and authorization code grant flows for issuance, and DCQL-based credential presentation.

**Use when:** You're building a wallet application or service that needs to support OpenID4VCI 1.0 and OpenID4VP 1.0.

### [🟢 waltid-openid4vp-wallet](./waltid-openid4vp-wallet)

OpenID4VP 1.0 wallet implementation. Provides integration-ready components for building wallets that respond to verifier presentation requests using OpenID4VP 1.0.

**Use when:** You're building a wallet application that needs to handle verifier presentation requests using OpenID4VP 1.0.

### [🟢 waltid-openid4vci-wallet](./waltid-openid4vci-wallet)

OpenID4VCI 1.0 wallet (holder) implementation. Provides offer parsing, metadata resolution, token exchange, and proof-of-possession for wallets receiving credentials.

**Use when:** You're building a wallet that needs to receive credentials using OpenID4VCI 1.0.

### [🟢 waltid-openid4vc-wallet-persistence](./waltid-openid4vc-wallet-persistence)

SQL-backed persistence for the Wallet SDK. Provides Exposed ORM-based implementations of wallet stores supporting SQLite and PostgreSQL.

**Use when:** You need persistent storage for wallet data (keys, credentials, DIDs) that survives application restarts.



## Protocol Versions

- **Draft Implementations**: Use [waltid-openid4vc](./waltid-openid4vc) for OID4VCI Draft 11/13 and OID4VP Draft 14/20
- **OpenID4VP 1.0**: Use the `waltid-openid4vp-`* libraries for the final OpenID4VP 1.0 specification
- **OpenID4VCI 1.0**: Use [waltid-openid4vci](./waltid-openid4vci) for issuer-side and [waltid-openid4vci-wallet](./waltid-openid4vci-wallet) for wallet-side
- **Wallet SDK**: Use [waltid-openid4vc-wallet](./waltid-openid4vc-wallet) for a complete wallet library supporting both OpenID4VCI 1.0 and OpenID4VP 1.0

## Join the community

- Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
- Get help, request features and report bugs: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
- Find more indepth documentation on our [docs site](https://docs.walt.id)

## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

