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

The [Issuer2-to-Wallet2 walkthrough](../../../waltid-services/waltid-wallet-api2/examples/issuer2-batch.md)
shows all four OSS/Enterprise management contracts feeding the public wallet endpoints,
including both grants and explicit holder copies.

## Batch and multi-credential issuance

The full receive endpoints default to one instance per offered configuration.
Pass `credentials` to select configurations and `holderBindings` to request
multiple instances:

```json
{
  "offerUrl": "https://issuer.example/offer",
  "credentials": [{
    "credentialConfigurationId": "identity_credential",
    "holderBindings": [{"keyId": "holder-1"}, {"keyId": "holder-2"}]
  }]
}
```

Replace the offer URL, configuration and key IDs with real values. Keys must
already be available to the wallet. Enterprise uses `keyReference` (an attached
KMS resource path) in each holder binding instead of OSS `keyId`.

- Resolve the offer through `resolve-offer/batch` first and check `batchSize`; absence means no batch support.
- One selection is one configuration/dataset. Different formats or datasets use
  separate requests, sharing the access token.
- The full handlers use token `authorizationDetails` and `scope` to determine
  granted targets. Returned dataset identifiers are never replaced by config IDs.
- Authorization parameters are selected automatically from metadata in both flows:
  prefer `authorization_details` when the authorization server advertises
  `openid_credential`; otherwise use the selected configurations' advertised scopes.
  Authorization-code issuance requires one of these selectors; an offered pre-authorized
  code already authorizes its credentials and works without either selector.
  Multiple datasets under one configuration require dataset identifiers.
  Isolated request-token also negotiates automatically when given `credentialIssuer`
  and `credentialConfigurationIds`; explicit `authorizationDetails` or `scope` remain
  available for callers constructing that protocol step themselves.
- Authorization URL generation accepts `credentialConfigurationIds`. Supply
  either an offer or `credentialIssuer` for wallet-initiated authorization.
- Authorized receive accepts plural `credentials` selections. Existing single-copy
  REST callers can still supply `credentialConfigurationId`; supplying both is rejected.
- Isolated sign-proof returns `proofJwt` for one proof and `proofs.jwt` for multiple
  proofs. Pass the returned field to fetch-credential. Pass `credentialIdentifier` when the token returned it;
  `credentialConfigurationId` is still needed locally for metadata, but only
  one selector is sent to the issuer. Multi-proof fetch also requires
  `credentialIssuerBaseUrl` to validate the issuer's batch limit.
- When storing an isolated response, include a holder binding for every proof.
  Responses may reorder instances or return fewer; keys are matched from the
  credential's holder binding, not array position.
- Full receive results carry `deferredCredentials`, each with an opaque
  `deferredCredentialId`. List pending handles with
  `GET /wallet/{walletId}/credentials/receive/deferred`, and resume one with
  `POST /wallet/{walletId}/credentials/receive/deferred/{deferredCredentialId}`.
  These routes use the wallet's retained context; clients do not resend access tokens.
  The resume body has `type` equal to `stored`, `deferred`, `failed`, or `cancelled`.
  Preserve both saved IDs and pending handles in mixed or failed outcomes.
- With OSS SQL persistence enabled, store-backed holder bindings and deferred
  responses awaiting local storage survive service recreation. In-memory deployments
  have process-local continuation semantics. Storage-producing requests require stored
  holder keys. Keep them available until completion; a missing or changed key stops
  resumption before polling.
- Retained handles persist the next allowed poll time from the issuer's `interval`.
  An early resume returns a `deferred` outcome with the remaining seconds rounded up,
  without contacting the issuer or reserving usage. Restart does not reset the deadline;
  a new pending response updates it. Received batches awaiting only local storage have
  no polling interval and can be retried immediately. Isolated polling remains a
  caller-managed step: callers must respect the interval returned by the issuer.
- Retained deferred polls are claimed with a conditional database update before contacting
  the issuer. `REMOTE_OUTCOME_UNCERTAIN` means a request is still running or its response
  was lost; that handle is not automatically polled again, including after restart.
  A received response whose checkpoint failed can still be saved by the surviving runtime.
  A process that lost that response needs issuer reconciliation, not a blind retry.
- Received batches are also claimed before local writes or storage callbacks.
  `STORAGE_OUTCOME_UNCERTAIN` means another writer is saving, or stopped without
  releasing its claim. The outcome includes any credential IDs already readable
  from storage. It does not automatically take over or repeat callbacks. Caught
  storage failures and cancellations release their own claim and retain the received
  response; resuming then saves only missing IDs, without another issuer request.
