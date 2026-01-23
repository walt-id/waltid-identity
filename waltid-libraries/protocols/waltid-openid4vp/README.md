<div align="center">
<h1>OpenID for Verifiable Presentations (OpenID4VP) 1.0</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>Core Kotlin Multiplatform library providing models and utilities for OpenID4VP 1.0</p>

<a href="https://walt.id/community">
<img src="https://img.shields.io/badge/Join-The Community-blue.svg?style=flat" alt="Join community!" />
</a>
<a href="https://www.linkedin.com/company/walt-id/">
<img src="https://img.shields.io/badge/-LinkedIn-0072b1?style=flat&logo=linkedin" alt="Follow walt_id" />
</a>
  
  <h2>Status</h2>
  <p align="center">
    <img src="https://img.shields.io/badge/🟢%20Actively%20Maintained-success?style=for-the-badge&logo=check-circle" alt="Status: Actively Maintained" />
    <br/>
    <em>This project is being actively maintained by the development team at walt.id.<br />Regular updates, bug fixes, and new features are being added.</em>
  </p>
</div>

## What This Library Contains

This library provides the foundational data models, types, and utilities for implementing **OpenID for Verifiable Presentations (OpenID4VP) 1.0**. It defines the core structures used by Verifiers to request verifiable presentations from Wallets.

