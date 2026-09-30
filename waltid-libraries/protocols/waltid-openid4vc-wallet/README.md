<div align="center">
<h1>walt.id Core Wallet Module</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>Kotlin Multiplatform wallet library for OpenID4VCI 1.0 credential issuance and OpenID4VP 1.0 credential presentation</p>

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

## Overview

This library provides the core wallet functionality for building identity wallets that support OpenID4VCI 1.0 (credential issuance) and OpenID4VP 1.0 (credential presentation). It is designed as a framework-agnostic, multiplatform library that can be used in mobile apps, web applications, and server-side wallet services.

## Features

### Credential Issuance (OpenID4VCI 1.0)

- **Pre-authorized code grant** — Full flow from offer to stored credential
- **Authorization code grant** — PKCE-enabled OAuth flow with user authentication
- **Deferred issuance** — Poll for credentials that are issued asynchronously
- **Proof of possession** — JWT-based key binding proofs
- **Multiple credential formats** — W3C VC, SD-JWT, mdoc/mDL

For isolated proof signing, `signProof(SignProofRequest)` retains the single-JWT SDK contract.
Use `signProofs(SignProofsRequest)` for explicit holder collections; its `SignProofsResult.proofs`
contains the complete collection. Both entry points share the same proof validation and signing logic.
On the wire, one JWT keeps the released `{"proofJwt":"..."}` response; multiple JWTs use
`{"proofs":{"jwt":["...","..."]}}`. The plural result decoder accepts either shape.

### Credential Presentation (OpenID4VP 1.0)

- **DCQL matching** — Digital Credentials Query Language for credential selection
- **Multiple response modes** — direct_post, direct_post.jwt
- **Format-specific presenters** — JWT VP, SD-JWT KB, mdoc DeviceResponse
- **Holder policies** — Configurable policies for presentation consent

### Wallet Architecture

- **Multi-store support** — Multiple key stores, credential stores per wallet
- **Pluggable storage** — Interface-based design for custom backends
- **In-memory stores** — Default implementations for development and testing
- **Static key/DID fallback** — Support for store-less isolated flows

## Installation

Add the dependency to your `build.gradle.kts`:

```kotlin
repositories {
    maven("https://maven.waltid.dev/releases")
}

dependencies {
    implementation("id.walt.protocols:waltid-openid4vc-wallet:<version>")
}
```

## Architecture

```
waltid-openid4vc-wallet
├── data/                    # Core data models
│   ├── Wallet.kt           # Wallet instance with stores
│   ├── StoredCredential.kt # Credential storage model
│   ├── WalletKeyStore.kt   # Key store interface
│   ├── WalletCredentialStore.kt
│   └── WalletDidStore.kt
├── handlers/                # Protocol handlers
│   ├── WalletIssuanceHandler.kt   # OpenID4VCI flows
│   └── WalletPresentationHandler.kt # OpenID4VP flows
└── stores/inmemory/         # Default in-memory implementations
```

## Usage

### Creating a Wallet

```kotlin
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.stores.inmemory.InMemoryKeyStore
import id.walt.wallet2.stores.inmemory.InMemoryCredentialStore
import id.walt.wallet2.stores.inmemory.InMemoryDidStore

val wallet = Wallet(
    id = "my-wallet",
    keyStores = listOf(InMemoryKeyStore()),
    credentialStores = listOf(InMemoryCredentialStore()),
    didStore = InMemoryDidStore()
)
```

### Receiving Credentials (OpenID4VCI 1.0)

```kotlin
import id.walt.wallet2.handlers.WalletIssuanceHandler
import id.walt.wallet2.handlers.ReceiveCredentialRequest
import io.ktor.http.Url

// Full pre-authorized code flow
val request = ReceiveCredentialRequest(
    offerUrl = Url("openid-credential-offer://?credential_offer=..."),
    txCode = "123456" // PIN if required
)

val result = WalletIssuanceHandler.receiveCredentials(wallet, request, onEvent = { event ->
    println("Issuance event: $event")
})

println("Received ${result.credentialIds.size} credential(s)")
```

`receiveCredentials` and `receiveCredentialsAuthCode` return `ReceiveCredentialsResult`, retaining
all deferred targets and partial progress. The released `receiveCredential` and
`receiveCredentialAuthCode` APIs retain `ReceiveCredentialResult` and its non-null transaction map.
When progress cannot fit that map or issuance stops partway, they throw `CredentialReceiveException`
with the complete detailed result; do not redeem the grant again. REST requests with explicit
`credentials` selections and `/authorized/batch` use the detailed result. Omitted pre-authorized
selections and `/authorized` retain the released successful response shape.

For batches, supply `WalletCredentialSelection` with one distinct stored holder key per copy:

