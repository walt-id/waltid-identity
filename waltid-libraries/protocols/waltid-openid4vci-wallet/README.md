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
import id.waltid.openid4vci.wallet.nonce.NonceRequestBuilder
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

    // 7. Obtain a fresh proof nonce when the issuer advertises a Nonce Endpoint
    val nonce = issuerMetadata.nonceEndpoint?.let { nonceEndpoint ->
        NonceRequestBuilder(httpClient).requestNonce(nonceEndpoint).cNonce
    }

    // 8. Generate proof of possession, omitting the nonce claim when none was obtained
    val key = KeyManager.loadKey("my-key-id")
    val proofBuilder = JwtProofBuilder()
    val proof = proofBuilder.buildProof(
        key = key,
        audience = offer.credentialIssuer,
        nonce = nonce
    )

    // 9. Request credential (Implementation coming soon)
    // ...
}
```

## Key Source Files

- `oauth/` - OAuth 2.0 client components (PKCE, State management)
- `offer/` - Credential offer parsing and resolution
- `metadata/` - Issuer and authorization server metadata discovery
- `nonce/` - OpenID4VCI 1.0 Nonce Endpoint requests
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
* Find more in-depth documentation on our [docs site](https://docs.walt.id)

## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

<div align="center">
<img src="../../../assets/walt-banner.png" alt="walt.id banner" />
</div>


## Client attestation challenges

The challenge behavior follows the [OAuth Attestation-Based Client Authentication specification](https://datatracker.ietf.org/doc/html/draft-ietf-oauth-attestation-based-client-auth-07).

`ClientAttestationChallengeContext` retains an opaque attestation challenge and a separate authorization-server DPoP nonce. Scope a context to one issuance flow, wallet instance, authorization server, client ID and attestation key. Call `prepare(httpClient, metadata.challengeEndpoint)` before building the first PoP. It sends an empty POST with `Accept: application/json` once for each new issuance flow when an endpoint is advertised, even if an older challenge is remembered. A resumed flow retains its preparation state and uses newer response-header challenges without refetching. An absent endpoint allows response-only negotiation. `requestChallengeDetailed` also exposes an optional `DPoP-Nonce`; the original `requestChallenge` response API remains available.

For PAR and token requests, supply a `ClientAttestationHeadersFactory` that signs a fresh PoP using `context.challenge`, and pass `context::observe` as `onResponseHeaders`. Keep the reusable client-attestation JWT outside the factory. The callback observes successful and error responses before another proof is signed. The PoP audience is the authorization server issuer identifier. Challenges are not decoded, expired locally, or assumed to be single-use.

An HTTP 400 `use_attestation_challenge` with a nonblank `OAuth-Client-Attestation-Challenge` permits one retry with a new PoP. OAuth parameters (including the grant, PKCE verifier and transaction code) remain unchanged. Missing/blank challenge headers, unrelated/malformed errors, cancellation and transport failures do not trigger this recovery. The existing static-header overloads remain available and cannot regenerate a proof, so they do not perform challenge recovery.

Token requests independently allow one existing DPoP nonce recovery and one attestation recovery, in either order: at most three logical attempts. Redirect handling retains its existing restrictions. Authorization-server DPoP nonces and credential-server nonces remain separate. Resource-server attestation and additional authentication methods are outside this feature.

The common tests cover discovery, header updates, signed PAR proofs, bounded recovery, both DPoP/attestation orders, cancellation and static-header compatibility. Wallet and native SDK end-to-end coverage is described in the Wallet2 and mobile READMEs.

PAR, token and challenge-endpoint requests explicitly disable generic HTTP retries and Ktor `expectSuccess`, so error responses reach the protocol handler even with a caller-supplied client. Each authenticated retry is controlled by the protocol layer.

The challenge endpoint must return a nonblank string `attestation_challenge`; a DPoP nonce alone is not sufficient for client attestation. An accompanying `DPoP-Nonce` remains independent freshness for DPoP. `resolveAuthorizationServerMetadataDetailed` exposes final response headers after issuer validation, retaining the latest nonblank challenge and DPoP nonce across OAuth-to-OIDC metadata fallback. Authorization-server discovery disables generic HTTP retries and `expectSuccess` so error responses remain visible to this fallback handling.
