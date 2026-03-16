# walt.id OpenID4VCI Wallet Library

Kotlin Multiplatform library implementing the **Wallet (Holder)** role in the OpenID for Verifiable Credential
Issuance (OpenID4VCI) 1.0 protocol.

## Overview

This library provides a complete implementation of the wallet-side OpenID4VCI flow, allowing applications to:

- Parse and resolve credential offers
- Discover issuer metadata
- Perform OAuth 2.0 authorization flows (authorization code & pre-authorized code)
- Generate cryptographic proofs of possession
- Request and receive verifiable credentials

## Architecture

The library follows a modular design based on the OpenID4VCI 1.0 specification:

```
waltid-openid4vci-wallet
├── oauth/                   # OAuth 2.0 client components (RFC 6749)
├── offer/                   # Credential offer parsing (§4)
├── metadata/                # Issuer metadata discovery (§11.2)
├── authorization/           # Authorization requests (§5)
├── token/                   # Token exchange (§6)
├── proof/                   # Proof of possession (§7.2)
├── credential/              # Credential requests (§7)
├── nonce/                   # Nonce management (§7.2.1)
├── deferred/                # Deferred issuance (§9)
└── notification/            # Notification endpoint (§10)
```

## Dependencies

- `waltid-openid4vci`: Shared protocol models (CredentialOffer, CredentialIssuerMetadata, etc.)
- `waltid-crypto`: Cryptographic key operations and proof signing
- `ktor-client`: HTTP client for API calls
- `kotlinx-serialization`: JSON serialization
- `kotlinx-datetime`: Timestamp handling
- `kotlinx-coroutines`: Async operations

## Key Components

### OAuth 2.0 Client (`oauth/`)

- **`ClientConfiguration`**: OAuth 2.0 client identity and redirect URIs
- **`PKCEManager`**: PKCE (RFC 7636) code verifier/challenge generation
- **`StateManager`**: CSRF state generation and validation

### Credential Offer Handling (`offer/`)

- **`CredentialOfferParser`**: Parses `openid-credential-offer://` URLs
- **`CredentialOfferResolver`**: Fetches credential offers from URIs

### Metadata Discovery (`metadata/`)

- **`IssuerMetadataResolver`**: Fetches issuer and authorization server metadata from `.well-known` endpoints
- **`OfferedCredentialResolver`**: Matches offered credentials against issuer capabilities

### Authorization (`authorization/`)

- **`AuthorizationRequestBuilder`**: Constructs OAuth 2.0 authorization requests with OpenID4VCI extensions
    - Supports `authorization_details` (RFC 9396)
    - PKCE support
    - Pushed Authorization Requests (PAR)
- **`AuthorizationResponseParser`**: Parses authorization responses and validates state

### Token Exchange (`token/`)

- **`TokenRequestBuilder`**: Exchanges codes for access tokens
    - Authorization code grant
    - Pre-authorized code grant
    - Parses `c_nonce` for proof generation

### Proof of Possession (`proof/`)

- **`ProofOfPossessionBuilder`**: Interface for proof generation
- **`JwtProofBuilder`**: Generates JWT proofs (§7.2.1)
    - Supports DID-based binding (kid header)
    - Supports JWK-based binding (jwk header)
    - Implements `openid4vci-proof+jwt` type

## Usage Example

```kotlin
import id.waltid.openid4vci.wallet.*
import id.waltid.openid4vci.wallet.oauth.ClientConfiguration
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

    // 8. Request credential (implementation in progress)
    // ...
}
```

## Implementation Status

### ✅ Completed Components

- [x] Build configuration (KMP setup)
- [x] OAuth 2.0 client components (ClientConfiguration, PKCEManager, StateManager)
- [x] Credential offer parsing and resolution (WF-01)
- [x] Issuer metadata discovery (WF-02)
- [x] Offered credential resolution (WF-03)
- [x] Authorization request builder (WF-04)
- [x] Authorization response parser (WF-04)
- [x] Token request builder and response parser (WF-05)
- [x] Proof of possession builder interface (WF-06)
- [x] JWT proof builder implementation (WF-06)

### 🚧 In Progress / Planned

- [ ] CWT proof builder (WF-06)
- [ ] Credential request builder (WF-07)
- [ ] Credential response parser (WF-07)
- [ ] Nonce client (WF-08)
- [ ] Deferred credential client (WF-09)
- [ ] Notification client (WF-10)
- [ ] Main wallet issuance orchestrator (WF-11)
- [ ] Batch credential issuance support
- [ ] Credential response encryption/decryption

## Specification Compliance

This library implements:

- **OpenID for Verifiable Credential Issuance 1.0
  ** ([spec](https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html))
- **RFC 6749**: OAuth 2.0 Authorization Framework
- **RFC 7636**: Proof Key for Code Exchange (PKCE)
- **RFC 9396**: OAuth 2.0 Rich Authorization Requests (authorization_details)

## Platform Support

- **JVM** (Java, Kotlin, Android)
- **JS** (Browser, Node.js)
- **Native** (iOS, macOS - if enabled)

## License

Apache License 2.0

## Contributing

Contributions are welcome! This library is part of the walt.id identity infrastructure project.

## Related Libraries

- `waltid-openid4vci`: Issuer-side OpenID4VCI implementation
- `waltid-openid4vp-wallet`: Wallet-side OpenID4VP implementation
- `waltid-crypto`: Cryptographic key operations