```kotlin
val selections = listOf(WalletCredentialSelection(
    "identity", holderBindings = listOf(CredentialHolderBinding(keyId = "holder-1"), CredentialHolderBinding(keyId = "holder-2"))))
val result = WalletIssuanceHandler.receiveCredentials(wallet, request.copy(credentials = selections))
```

See the [Wallet2 batch guide](https://docs.walt.id/community-stack/wallet2/credential-receiving/batch-issuance)
for grants, dataset identifiers, partial results and continuation handling.
Storage-producing flows require wallet-owned keys; isolated proof signing can use inline keys.
Released result APIs keep their successful wire shapes and throw progress-bearing exceptions when
that contract cannot represent a result. Use the detailed APIs and `listIssuanceContinuations()`
for new consumers. Pending 1.1.0 records remain resumable with their saved key, but cannot recheck
configuration/proof constraints that the old record did not retain.

When issuer metadata requires a key attestation, attach a `KeyAttestationProvider` to the wallet before issuance. The provider receives the actual proof key's public JWK, the credential issuer, the current nonce, and any advertised storage or authentication constraints. It returns a signed `key-attestation+jwt` and exposes its public verification key. The wallet checks the signature, key binding, nonce, lifetime, and advertised constraints before placing the attestation in the JWT proof header. The provider is runtime configuration: reattach it after restoring or copying a wallet. Without a provider, a required-attestation request fails before sending the proof. The issuer must independently trust the attester; attaching a provider does not establish issuer trust or certify the key's security properties.

Validation follows [OpenID4VCI 1.0 Appendix D.1](https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0-final.html#appendix-D.1): `typ` must be `key-attestation+jwt`, `iss` is not required, and the proof key may occur anywhere in `attested_keys`. The unhyphenated spelling in a TS3 example is an [acknowledged upstream typo](https://github.com/eu-digital-identity-wallet/eudi-doc-standards-and-technical-specifications/issues/605). This wallet-side validation does not establish full EUDI TS3 compliance, including its first-key signing rule, certificate trust, certification and status requirements.

### Presenting Credentials (OpenID4VP 1.0)

```kotlin
import id.walt.wallet2.handlers.WalletPresentationHandler
import id.walt.wallet2.handlers.PresentCredentialRequest
import io.ktor.http.Url

val request = PresentCredentialRequest(
    requestUrl = Url("openid4vp://?request_uri=...")
)

val result = WalletPresentationHandler.presentCredential(wallet, request) { event ->
    println("Presentation event: $event")
}

println("Presentation submitted to: ${result.redirectUri}")
```

### Isolated Step-by-Step Flows

For UIs that need fine-grained control, each protocol step can be called individually:

```kotlin
// Issuance: resolve offer → show user what's being offered
val offerResult = WalletIssuanceHandler.resolveOffer(
    ResolveOfferRequest(offerUrl = Url("openid-credential-offer://..."))
)
println("Issuer: ${offerResult.credentialIssuer}")
println("Credentials: ${offerResult.offeredCredentials}")

// Presentation: match credentials → show user what will be shared
val matchResult = WalletPresentationHandler.matchCredentialsFromStore(
    wallet,
    MatchCredentialsFromStoreRequest(dcqlQuery = query)
)
println("Matched credentials: ${matchResult.matchedCredentialIds}")
```

## Wallet Data Model

### Wallet

A wallet instance composed of pluggable storage backends:


| Field              | Description                                                       |
| ------------------ | ----------------------------------------------------------------- |
| `keyStores`        | List of key stores (first match wins for lookups)                 |
| `credentialStores` | List of credential stores (first store for writes, all for reads) |
| `didStore`         | Optional DID store                                                |
| `staticKey`        | Fallback key when no key stores are configured                    |
| `staticDid`        | Fallback DID when no DID store is configured                      |


### StoredCredential

```kotlin
data class StoredCredential(
    val id: String,                    // Wallet-assigned ID
    val credential: DigitalCredential, // Parsed credential
    val label: String?,                // Display label
    val addedAt: Instant               // Storage timestamp
)
```

## Session Events

Both handlers emit events for progress tracking:

**Issuance Events:**

- `issuance_offer_resolved`
- `issuance_token_obtained`
- `issuance_proof_signed`
- `issuance_credential_received`
- `issuance_credential_stored`
- `issuance_deferred`
- `issuance_completed`

**Presentation Events:**

- `presentation_request_parsed`
- `presentation_credentials_selected`
- `presentation_response_prepared`
- `presentation_completed`
- `presentation_failed`

## Related Libraries

This library builds on top of several walt.id protocol libraries:

- **[waltid-openid4vci](../waltid-openid4vci)** — OpenID4VCI 1.0 shared types
- **[waltid-openid4vci-wallet](../waltid-openid4vci-wallet)** — OpenID4VCI wallet client
- **[waltid-openid4vp](../waltid-openid4vp)** — OpenID4VP 1.0 core types
- **[waltid-openid4vp-wallet](../waltid-openid4vp-wallet)** — OpenID4VP wallet presenter
- **[waltid-dcql](../../credentials/waltid-dcql)** — DCQL credential matching

### Companion Libraries

- **[waltid-openid4vc-wallet-persistence](../waltid-openid4vc-wallet-persistence)** — SQL-backed store implementations

### Implementations

- **[waltid-openid4vc-wallet-server](../waltid-openid4vc-wallet-server)** — Ktor HTTP route handlers (Wallet API)
- (Wallet SDK for mobile coming soon)

## Supported Platforms


| Platform   | Support                              |
| ---------- | ------------------------------------ |
| JVM        | Full support                         |
| JavaScript | Full support                         |
| iOS        | Available when `enableIosBuild=true` |

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
* Find more indepth documentation on our [docs site](https://docs.walt.id)

## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

<div align="center">
<img src="../../../assets/walt-banner.png" alt="walt.id banner" />
</div>


### Authorization URL contracts

`generateAuthorizationUrl(GenerateAuthorizationUrlRequest)` preserves the released single-configuration contract: it authorizes the first offered configuration, honors `useScope`, and returns a non-null `credentialConfigurationId`.

`generateBatchAuthorizationUrl(GenerateBatchAuthorizationUrlRequest)` authorizes the selected configurations (all offered configurations by default), negotiates authorization details or scopes from metadata, and returns `credentialConfigurationIds`. Both use the same metadata, PKCE and PAR implementation. The wallet-aware overload supplies signing capability and client attestation when required by PAR. REST adapters expose the batch contract at `credentials/receive/authorization-url/batch`; the original `authorization-url` endpoint retains the single contract.


`WalletIssuanceSessionService.start` retains the released `WalletIssuanceSession` and four-field offer preview. `startBatch` returns `WalletIssuanceBatchSession` with the same ID/offer and an additional `batchSize`. Both use one retained-session implementation. Batch acceptance after recreation reads the limit from the persisted issuer-metadata snapshot. The mobile SDK uses the batch result; its Swift facade continues exposing `offer.batchSize`.

`ResolveOfferResult` retains its released shape. Callers needing the batch limit use `resolveOfferDetailed` and read `resolvedIssuerMetadata.metadata.batchCredentialIssuance?.batchSize`; the metadata is already resolved. The REST `/resolve-offer/batch` response wraps the existing offer details with this limit.

`requestToken` and `exchangeCode` retain the released three-field `RequestTokenResult` (`accessToken`, `expiresIn`, `tokenType`). Isolated batch callers use `requestTokenDetailed` and `exchangeCodeDetailed` to retain granted `authorizationDetails` and `scope`. Both delegate to the same token exchange implementation. REST adapters expose the detailed result at `credentials/receive/request-token/batch` and `credentials/receive/exchange-code/batch`; each code is redeemed once through the chosen contract.

`ReceiveAuthorizedCredentialRequest` and `receiveCredentialAuthCode` retain the released required single configuration. Batch continuation uses `ReceiveAuthorizedCredentialsRequest` with a required, non-empty `credentials` list and `receiveCredentialsAuthCode` (or its typed streaming counterpart, `receiveCredentialsAuthCodeFlow`). REST adapters expose this at `credentials/receive/authorized/batch`; the original `authorized` endpoint accepts the released single-target body. Both normalize into the shared target executor, validating caller-supplied credential and nonce endpoints before code redemption.

### Isolated fetch result contracts

`fetchCredential` retains the released `FetchCredentialResult(rawCredentials)` success
contract. Use `fetchCredentials` for `FetchCredentialsResult`, which also carries
remote deferral and local-save progress. Both methods use the same implementation.
If the released result cannot represent pending or failed progress, the old method
throws `CredentialFetchException` containing the complete detailed result. Do not
repeat the issuer request; retain the transaction or resume the local-save handle.
The deferred retry interval is `deferredCredential.intervalSeconds`.

The OSS and Enterprise HTTP adapters keep released success bodies for requests using
only released fields. Fetch selects the detailed result when `proofs`, non-default
`holderBindings`, `credentialIdentifier` or DPoP context is supplied. Poll selects it
when `proofRequired=true`, non-default `holderBindings`, `credentialIdentifier` or
DPoP context is supplied. A legacy poll success remains `ReceiveCredentialResult`.
Legacy pending results return HTTP 409 and local-save failures HTTP 500, with full
progress; detailed requests retain HTTP 200 pending and HTTP 207 partial progress.
