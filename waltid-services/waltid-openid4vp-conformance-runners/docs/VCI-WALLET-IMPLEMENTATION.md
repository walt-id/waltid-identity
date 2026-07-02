# VCI Wallet Conformance Test Implementation

## Overview

This document describes the implementation of OpenID4VCI wallet conformance testing infrastructure.
The wallet acts as a **credential holder** receiving credentials from an issuer (the conformance suite).

**Status:** All 140 conformance tests passing ✅

## Architecture

### Components

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         OIDF Conformance Suite                              │
│                    (https://localhost.emobix.co.uk:8443)                    │
│                                                                             │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐              │
│  │ Credential      │  │ Authorization   │  │ Token           │              │
│  │ Issuer          │  │ Server          │  │ Endpoint        │              │
│  │ Metadata        │  │ (OAuth 2.0)     │  │ (with DPoP)     │              │
│  └────────┬────────┘  └────────┬────────┘  └────────┬────────┘              │
│           │                    │                    │                       │
└───────────┼────────────────────┼────────────────────┼───────────────────────┘
            │                    │                    │
            │ (1) Fetch metadata │                    │
            │ (4) PAR request    │                    │
            │ (5) Authorize      │                    │
            │                    │                    │ (7) Token exchange
            │                    │                    │     with DPoP + nonce
            │                    │                    │
┌───────────┼────────────────────┼────────────────────┼──────────────────────┐
│           ▼                    ▼                    ▼                      │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    VCI Wallet Conformance Adapter                   │   │
│  │                         (Port 7007)                                 │   │
│  │                                                                     │   │
│  │  Endpoints:                                                         │   │
│  │  - POST /credential-offer  ← Receives offer from conformance suite  │   │
│  │  - GET  /callback          ← OAuth redirect after user login        │   │
│  └──────────────────────────────────┬──────────────────────────────────┘   │
│                                     │                                      │
│                                     │ (2) Parse offer                      │
│                                     │ (3) Generate auth URL                │
│                                     │ (6) Exchange code for token          │
│                                     │ (8) Fetch credential                 │
│                                     ▼                                      │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                         wallet-api2                                 │   │
│  │                         (Port 7005)                                 │   │
│  │                                                                     │   │
│  │  Endpoints (isolated flow):                                         │   │
│  │  - POST /wallet/{id}/credentials/receive/authorization-url          │   │
│  │  - POST /wallet/{id}/credentials/receive/exchange-code              │   │
│  │  - POST /wallet/{id}/credentials/receive/fetch-credential           │   │
│  │  - POST /wallet/{id}/credentials/receive/sign-proof                 │   │
│  └──────────────────────────────────┬──────────────────────────────────┘   │
│                                     │                                      │
│                                     ▼                                      │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    Protocol Libraries                               │   │
│  │                                                                     │   │
│  │  waltid-openid4vc-wallet (handlers)                                 │   │
│  │  └── WalletIssuanceHandler                                          │   │
│  │      ├── generateAuthorizationUrl() - PAR + auth URL generation     │   │
│  │      ├── exchangeCode()             - Token exchange with DPoP      │   │
│  │      ├── fetchCredential()          - Credential request            │   │
│  │      └── signProof()                - Key proof JWT generation      │   │
│  │                                                                     │   │
│  │  waltid-openid4vci-wallet (builders)                                │   │
│  │  └── TokenRequestBuilder                                            │   │
│  │      └── exchangeAuthorizationCode() - With DPoP/client_assertion   │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                            │
│                              Host Machine                                  │
└────────────────────────────────────────────────────────────────────────────┘
```

## Implemented Features

### 1. PAR (Pushed Authorization Request)

In `WalletIssuanceHandler.generateAuthorizationUrl()`:

- Detects `pushed_authorization_request_endpoint` in AS metadata
- POSTs authorization parameters to PAR endpoint
- Receives `request_uri` and builds authorization URL with it
- Includes `client_assertion` JWT for `private_key_jwt` authentication

### 2. DPoP (RFC 9449) Support

Full DPoP implementation for both token and credential endpoints:

- **Proof generation:** JWT with `typ=dpop+jwt`, `alg`, `jwk` (public key), `jti`, `htm`, `htu`, `iat`
- **Nonce retry:** Automatic retry on `use_dpop_nonce` error with server-provided nonce
- **Access token hash:** `ath` claim for credential endpoint requests

### 3. private_key_jwt Authentication (RFC 7523)

Client authentication for token requests:

- Header: `alg`, `typ=JWT`, `kid`
- Payload: `iss`, `sub`, `aud` (AS issuer URL), `jti`, `iat`, `exp`
- **Fresh JTI per request:** Generator function ensures unique `jti` on DPoP nonce retry

### 4. Nonce Endpoint Support

Some issuers provide `nonce_endpoint` instead of `c_nonce` in token response:

- `FetchCredentialRequest` accepts `nonceEndpoint` and `cNonce`
- Handler fetches from nonce endpoint if provided, falls back to `cNonce`
- Proof signed internally with the fetched nonce

### 5. Credential Fetch with Key Proof

Complete credential request implementation:

- Authorization header with DPoP-bound access token
- DPoP proof for credential endpoint (with `ath`)
- Key proof JWT with nonce

## Files Modified

### Protocol Libraries

1. **`waltid-openid4vc-wallet/src/.../WalletIssuanceHandler.kt`**
   - PAR support with client_assertion
   - DPoP proof generation
   - `fetchCredential()` with nonce endpoint support
   - `signProof()` for key proof JWT
   - `generateClientAssertion()` for private_key_jwt
   - `GenerateAuthorizationUrlResult` with `nonceEndpoint`, `credentialIssuerUrl`
   - `FetchCredentialRequest` with `nonceEndpoint`, `cNonce`, `proofKey`

2. **`waltid-openid4vci-wallet/src/.../TokenRequestBuilder.kt`**
   - `clientAssertionGenerator` parameter (function, not string)
   - DPoP nonce retry regenerates client_assertion (fresh jti)
   - DPoP header support

3. **`waltid-openid4vc-wallet-server/src/.../Wallet2RouteHandler.kt`**
   - Auto-inject wallet's staticKey for DPoP and proof signing

### Conformance Runner

4. **`waltid-openid4vp-conformance-runners/src/.../VciWalletConformanceAdapter.kt`**
   - HTTP bridge between conformance suite and wallet-api2
   - Handles offer → auth → token → credential flow
   - Stores `nonceEndpoint` from auth response

## Key Implementation Details

### client_assertion JTI Reuse Fix

**Problem:** When DPoP nonce retry occurred, the same `client_assertion` JWT was reused,
violating RFC 7523's requirement for unique `jti` values.

**Solution:** Changed `TokenRequestBuilder.exchangeAuthorizationCode()` to accept
`clientAssertionGenerator: (suspend () -> String)?` instead of a pre-built string.
The generator is called fresh for each request attempt (initial and retry).

```kotlin
// In WalletIssuanceHandler.exchangeCode()
val clientAssertionGenerator: (suspend () -> String)? = if (key != null && asIssuerUrl != null) {
    { generateClientAssertion(key, clientId, asIssuerUrl) }
} else null

// In TokenRequestBuilder - called fresh on each attempt
val clientAssertion = clientAssertionGenerator?.invoke()
```

### Nonce Endpoint Flow

```kotlin
// In WalletIssuanceHandler.fetchCredential()
val nonce = request.nonceEndpoint?.let { fetchCNonce(httpClient, it.toString()) }
    ?: request.cNonce

if (nonce != null && request.proofKey != null) {
    proofBuilder.buildJwtProof(
        key = request.proofKey,
        audience = request.credentialIssuerUrl.toString(),
        nonce = nonce,
        includeJwk = true
    )
}
```

## Test Configuration

- **VCI Wallet Adapter:** `http://10.0.0.79:7007`
- **wallet-api2:** `https://localhost:7005`
- **Conformance Suite:** `https://localhost.emobix.co.uk:8443`
- **Test Plan:** `oid4vci-1_0-wallet-test-credential-issuance`
- **Test Variant:** `dpop-private_key_jwt-sd_jwt_vc-issuer_initiated-simple-immediate-unsigned-authorization_code-by_value-plain-vci`
- **Result:** 140/140 tests passing ✅

## Related Documentation

- [VCI-WALLET.md](VCI-WALLET.md) - Test setup and quick start
- [VCI-WALLET-BACKLOG.md](VCI-WALLET-BACKLOG.md) - Implementation status
- [RFC 9449 - DPoP](https://datatracker.ietf.org/doc/html/rfc9449)
- [RFC 7523 - JWT Bearer Client Authentication](https://datatracker.ietf.org/doc/html/rfc7523)
- [OpenID4VCI 1.0](https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html)
