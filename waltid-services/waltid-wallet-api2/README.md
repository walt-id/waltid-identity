<div align="center">
<h1>walt.id Wallet API 2</h1>
 <span>by </span><a href="https://walt.id">walt.id</a>
 <p>The next-generation walt.id Wallet, rebuilt from scratch on a clean KMP-compatible core for OpenID4VCI 1.0 and OpenID4VP 1.0.</p>

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

`waltid-wallet-api2` is a self-contained Ktor-based REST service that lets holders receive, store, and present Verifiable Credentials. It implements the full OpenID4VCI 1.0 issuance protocol and OpenID4VP 1.0 presentation protocol with DCQL, and exposes a straightforward HTTP API for key management, DID management, and credential lifecycle operations.

**Key differences from waltid-wallet-api (v1):**
- Built on the new `waltid-openid4vc-wallet` library (multiplatform core).
- Clean separation: all route logic lives in `waltid-openid4vc-wallet-server`, and is similar to Enterprise to allow for easy later upgrading.
- Pluggable stores: in-memory by default, SQLite/Postgres via the `wallet2-persistence` feature flag.
- Auth is opt-in: enable the `auth` feature for account registration, JWT-based login, and wallet ownership enforcement.

---

## Features

| Area                             | Capability                                                                                                           |
|----------------------------------|----------------------------------------------------------------------------------------------------------------------|
| **Wallet lifecycle**             | Create / get info / list / delete wallets; wallets have independent key, credential, and DID stores                  |
| **Key management**               | Generate (Ed25519, secp256r1, secp256k1, RSA), import from JWK, list, get, delete                                    |
| **DID management**               | Create (`did:key`, `did:jwk`, `did:web`, …), import, list, get, delete                                               |
| **OID4VCI 1.0 - full flow**      | Single `POST /credentials/receive` call: resolve offer → token → proof-of-possession → credential                    |
| **OID4VCI 1.0 - isolated steps** | `resolve-offer`, `request-token`, `sign-proof`, `fetch-credential`, `authorization-url`, `exchange-code`, `deferred` |
| **OID4VP 1.0 / DCQL**            | `POST /credentials/present` (full flow); isolated `resolve-request`, `match-credentials-from-store`                  |
| **Credential store**             | Import raw credentials, list (metadata), get, delete; formats: SD-JWT VC, JWT VC JSON, mdoc                          |
| **Named stores**                 | Create independently-named key/credential/DID stores, reference them when creating wallets                           |
| **Shared stores**                | Multiple wallets can share a single named credential store                                                           |
| **Static-key wallet**            | Wallet configured with an embedded key JWK - no key-store required                                                   |
| **Auth (optional)**              | Account register/login (email+password), JWT sessions, wallet ownership enforcement, multi-account isolation         |
| **Persistence (optional)**       | SQLite (default) or Postgres via Exposed; in-memory when disabled                                                    |
| **OpenAPI / Swagger**            | Full OpenAPI 3 spec at `/swagger/index.html`                                                                         |

---

## Quick start

### 1. Build from source

```bash
# From the repository root
./gradlew :waltid-services:waltid-wallet-api2:run
```

The service starts on **port 7005** by default (configured in `config/web.conf`).

```bash
# Verify it's running
curl http://localhost:7005/wallet
# → []
```

### 2. Build a runnable fat-JAR

```bash
./gradlew :waltid-services:waltid-wallet-api2:buildFatJar
```

```bash
java -jar waltid-services/waltid-wallet-api2/build/libs/waltid-wallet-api2-all.jar
```

---

## Docker

### Build the image

The project uses the **Ktor Gradle Docker plugin** (`io.ktor.plugin`) with `jib` under the hood. Two Gradle tasks build the Docker image:

```bash
# Build a tarball of the image (does not require a running Docker daemon)
./gradlew :waltid-services:waltid-wallet-api2:buildImage

# Build and load directly into the local Docker daemon  ← recommended for local use
./gradlew :waltid-services:waltid-wallet-api2:publishImageToLocalRegistry
```

The resulting image is tagged `waltid/wallet-api2:<version>` and `waltid/wallet-api2:latest`.

```bash
docker images | grep wallet-api2
# waltid/wallet-api2   latest   ...   457MB
```

### Run with Docker

The service needs its `config/` directory mounted because it contains the required `wallet-service.conf` (the `publicBaseUrl`) and feature-flag configuration:

```bash
docker run --rm \
  -p 7005:7005 \
  -v "$(pwd)/waltid-services/waltid-wallet-api2/config:/waltid-wallet-api2/config" \
  waltid/wallet-api2:latest
```

