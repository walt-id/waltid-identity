<div align="center">
<h1>walt.id Wallet SDK - Server Library</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>Shared Ktor route handlers and OpenAPI documentation for the walt.id Wallet SDK</p>

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

This library provides shared Ktor HTTP route handlers for the walt.id Wallet SDK. It exposes the wallet functionality from [waltid-openid4vc-wallet](../waltid-openid4vc-wallet) as REST API endpoints with OpenAPI documentation.

Both the OSS wallet service and the Enterprise wallet service use this library, ensuring API consistency across deployments.

## Features

- **Wallet CRUD** — Create, list, get, delete wallets
- **Key Management** — Generate, import, list, delete keys
- **DID Management** — Create, import, list, delete DIDs
- **Credential Management** — Import, list, get, delete credentials
- **OpenID4VCI Issuance** — Full and isolated step-by-step flows
- **OpenID4VP Presentation** — Full and isolated step-by-step flows
- **Named Stores** — Create and manage shared storage backends
- **OpenAPI Documentation** — Auto-generated Swagger UI

## Installation

Add the dependency to your `build.gradle.kts`:

```kotlin
repositories {
    maven("https://maven.waltid.dev/releases")
}

dependencies {
    implementation("id.walt.protocols:waltid-openid4vc-wallet-server:<version>")
}
```

## Usage

### Register Routes

```kotlin
import id.walt.wallet2.server.handlers.Wallet2RouteHandler.registerWallet2Routes
import id.walt.wallet2.server.WalletResolver
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        // Create a wallet resolver (in-memory for development)
        val resolver = InMemoryWalletResolver()
        
        // Register all wallet routes
        registerWallet2Routes(resolver)
    }
}
```

### With Authentication

```kotlin
routing {
    authenticate("auth-session") {
        registerWallet2Routes(
            resolver = resolver,
            getAccountId = { call.principal<UserSession>()?.accountId }
        )
    }
}
```

## API Endpoints

### Wallet Management

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/wallet` | Create a new wallet |
| `GET` | `/wallet` | List wallet IDs |
| `GET` | `/wallet/{walletId}` | Get wallet info |
| `DELETE` | `/wallet/{walletId}` | Delete a wallet |

### Key Management

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/wallet/{walletId}/keys` | List keys |
| `POST` | `/wallet/{walletId}/keys/generate` | Generate a new key |
| `POST` | `/wallet/{walletId}/keys/import` | Import an existing key |
| `GET` | `/wallet/{walletId}/keys/{keyId}` | Get key metadata |
| `DELETE` | `/wallet/{walletId}/keys/{keyId}` | Delete a key |

### DID Management

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/wallet/{walletId}/dids` | List DIDs |
| `POST` | `/wallet/{walletId}/dids/create` | Create a DID |
| `POST` | `/wallet/{walletId}/dids/import` | Import a DID |
| `GET` | `/wallet/{walletId}/dids/{did}` | Get a DID entry |
| `DELETE` | `/wallet/{walletId}/dids/{did}` | Delete a DID |

### Credential Management

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/wallet/{walletId}/credentials` | List credentials (metadata only) |
| `POST` | `/wallet/{walletId}/credentials/import` | Import a raw credential |
| `GET` | `/wallet/{walletId}/credentials/{credentialId}` | Get credential with data |
| `DELETE` | `/wallet/{walletId}/credentials/{credentialId}` | Delete a credential |

### Issuance (OpenID4VCI 1.0)

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/wallet/{walletId}/credentials/receive` | Full pre-authorized code flow |
| `POST` | `/wallet/{walletId}/credentials/receive/resolve-offer` | Isolated: resolve offer |
| `POST` | `/wallet/{walletId}/credentials/receive/request-token` | Isolated: exchange code for token |
| `POST` | `/wallet/{walletId}/credentials/receive/sign-proof` | Isolated: sign proof-of-possession |
| `POST` | `/wallet/{walletId}/credentials/receive/fetch-credential` | Isolated: fetch credential |
| `POST` | `/wallet/{walletId}/credentials/receive/authorization-url` | Auth-code: generate redirect URL |
| `POST` | `/wallet/{walletId}/credentials/receive/exchange-code` | Auth-code: exchange code for token |
| `POST` | `/wallet/{walletId}/credentials/receive/deferred` | Poll deferred credential |

### Presentation (OpenID4VP 1.0)

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/wallet/{walletId}/credentials/present` | Full DCQL presentation flow |
| `POST` | `/wallet/{walletId}/credentials/present/isolated` | Stateless with inline credentials |
| `POST` | `/wallet/{walletId}/credentials/present/resolve-request` | Isolated: resolve VP request |
| `POST` | `/wallet/{walletId}/credentials/present/match-credentials` | Isolated: DCQL match inline credentials |
| `POST` | `/wallet/{walletId}/credentials/present/match-credentials-from-store` | DCQL match from wallet stores |

### Named Store Management

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/stores/keys` | List named key store IDs |
| `POST` | `/stores/keys/{storeId}` | Create a named key store |
| `GET` | `/stores/credentials` | List named credential store IDs |
| `POST` | `/stores/credentials/{storeId}` | Create a named credential store |
| `GET` | `/stores/dids` | List named DID store IDs |
| `POST` | `/stores/dids/{storeId}` | Create a named DID store |

## WalletResolver Interface

Implement `WalletResolver` to provide your own wallet storage backend:

```kotlin
interface WalletResolver {
    suspend fun resolveWallet(walletId: String): Wallet?
    suspend fun storeWallet(wallet: Wallet)
    suspend fun deleteWallet(walletId: String)
    suspend fun listWalletIds(): List<String>
    
    // Named store management
    suspend fun resolveKeyStore(storeId: String): WalletKeyStore?
    suspend fun resolveCredentialStore(storeId: String): WalletCredentialStore?
    suspend fun resolveDidStore(storeId: String): WalletDidStore?
    // ... store management methods
    
    // Account linking (for multi-tenant deployments)
    suspend fun linkWalletToAccount(accountId: String, walletId: String)
    suspend fun getWalletIdsForAccount(accountId: String): List<String>?
}
```

## Related Libraries

- **[waltid-openid4vc-wallet](../waltid-openid4vc-wallet)** — Core wallet library
- **[waltid-openid4vc-wallet-persistence](../waltid-openid4vc-wallet-persistence)** — SQL-backed stores
- **[waltid-service-commons](../../../waltid-services/waltid-service-commons)** — Service utilities

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
* Find more in-depth documentation on our [docs site](https://docs.walt.id)

## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

<div align="center">
<img src="../../../assets/walt-banner.png" alt="walt.id banner" />
</div>
