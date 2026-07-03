# walt.id Core Wallet Module

by [walt.id](https://walt.id)

Kotlin Multiplatform wallet library for OpenID4VCI 1.0 credential issuance and OpenID4VP 1.0 credential presentation



## Status

  
*This project is being actively maintained by the development team at walt.id.*  
*Regular updates, bug fixes, and new features are being added.*

## Overview

This library provides the core wallet functionality for building identity wallets that support OpenID4VCI 1.0 (credential issuance) and OpenID4VP 1.0 (credential presentation). It is designed as a framework-agnostic, multiplatform library that can be used in mobile apps, web applications, and server-side wallet services.

## Features

### Credential Issuance (OpenID4VCI 1.0)

- **Pre-authorized code grant** — Full flow from offer to stored credential
- **Authorization code grant** — PKCE-enabled OAuth flow with user authentication
- **Deferred issuance** — Poll for credentials that are issued asynchronously
- **Proof of possession** — JWT-based key binding proofs
- **Multiple credential formats** — W3C VC, SD-JWT, mdoc/mDL

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

val result = WalletIssuanceHandler.receiveCredential(wallet, request) { event ->
    println("Issuance event: $event")
}

println("Received ${result.credentialIds.size} credential(s)")
```

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

- Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
- Get help, request features and report bugs: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
- Find more in-depth documentation on our [docs site](https://docs.walt.id)

## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