> **Port note:** `web.conf` sets `webPort = 7005`. The Ktor plugin's Docker port mapping is 4000→4000 (used for `runDocker`); when running manually with `docker run` use `-p 7005:7005` to match `web.conf`.

#### With SQLite persistence

The default `config/_features.conf` already enables `wallet2-persistence`. Mount a data directory so the SQLite file survives container restarts:

```bash
mkdir -p wallet2-data

docker run --rm \
  -p 7005:7005 \
  -v "$(pwd)/waltid-services/waltid-wallet-api2/config:/waltid-wallet-api2/config" \
  -v "$(pwd)/wallet2-data:/waltid-wallet-api2/data" \
  waltid/wallet-api2:latest
```

#### With Postgres

Override `wallet2-persistence.conf` by placing a custom one in your mounted config directory:

```
wallet2-persistence {
    jdbcUrl       = "jdbc:postgresql://db:5432/wallet2?user=wallet&password=secret"
    driverClassName = "org.postgresql.Driver"
}
```

```bash
docker run --rm \
  -p 7005:7005 \
  -v "$(pwd)/my-config:/waltid-wallet-api2/config" \
  --network my-net \
  waltid/wallet-api2:latest
```

#### With auth enabled

Add `auth` to `enabledFeatures` in `_features.conf`, and add an `auth.conf`:

```hocon
# config/_features.conf
enabledFeatures = [
    auth
    wallet2-persistence
]

# config/auth.conf
jwtSecret = "change-this-secret-in-production-min-32-chars"
```

---

## Usage examples

All examples assume the service is running on `localhost:7005`.

### Wallet lifecycle

```bash
# Create a wallet (auto-creates one in-memory key, credential, and DID store)
curl -s -X POST http://localhost:7005/wallet \
  -H "Content-Type: application/json" -d '{}'
# → {"walletId":"9bbbc42d-..."}

WALLET_ID="9bbbc42d-..."

# List all wallets
curl http://localhost:7005/wallet
# → ["9bbbc42d-..."]

# Get wallet info (shows attached store counts)
curl http://localhost:7005/wallet/$WALLET_ID
# → {"walletId":"...","keyStoreCount":1,"credentialStoreCount":1,"hasDidStore":true,...}

# Delete a wallet
curl -X DELETE http://localhost:7005/wallet/$WALLET_ID
```

### Key management

```bash
# Generate an Ed25519 key
curl -s -X POST http://localhost:7005/wallet/$WALLET_ID/keys/generate \
  -H "Content-Type: application/json" -d '{"keyType":"Ed25519"}'
# → {"keyId":"v_CW0x...","keyType":"Ed25519"}

KEY_ID="v_CW0x..."

# Generate a P-256 key
curl -s -X POST http://localhost:7005/wallet/$WALLET_ID/keys/generate \
  -H "Content-Type: application/json" -d '{"keyType":"secp256r1"}'

# Import a key from waltid JWK format {"type":"jwk","jwk":{...}}
curl -s -X POST http://localhost:7005/wallet/$WALLET_ID/keys/import \
  -H "Content-Type: application/json" \
  -d '{"key":{"type":"jwk","jwk":{"kty":"OKP","crv":"Ed25519","x":"...","d":"..."}}}'

# List keys
curl http://localhost:7005/wallet/$WALLET_ID/keys

# Delete a key
curl -X DELETE http://localhost:7005/wallet/$WALLET_ID/keys/$KEY_ID
```

### DID management

```bash
# Create a did:key from an existing key
curl -s -X POST http://localhost:7005/wallet/$WALLET_ID/dids/create \
  -H "Content-Type: application/json" \
  -d "{\"method\":\"key\",\"keyId\":\"$KEY_ID\"}"
# → {"did":"did:key:z6Mk...","document":{...}}

# Create a did:jwk
curl -s -X POST http://localhost:7005/wallet/$WALLET_ID/dids/create \
  -H "Content-Type: application/json" \
  -d '{"method":"jwk"}'

# List DIDs
curl http://localhost:7005/wallet/$WALLET_ID/dids
```

### Receive credentials (OID4VCI 1.0)

#### Full flow - one call

```bash
# offerUrl from a QR code / deep link
OFFER_URL="openid-credential-offer://?credential_offer_uri=https%3A%2F%2Fissuer.example.com%2Foffer%2Fabc"

curl -s -X POST http://localhost:7005/wallet/$WALLET_ID/credentials/receive \
  -H "Content-Type: application/json" \
  -d "{\"offerUrl\":\"$OFFER_URL\"}"
# → {"credentialIds":["3f2a..."],"deferredTransactionIds":{}}
```

Or supply the offer JSON directly (no network fetch):

