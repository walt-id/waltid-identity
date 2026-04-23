<div align="center">
<h1>OpenID for Verifiable Credential Issuance (OpenID4VCI) Wallet Library</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>Kotlin Multiplatform library implementing the Wallet (Holder) role for OpenID4VCI 1.0</p>

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

This library provides a complete implementation of the **Wallet (Holder) role** for **OpenID for Verifiable Credential Issuance (OpenID4VCI) 1.0**. It allows applications to:

- **Parse and resolve credential offers** - Handle `openid-credential-offer://` URLs and fetch remote offers
- **Discover issuer metadata** - Resolve issuer and authorization server metadata from `.well-known` endpoints
- **OAuth 2.0 Client** - Complete client implementation with PKCE and state management
- **Authorization Requests** - Handle authorization code and pre-authorized code flows
- **Token Exchange** - Exchange codes for access tokens with OpenID4VCI extensions
- **Proof of Possession** - Generate cryptographic proofs of possession (JWT-based)
- **Credential Requests** - Request and receive verifiable credentials from Issuers

Learn more about OpenID4VCI [here](https://docs.walt.id/concepts/data-exchange-protocols/openid4vci).

## Main Purpose

This library serves as the foundation for building OpenID4VCI Wallet implementations. It provides a framework-agnostic set of components that handle the complex flows required by the OpenID4VCI specification.

**Use this library when:**
- You're building an OpenID4VCI Wallet application
- You need to support both authorization code and pre-authorized code grant flows
- You want a multiplatform wallet library that works on JVM, JavaScript, and iOS
- You need to generate compliant proofs of possession for credential issuance

## Architecture

The library follows a modular design based on the OpenID4VCI 1.0 specification:

```
waltid-openid4vci-wallet
├── oauth/                   # OAuth 2.0 client components (RFC 6749)
├── offer/                   # Credential offer parsing (§4)
├── metadata/                # Issuer metadata discovery (§11.2)
├── authorization/           # Authorization requests (§5)
├── token/                   # Token exchange (§6)
└── proof/                   # Proof of possession (§7.2)
```

## Assumptions and Dependencies

### Multiplatform Support

Works on JVM (Kotlin/Java), JavaScript, and iOS platforms (iOS requires `enableIosBuild=true` Gradle property).

### Dependencies

- **waltid-openid4vci** - Shared protocol models (CredentialOffer, CredentialIssuerMetadata, etc.)
- **waltid-crypto** - Cryptographic key operations and proof signing
- **Ktor Client** - For HTTP requests
- **Kotlinx Serialization** - For JSON serialization
- **Kotlin Logging** - For logging support
- **Kotlinx DateTime** - For timestamp handling
- **Kotlinx Coroutines** - For asynchronous operations

### Protocol Version

This library implements **OpenID for Verifiable Credential Issuance 1.0** (final specification), building on OAuth 2.0 and OpenID Connect.

## How to Use This Library

### Basic Usage

```kotlin
import id.waltid.openid4vci.wallet.*
import id.waltid.openid4vci.wallet.oauth.ClientConfiguration
import id.waltid.openid4vci.wallet.offer.*
import id.waltid.openid4vci.wallet.metadata.*
import id.waltid.openid4vci.wallet.token.*
import id.waltid.openid4vci.wallet.proof.*
import io.ktor.client.*
import id.walt.crypto.keys.KeyManager

// 1. Initialize components
val httpClient = HttpClient()
val clientConfig = ClientConfiguration(
    clientId = "my-wallet-app",
    redirectUris = listOf("myapp://callback")
)

// 2. Parse credential offer
val offerUrl = "openid-credential-offer://?"
val offerRequest = CredentialOfferParser.parseCredentialOfferUrl(offerUrl)

// 3. Resolve offer (fetch if URI reference)
val offerResolver = CredentialOfferResolver(httpClient)
val offer = offerResolver.resolveCredentialOffer(
    offerRequest.credentialOffer,
    offerRequest.credentialOfferUri
)

// 4. Discover issuer metadata
val metadataResolver = IssuerMetadataResolver(httpClient)
val issuerMetadata = metadataResolver.resolveCredentialIssuerMetadata(offer.credentialIssuer)
val authServerMetadata = metadataResolver.resolveAuthorizationServerMetadataWithFallback(issuerMetadata)

// 5. Resolve offered credentials
val resolvedOffers = OfferedCredentialResolver.resolveOfferedCredentials(offer, issuerMetadata)

// 6. Authorization flow (example: pre-authorized code)
val preAuthGrant = offer.grants?.preAuthorizedCodeGrant
if (preAuthGrant != null) {
    val tokenBuilder = TokenRequestBuilder(clientConfig, httpClient)
    val tokenResponse = tokenBuilder.exchangePreAuthorizedCode(
        tokenEndpoint = authServerMetadata.tokenEndpoint!!,
        preAuthorizedCode = preAuthGrant.preAuthorizedCode,
        txCode = null // or user-provided PIN
    )

    // 7. Generate proof of possession
    val key = KeyManager.loadKey("my-key-id")
    val proofBuilder = JwtProofBuilder()
    val proof = proofBuilder.buildProof(
        key = key,
        audience = offer.credentialIssuer,
        nonce = tokenResponse.c_nonce!!
    )

    // 8. Request credential (Implementation coming soon)
    // ...
}
```

## Key Source Files

- `oauth/` - OAuth 2.0 client components (PKCE, State management)
- `offer/` - Credential offer parsing and resolution
- `metadata/` - Issuer and authorization server metadata discovery
- `authorization/` - Authorization request building and response parsing
- `token/` - Token exchange logic for both grant types
- `proof/` - Proof of possession building (JWT-based)

## Related Libraries

This library is part of the walt.id OpenID4VCI ecosystem:

### Supporting Libraries

- **[waltid-openid4vci](../waltid-openid4vci/README.md)** - Shared protocol models and Issuer-side implementation
- **[waltid-crypto](../../crypto/waltid-crypto/README.md)** - Cryptographic key operations
- **[waltid-openid4vp-wallet](../waltid-openid4vp-wallet/README.md)** - Wallet-side OpenID4VP implementation

## Documentation

- [OpenID for Verifiable Credential Issuance 1.0 Specification](https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html)
- [OAuth 2.0 Authorization Framework](https://datatracker.ietf.org/doc/html/rfc6749)
- [walt.id Documentation](https://docs.walt.id)

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
* Find more indepth documentation on our [docs site](https://docs.walt.id)

## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

<div align="center">
<img src="../../../assets/walt-banner.png" alt="walt.id banner" />
</div>