Learn more about OpenID4VP [here](https://docs.walt.id/concepts/data-exchange-protocols/openid4vp).

The library includes:
- **Authorization Request models** - Data structures for OpenID4VP authorization requests
- **Client Metadata** - Verifier metadata and capabilities
- **Response Types and Modes** - Configuration for how presentations are returned
- **Credential Format enumerations** - Standard format identifiers (JWT VC JSON, SD-JWT VC, mdoc, etc.)
- **Client ID Prefix utilities** - Basic utilities for parsing client identifier prefixes

## Main Purpose

This library serves as the foundation for OpenID4VP implementations. It provides the data models and types that both Verifier and Wallet implementations need to communicate using the OpenID4VP protocol.

**Use this library when:**
- You're building a custom OpenID4VP implementation and need the core data models
- You want to understand the structure of OpenID4VP authorization requests and responses
- You're implementing one of the helper libraries (client ID prefix parsing, OpenAPI generation)

**You typically won't use this library directly** if you're building a Verifier or Wallet - instead, use the implementation libraries:
- **[waltid-openid4vp-verifier](../waltid-openid4vp-verifier/README.md)** - Complete Verifier implementation
- **[waltid-openid4vp-wallet](../waltid-openid4vp-wallet/README.md)** - Complete Wallet/Holder implementation

## Key Concepts

### OpenID4VP Protocol Overview

OpenID4VP enables Verifiers to request verifiable presentations from Wallets. The protocol uses **Digital Credentials Query Language (DCQL)** to specify credential requirements, replacing the older Presentation Definition format used in draft implementations.

**Basic Protocol Flow:**
1. **Verifier** creates an Authorization Request with a DCQL query specifying required credentials
2. **Wallet** receives the request and matches available credentials against the query
3. **Wallet** creates Verifiable Presentation(s) and sends them in a `vp_token`
4. **Verifier** validates the presentation(s)

For detailed flow documentation, see [flow.md](./flow.md).

### DCQL (Digital Credentials Query Language)

DCQL is a JSON-based language for specifying credential requirements. It replaces Presentation Definition from earlier OpenID4VP drafts. DCQL queries define:
- **Credential formats** (e.g., `jwt_vc_json`, `dc+sd-jwt`, `mso_mdoc`)
- **Required claims** with JSON paths
- **Claim values** for filtering
- **Credential sets** for combining multiple credentials
- **Trusted authorities** for issuer filtering

See [waltid-dcql](../../credentials/waltid-dcql/README.md) for complete DCQL documentation.

### Authorization Request

The `AuthorizationRequest` is the core data structure that Verifiers send to Wallets. It contains:
- **`client_id`** - Verifier identifier (may include prefixes)
- **`response_type`** - Typically `vp_token` for OpenID4VP
- **`response_mode`** - How the response is delivered (`fragment`, `query`, `direct_post`, etc.)
- **`dcql_query`** - The credential requirements (REQUIRED unless using `scope`)
- **`nonce`** - Fresh random value for replay protection (REQUIRED)
- **`state`** - Session state for CSRF protection (RECOMMENDED)
- **`redirect_uri`** - Where to send the response (for redirect-based flows)
- **`response_uri`** - Where to POST the response (for `direct_post` mode)

### Client ID Prefixes

OpenID4VP supports dynamic Verifier identification using prefixes beyond simple client IDs. This enables:
- **Dynamic registration** without pre-registration
- **Trust mechanisms** using DIDs, X.509 certificates, or attestations
- **Federation** via OpenID Federation

Supported prefixes include:
- `redirect_uri:` - Use redirect URI as client identifier
- `x509_san_dns:` - Authenticate via X.509 certificate
- `decentralized_identifier:` - Authenticate via DID
- `verifier_attestation:` - Authenticate via Verifier Attestation JWT
- `openid_federation:` - Resolve via OpenID Federation

See **[waltid-openid4vp-clientidprefix](../waltid-openid4vp-clientidprefix/README.md)** for parsing and authentication utilities.

### Response Modes

The protocol supports multiple ways for Wallets to return presentations:

- **`fragment`** - Response in URL fragment (default for `vp_token` with redirect)
- **`query`** - Response in URL query string (used with `response_type=code`)
- **`direct_post`** - Wallet POSTs response directly to Verifier's `response_uri`
- **`direct_post.jwt`** - Like `direct_post`, but response is JWE-encrypted
- **`dc_api` / `dc_api.jwt`** - Digital Credentials API (Appendix A)

### Credential Formats

The library defines standard credential format identifiers:
- **`jwt_vc_json`** - W3C Verifiable Credentials as JWT
- **`ldp_vc`** - W3C Verifiable Credentials with Data Integrity Proofs
- **`mso_mdoc`** - ISO mdoc (mobile driver's license)
- **`dc+sd-jwt`** - IETF SD-JWT Verifiable Credentials

## Assumptions and Dependencies

### Multiplatform Support

Works on JVM (Kotlin/Java), JavaScript, and iOS platforms (iOS requires `enableIosBuild=true` Gradle property).

### Dependencies

- **waltid-dcql** - For DCQL query models and matching
- **Kotlinx Serialization** - For JSON serialization
- **Ktor Client** - For HTTP requests (in implementation libraries)

### Protocol Version

This library implements **OpenID for Verifiable Presentations 1.0** (final specification), not the draft versions.

## How to Use This Library

### Basic Usage

Most developers should use the implementation libraries rather than this core library directly. However, if you need to work with the core models:

```kotlin
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.openid.OpenID4VPResponseType
import id.walt.dcql.models.DcqlQuery

val authRequest = AuthorizationRequest(
    responseType = OpenID4VPResponseType.VP_TOKEN,
    clientId = "did:key:z6M...",
    redirectUri = "https://verifier.example.com/callback",
    dcqlQuery = dcqlQuery,
    responseMode = "fragment",
    state = "xyz123",
    nonce = "nonce_value"
)
```

### Key Source Files

- `AuthorizationRequest.kt` - Core authorization request structure
- `ClientMetadata.kt` - Verifier metadata and capabilities
- `CredentialFormat.kt` - Credential format enumerations
- `OpenID4VPResponseType.kt` / `OpenID4VPResponseMode.kt` - Response configuration
- `ClientIdPrefixDecoder.kt` - Basic client ID prefix utilities

## Related Libraries

This core library is used by several specialized libraries:

### Implementation Libraries

- **[waltid-openid4vp-verifier](../waltid-openid4vp-verifier/README.md)** - Complete Verifier implementation for creating and validating OpenID4VP requests. Includes session management, presentation validation, and DCQL fulfillment checking.

- **[waltid-openid4vp-wallet](../waltid-openid4vp-wallet/README.md)** - Complete Wallet/Holder implementation for processing authorization requests and generating presentations. Handles credential selection, presentation creation, and response delivery.

### Helper Libraries

- **[waltid-openid4vp-clientidprefix](../waltid-openid4vp-clientidprefix/README.md)** - Client ID prefix parsing and authentication utilities. Use this when you need to parse and validate client identifier prefixes.

- **[waltid-openid4vp-verifier-openapi](../waltid-openid4vp-verifier-openapi/README.md)** - OpenAPI schema generation and example request bodies for Verifier endpoints. Useful for API documentation and testing.

### Supporting Libraries

- **[waltid-dcql](../../credentials/waltid-dcql/README.md)** - Digital Credentials Query Language models and matching engine
- **[waltid-digital-credentials](../../credentials/waltid-digital-credentials/README.md)** - Unified credential parsing and validation
- **[waltid-verification-policies2](../../credentials/waltid-verification-policies2/README.md)** - Verification policies for validating presentations

## Documentation

- [OpenID for Verifiable Presentations 1.0 Specification](https://openid.net/specs/openid-4-verifiable-presentations-1_0.html)
- [DCQL Documentation](../../credentials/waltid-dcql/README.md)
- [Protocol Flow Details](./flow.md)

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
* Find more indepth documentation on our [docs site](https://docs.walt.id)

## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

<div align="center">
<img src="../../../assets/walt-banner.png" alt="walt.id banner" />
</div>