```bash
curl -s -X POST http://localhost:7005/wallet/$WALLET_ID/credentials/receive \
  -H "Content-Type: application/json" \
  -d '{
    "offerJson": {
      "credential_issuer": "https://issuer.example.com",
      "credential_configuration_ids": ["pid_sd_jwt"],
      "grants": {
        "urn:ietf:params:oauth:grant-type:pre-authorized_code": {
          "pre-authorized_code": "abc123"
        }
      }
    }
  }'
```

#### Step-by-step (isolated steps)

```bash
# Step 1: Resolve the offer - returns issuer, token endpoint, offered credentials, grant type
curl -s -X POST http://localhost:7005/wallet/$WALLET_ID/credentials/receive/resolve-offer \
  -H "Content-Type: application/json" \
  -d "{\"offerUrl\":\"$OFFER_URL\"}"

# Step 2: Request an access token
curl -s -X POST http://localhost:7005/wallet/$WALLET_ID/credentials/receive/request-token \
  -H "Content-Type: application/json" \
  -d '{
    "tokenEndpoint": "https://issuer.example.com/token",
    "credentialIssuer": "https://issuer.example.com",
    "preAuthorizedCode": "abc123"
  }'

ACCESS_TOKEN="..."
C_NONCE="..."

# Step 3: Sign a proof of possession
curl -s -X POST http://localhost:7005/wallet/$WALLET_ID/credentials/receive/sign-proof \
  -H "Content-Type: application/json" \
  -d "{\"issuerUrl\":\"https://issuer.example.com\",\"nonce\":\"$C_NONCE\",\"keyId\":\"$KEY_ID\"}"

PROOF_JWT="..."

# Step 4: Fetch the credential
curl -s -X POST http://localhost:7005/wallet/$WALLET_ID/credentials/receive/fetch-credential \
  -H "Content-Type: application/json" \
  -d "{
    \"credentialEndpoint\": \"https://issuer.example.com/credential\",
    \"accessToken\": \"$ACCESS_TOKEN\",
    \"credentialConfigurationId\": \"pid_sd_jwt\",
    \"proofJwt\": \"$PROOF_JWT\"
  }"
```

#### Auth-code grant (isolated)

```bash
# Step 1: Generate the authorization redirect URL (PKCE is recommended)
curl -s -X POST http://localhost:7005/wallet/$WALLET_ID/credentials/receive/authorization-url \
  -H "Content-Type: application/json" \
  -d "{\"offerUrl\":\"$OFFER_URL\",\"usePkce\":true}"
# → {"authorizationUrl":"https://issuer.example.com/authorize?...","codeVerifier":"...","state":"...","credentialIssuerBaseUrl":"https://issuer.example.com"}

# (User follows authorizationUrl in a browser, then is redirected back with ?code=...)

# Step 2: Exchange the authorization code for an access token
curl -s -X POST http://localhost:7005/wallet/$WALLET_ID/credentials/receive/exchange-code \
  -H "Content-Type: application/json" \
  -d '{
    "credentialIssuerBaseUrl": "https://issuer.example.com",
    "code": "<code-from-redirect>",
    "codeVerifier": "<pkce-verifier>",
    "redirectUri": "openid://"
  }'
# → {"accessToken":"...","cNonce":"..."}
```

### Present credentials (OID4VP 1.0 / DCQL)

```bash
REQUEST_URL="openid4vp://authorize?client_id=verifier&request_uri=..."
HOLDER_DID="did:key:z6Mk..."

# Full flow - one call
curl -s -X POST http://localhost:7005/wallet/$WALLET_ID/credentials/present \
  -H "Content-Type: application/json" \
  -d "{\"requestUrl\":\"$REQUEST_URL\",\"did\":\"$HOLDER_DID\"}"
```

#### Credential matching before presentation

```bash
# Find which stored credentials match a DCQL query (useful for wallet picker UIs)
curl -s -X POST http://localhost:7005/wallet/$WALLET_ID/credentials/present/match-credentials-from-store \
  -H "Content-Type: application/json" \
  -d '{
    "dcqlQuery": {
      "credentials": [{
        "id": "pid",
        "format": "dc+sd-jwt",
        "meta": {"vct_values": ["eu.europa.ec.eudi.pid.1"]},
        "claims": [{"path": ["given_name"]}, {"path": ["family_name"]}]
      }]
    }
  }'
# → {"matchedQueryIds":["pid"],"matchCount":1,"matchedCredentialIds":{"pid":["3f2a..."]}}
```

### Named stores (multi-wallet)