- Full pre-authorized and authorization-code receive also checkpoint immediate batches
  before saving. A storage failure returns `storageOutcome` with local-save handles,
  alongside the full flow's `credentialIds`, issuer-deferred targets and target failure.
  Resume these handles through the same route, even when no credential was saved
  (HTTP 207). The top-level IDs cover the whole flow; IDs inside `storageOutcome`
  cover only the stopped batch and must not be counted twice. Local-save handles
  have no issuer transaction ID and do not appear in `deferredTransactionIds`.
- Isolated fetch with `storeInWallet=true` also returns `storageOutcome`. A partial
  save returns HTTP 207 with the original `rawCredentials`, committed IDs, and a
  retained handle in that outcome's `deferredCredentials`. Resume the handle through
  the same deferred-resume route; it saves only missing IDs without another issuer
  request. A local-save handle has no polling interval. Quota rejection also retains
  the validated response. Persistence requires the configured continuation store;
  if its initial checkpoint fails, only the surviving runtime retains the response.
- Isolated fetch carries `deferredCredential` and `interval`. For the isolated
  `POST /wallet/{walletId}/credentials/receive/deferred` step, retain the original
  access token and holder keys, and copy `holderBindings` and `proofRequired` from
  the fetch result, including `credentialIdentifier` when supplied. No private keys
  are included in deferred results. A received response whose local save fails returns
  HTTP 207 with saved IDs and `storageOutcome`; resume its retained handle without
  sending the transaction to the issuer again. If the original isolated request omitted
  its configuration ID, the local-save handle retains a null configuration ID.

`resolve-offer` retains its released offer shape. `resolve-offer/batch` returns `{ "offer": { ... }, "batchSize": 2 }`, sharing the same offer projection and adding the issuer batch limit. An absent limit permits one holder binding.

The isolated `request-token` and `exchange-code` routes retain their released three-field token result. Append `/batch` to either route to receive granted `authorizationDetails` and `scope` as well. The same wallet ownership checks apply to both contracts; choose one route before redeeming the code.

Swagger includes ordered single, batch, multi-configuration, and authorization
examples. Issuance does not implicitly generate keys or increase batch size.

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
| `POST` | `/wallet/{walletId}/credentials/receive/resolve-offer` | Isolated: resolve offer (enriched with issuer/credential display metadata) |
| `POST` | `/wallet/{walletId}/credentials/receive/request-token` | Isolated: exchange code for token |
| `POST` | `/wallet/{walletId}/credentials/receive/sign-proof` | Isolated: sign proof-of-possession |
| `POST` | `/wallet/{walletId}/credentials/receive/fetch-credential` | Isolated: fetch credential |
| `POST` | `/wallet/{walletId}/credentials/receive/reject` | Isolated: reject fetched credential (`credential_deleted`) |
| `POST` | `/wallet/{walletId}/credentials/receive/authorization-url` | Auth-code: generate redirect URL |
| `POST` | `/wallet/{walletId}/credentials/receive/exchange-code` | Auth-code: exchange code for token |
| `GET` | `/wallet/{walletId}/credentials/receive/deferred` | List retained deferred handles |
| `POST` | `/wallet/{walletId}/credentials/receive/deferred/{deferredCredentialId}` | Resume a retained handle without resending tokens |
| `POST` | `/wallet/{walletId}/credentials/receive/deferred` | Poll deferred credential |

### Presentation (OpenID4VP 1.0)

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/wallet/{walletId}/credentials/present` | Full DCQL presentation flow |
| `POST` | `/wallet/{walletId}/credentials/present/isolated` | Stateless with inline credentials |
| `POST` | `/wallet/{walletId}/credentials/present/resolve-request` | Isolated: resolve VP request |
| `POST` | `/wallet/{walletId}/credentials/present/match-credentials` | Isolated: DCQL match inline credentials |
| `POST` | `/wallet/{walletId}/credentials/present/match-credentials-from-store` | DCQL match from wallet stores |
| `POST` | `/wallet/{walletId}/credentials/present/preview` | Stateless consent preview (returns authorizationRequest) |
| `POST` | `/wallet/{walletId}/credentials/present/build-vp-token` | Build signed VP from selections |
| `POST` | `/wallet/{walletId}/credentials/present/send-response` | Send authorization response to verifier |
| `POST` | `/wallet/{walletId}/credentials/present/reject` | Reject via requestUrl (no preview handle) |

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

Wallet deletion closes the shared issuance runtime before removing its stores. Late offer
resolution or credential responses cannot retain new handles or save credentials through
that runtime. An active retained transition or local save rejects deletion until it finishes.
Resolvers must share one `WalletIssuanceSessionState` per wallet across requests. The OSS
persistence adapter also checks wallet existence inside its continuation write transaction.