```bash
# Create a shared credential store
curl -s -X POST http://localhost:7005/stores/credentials/shared-store

# Create two wallets that share it
WALLET_A=$(curl -s -X POST http://localhost:7005/wallet \
  -H "Content-Type: application/json" \
  -d '{"credentialStoreIds":["shared-store"]}' | python3 -c "import sys,json; print(json.load(sys.stdin)['walletId'])")

WALLET_B=$(curl -s -X POST http://localhost:7005/wallet \
  -H "Content-Type: application/json" \
  -d '{"credentialStoreIds":["shared-store"]}' | python3 -c "import sys,json; print(json.load(sys.stdin)['walletId'])")

# Credentials imported via wallet-A are visible from wallet-B (shared store)
```

### Authentication (when `auth` feature is enabled)

```bash
# Register
curl -s -X POST http://localhost:7005/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","password":"hunter2"}'

# Login
TOKEN=$(curl -s -X POST http://localhost:7005/auth/emailpass \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@example.com","password":"hunter2"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")

# All wallet routes require the token when auth is enabled
curl -H "Authorization: Bearer $TOKEN" http://localhost:7005/wallet

# Wallets created while authenticated are owned by the account
curl -s -X POST http://localhost:7005/wallet \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{}'
```

---

## Configuration

All configuration files live in `config/` and are loaded automatically at startup. Mount the directory when running in Docker.

| File                       | Purpose                                                       |
|----------------------------|---------------------------------------------------------------|
| `_features.conf`           | Feature flags: `auth`, `wallet2-persistence`, `dev-mode`      |
| `wallet-service.conf`      | `publicBaseUrl` - the external URL of this service (required) |
| `wallet2-persistence.conf` | JDBC URL + driver for SQLite or Postgres                      |
| `web.conf`                 | `webHost` + `webPort` (default `0.0.0.0:7005`)                |

### Feature flags

```hocon
# config/_features.conf
enabledFeatures = [
    # auth                  - enable account register/login + wallet ownership
    wallet2-persistence     # enable SQL persistence (SQLite default)
    # dev-mode              # load dev-mode.conf overrides
]
```

### Minimal required config

```hocon
# config/wallet-service.conf
publicBaseUrl = "https://wallet.example.com"
```

---

## Architecture

```
waltid-wallet-api2 (this service)
    ├── Main.kt                        - entry point, feature flag wiring
    ├── OSSWallet2Service.kt           - pluggable WalletResolver + named store registry
    ├── config/                        - HOCON configuration files
    └── depends on:
        ├── waltid-openid4vc-wallet-server  - all HTTP route handlers (compatible with Enterprise for easy later upgrading)
        │   └── Wallet2RouteHandler         - /wallet, /keys, /dids, /credentials, ...
        ├── waltid-openid4vc-wallet         - core logic: WalletIssuanceHandler, WalletPresentationHandler
        └── waltid-openid4vc-wallet-persistence  - Exposed-based SQLite/Postgres stores
```

The route handlers and core logic live in the **library** layer, not in this service - both the OSS service and the Enterprise stack point at the same libraries. Adding the `waltid.ktordocker` Gradle plugin is all that's needed to get Docker build/push support.

---

## API reference

Full interactive OpenAPI docs are available at **`http://localhost:7005/swagger/index.html`** when the service is running.

Key endpoint groups:

| Group                     | Base path                                                                |
|---------------------------|--------------------------------------------------------------------------|
| Wallet Management         | `GET/POST /wallet`, `GET/DELETE /wallet/{walletId}`                      |
| Key Management            | `/wallet/{walletId}/keys/...`                                            |
| DID Management            | `/wallet/{walletId}/dids/...`                                            |
| Credentials               | `/wallet/{walletId}/credentials/...`                                     |
| Issuance (OID4VCI 1.0)    | `/wallet/{walletId}/credentials/receive/...`                             |
| Presentation (OID4VP 1.0) | `/wallet/{walletId}/credentials/present/...`                             |
| Named Stores              | `GET/POST /stores/{keys,credentials,dids}/...`                           |
| Auth (optional)           | `/auth/register`, `/auth/emailpass`, `/auth/logout`, `/auth/account/...` |

## Join the community

* Connect and get the latest updates: [Discord](https://discord.gg/AW8AgqJthZ) | [Newsletter](https://walt.id/newsletter) | [YouTube](https://www.youtube.com/channel/UCXfOzrv3PIvmur_CmwwmdLA) | [LinkedIn](https://www.linkedin.com/company/walt-id/)
* Get help, request features and report bugs: [GitHub Issues](https://github.com/walt-id/waltid-identity/issues)
* Find more indepth documentation on our [docs site](https://docs.walt.id)

## License

Licensed under the [Apache License, Version 2.0](https://github.com/walt-id/waltid-identity/blob/main/LICENSE)

<div align="center">
<img src="../../assets/walt-banner.png" alt="walt.id banner" />
</div>
