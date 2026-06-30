# VCI Wallet Conformance Test Implementation

## Overview

This document describes the implementation of OpenID4VCI wallet conformance testing infrastructure.
The wallet acts as a **credential holder** receiving credentials from an issuer (the conformance suite).

## Architecture

### Components

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         OIDF Conformance Suite                              │
│                    (https://localhost.emobix.co.uk:8443)                    │
│                                                                             │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐             │
│  │ Credential      │  │ Authorization   │  │ Token           │             │
│  │ Issuer          │  │ Server          │  │ Endpoint        │             │
│  │ Metadata        │  │ (OAuth 2.0)     │  │ (with DPoP)     │             │
│  └────────┬────────┘  └────────┬────────┘  └────────┬────────┘             │
│           │                    │                    │                       │
└───────────┼────────────────────┼────────────────────┼───────────────────────┘
            │                    │                    │
            │ (1) Fetch metadata │                    │
            │ (4) PAR request    │                    │
            │ (5) Authorize      │                    │
            │                    │                    │ (7) Token exchange
            │                    │                    │     with DPoP + nonce
            │                    │                    │
┌───────────┼────────────────────┼────────────────────┼───────────────────────┐
│           ▼                    ▼                    ▼                       │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    VCI Wallet Conformance Adapter                    │   │
│  │                         (Port 7007)                                  │   │
│  │                                                                      │   │
│  │  Endpoints:                                                          │   │
│  │  - POST /credential-offer  ← Receives offer from conformance suite  │   │
│  │  - GET  /callback          ← OAuth redirect after user login        │   │
│  └──────────────────────────────────┬──────────────────────────────────┘   │
│                                     │                                       │
│                                     │ (2) Parse offer                       │
│                                     │ (3) Generate auth URL                 │
│                                     │ (6) Exchange code for token           │
│                                     │ (8) Fetch credential                  │
│                                     ▼                                       │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                         wallet-api2                                  │   │
│  │                         (Port 7005)                                  │   │
│  │                                                                      │   │
│  │  Endpoints:                                                          │   │
│  │  - POST /wallet/{id}/credentials/receive           (pre-auth flow)  │   │
│  │  - POST /wallet/{id}/credentials/receive/auth-url  (auth-code flow) │   │
│  │  - POST /wallet/{id}/credentials/receive/exchange-code              │   │
│  │  - POST /wallet/{id}/credentials/receive/fetch                      │   │
│  └──────────────────────────────────┬──────────────────────────────────┘   │
│                                     │                                       │
│                                     ▼                                       │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    Protocol Libraries                                │   │
│  │                                                                      │   │
│  │  waltid-openid4vc-wallet (handlers)                                 │   │
│  │  └── WalletIssuanceHandler                                          │   │
│  │      ├── generateAuthorizationUrl() - PAR + auth URL generation     │   │
│  │      ├── exchangeCode()             - Token exchange with DPoP      │   │
│  │      └── generateDpopProof()        - RFC 9449 DPoP proof           │   │
│  │                                                                      │   │
│  │  waltid-openid4vci-wallet (builders)                                │   │
│  │  └── TokenRequestBuilder                                            │   │
│  │      └── exchangeAuthorizationCode() - With DPoP nonce retry        │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│                              Host Machine                                   │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Detailed Call Flow (Authorization Code + DPoP + private_key_jwt)

### Phase 1: Credential Offer

```
Conformance Suite                        VCI Adapter (7007)
      │                                        │
      │  POST /credential-offer                │
      │  Body: { offerUrl: "openid-credential- │
      │          offer://..." }                │
      │ ─────────────────────────────────────► │
      │                                        │
      │  HTTP 200                              │
      │  Location: <authorization_url>         │
      │ ◄───────────────────────────────────── │
      │                                        │
```

### Phase 2: Authorization URL Generation (with PAR)

```
VCI Adapter                              wallet-api2 (7005)                    Conformance Suite (AS)
      │                                        │                                        │
      │  POST /wallet/{id}/credentials/        │                                        │
      │       receive/auth-url                 │                                        │
      │  Body: { offerUrl, clientId,           │                                        │
      │          redirectUri, usePkce }        │                                        │
      │ ─────────────────────────────────────► │                                        │
      │                                        │                                        │
      │                                        │  GET /.well-known/openid-credential-   │
      │                                        │      issuer                            │
      │                                        │ ─────────────────────────────────────► │
      │                                        │                                        │
      │                                        │  HTTP 200 { credential_issuer,         │
      │                                        │    credential_configurations_supported,│
      │                                        │    authorization_servers }             │
      │                                        │ ◄───────────────────────────────────── │
      │                                        │                                        │
      │                                        │  GET /.well-known/oauth-authorization- │
      │                                        │      server                            │
      │                                        │ ─────────────────────────────────────► │
      │                                        │                                        │
      │                                        │  HTTP 200 { issuer, authorization_     │
      │                                        │    endpoint, token_endpoint,           │
      │                                        │    pushed_authorization_request_       │
      │                                        │    endpoint, ... }                     │
      │                                        │ ◄───────────────────────────────────── │
      │                                        │                                        │
      │                                        │  POST /par                             │
      │                                        │  Body: response_type=code&client_id=X& │
      │                                        │    redirect_uri=Y&scope=org.iso...&    │
      │                                        │    state=Z&code_challenge=...&         │
      │                                        │    code_challenge_method=S256&         │
      │                                        │    client_assertion=<JWT>&             │
      │                                        │    client_assertion_type=urn:ietf:...  │
      │                                        │ ─────────────────────────────────────► │
      │                                        │                                        │
      │                                        │  HTTP 201 { request_uri, expires_in }  │
      │                                        │ ◄───────────────────────────────────── │
      │                                        │                                        │
      │  HTTP 200 { authorizationUrl:          │                                        │
      │    "/authorize?client_id=X&            │                                        │
      │     request_uri=urn:ietf:...",         │                                        │
      │    state, codeVerifier, tokenEndpoint }│                                        │
      │ ◄───────────────────────────────────── │                                        │
      │                                        │                                        │
```

### Phase 3: User Authorization (Browser)

```
User Browser                             Conformance Suite (AS)
      │                                        │
      │  GET /authorize?client_id=X&           │
      │      request_uri=urn:ietf:params:...   │
      │ ─────────────────────────────────────► │
      │                                        │
      │  HTTP 302 (Login page / consent)       │
      │ ◄───────────────────────────────────── │
      │                                        │
      │  [User logs in / consents]             │
      │ ─────────────────────────────────────► │
      │                                        │
      │  HTTP 302 Location:                    │
      │    http://10.0.0.79:7007/callback?     │
      │    code=AUTH_CODE&state=STATE          │
      │ ◄───────────────────────────────────── │
      │                                        │
```

### Phase 4: Token Exchange (with DPoP + Nonce Retry)

```
VCI Adapter                              wallet-api2 (7005)                    Conformance Suite (Token)
      │                                        │                                        │
      │  GET /callback?code=X&state=Y          │                                        │
      │  [Browser redirect]                    │                                        │
      │ ─────────────────────────────────────► │                                        │
      │                                        │                                        │
      │  POST /wallet/{id}/credentials/        │                                        │
      │       receive/exchange-code            │                                        │
      │  Body: { tokenEndpoint, code,          │                                        │
      │          codeVerifier, clientId,       │                                        │
      │          redirectUri }                 │                                        │
      │ ─────────────────────────────────────► │                                        │
      │                                        │                                        │
      │                                        │  POST /token                           │
      │                                        │  Headers: DPoP: <proof_without_nonce>  │
      │                                        │  Body: grant_type=authorization_code&  │
      │                                        │    code=X&redirect_uri=Y&client_id=Z&  │
      │                                        │    code_verifier=V                     │ ❌ MISSING:
      │                                        │ ─────────────────────────────────────► │   client_assertion
      │                                        │                                        │   client_assertion_type
      │                                        │  HTTP 400 { error: "use_dpop_nonce" }  │
      │                                        │  Headers: DPoP-Nonce: <nonce>          │
      │                                        │ ◄───────────────────────────────────── │
      │                                        │                                        │
      │                                        │  POST /token (RETRY)                   │
      │                                        │  Headers: DPoP: <proof_with_nonce>     │
      │                                        │  Body: [same + client_assertion]       │ ❌ STILL MISSING
      │                                        │ ─────────────────────────────────────► │
      │                                        │                                        │
      │                                        │  HTTP 200 { access_token, token_type:  │
      │                                        │    "DPoP", c_nonce, ... }              │
      │                                        │ ◄───────────────────────────────────── │
      │                                        │                                        │
      │  HTTP 200 { accessToken, cNonce }      │                                        │
      │ ◄───────────────────────────────────── │                                        │
      │                                        │                                        │
```

### Phase 5: Credential Request (NOT YET IMPLEMENTED)

```
VCI Adapter                              wallet-api2 (7005)                    Conformance Suite (Issuer)
      │                                        │                                        │
      │  POST /wallet/{id}/credentials/        │                                        │
      │       receive/fetch                    │                                        │
      │  Body: { credentialEndpoint,           │                                        │
      │          accessToken, format,          │                                        │
      │          credentialConfigurationId }   │                                        │
      │ ─────────────────────────────────────► │                                        │
      │                                        │                                        │
      │                                        │  POST /credential                      │
      │                                        │  Headers:                              │
      │                                        │    Authorization: DPoP <access_token>  │
      │                                        │    DPoP: <proof_for_credential_ep>     │
      │                                        │  Body: { format: "vc+sd-jwt",          │
      │                                        │    credential_configuration_id: "...", │
      │                                        │    proof: { proof_type: "jwt",         │
      │                                        │             jwt: <key_proof> } }       │
      │                                        │ ─────────────────────────────────────► │
      │                                        │                                        │
      │                                        │  HTTP 200 { credential: "..." }        │
      │                                        │ ◄───────────────────────────────────── │
      │                                        │                                        │
      │  HTTP 200 { credential, stored: true } │                                        │
      │ ◄───────────────────────────────────── │                                        │
      │                                        │                                        │
```

## What Has Been Implemented

### 1. VCI Wallet Conformance Adapter (`VciWalletConformanceAdapter.kt`)

An HTTP server that bridges the conformance suite with wallet-api2:

- **POST /credential-offer**: Receives credential offers, initiates auth flow
- **GET /callback**: Handles OAuth redirect, exchanges code for token
- Stores pending auth flows (state → codeVerifier, tokenEndpoint)

### 2. PAR (Pushed Authorization Request) Support

In `WalletIssuanceHandler.generateAuthorizationUrl()`:

- Detects `pushed_authorization_request_endpoint` in AS metadata
- POSTs authorization parameters to PAR endpoint
- Receives `request_uri` and builds authorization URL with it
- Includes `client_assertion` JWT for `private_key_jwt` authentication

### 3. DPoP (RFC 9449) Support

In `WalletIssuanceHandler.exchangeCode()` and `TokenRequestBuilder`:

- Generates DPoP proof JWT with:
  - Header: `typ=dpop+jwt`, `alg`, `jwk` (public key)
  - Payload: `jti`, `htm` (POST), `htu` (token endpoint), `iat`
- Supports nonce retry flow:
  1. First request without nonce
  2. On `use_dpop_nonce` error, extract `DPoP-Nonce` header
  3. Regenerate proof with nonce and retry

### 4. Credential Configuration Scope

- Extracts scope from credential configuration metadata (e.g., `org.iso.18013.5.1.mDL`)
- Uses this instead of hardcoded `openid` scope (which fails for PLAIN_OAUTH)

### 5. Token Endpoint URL in Auth Response

- `GenerateAuthorizationUrlResult` now includes `tokenEndpoint`
- Adapter stores and uses correct token endpoint for exchange

## Files Modified

### Protocol Libraries

1. **`waltid-openid4vc-wallet/src/.../WalletIssuanceHandler.kt`**
   - Added PAR support
   - Added client_assertion JWT generation
   - Added DPoP proof generation with nonce support
   - Added credential config scope extraction
   - Added tokenEndpoint to GenerateAuthorizationUrlResult

2. **`waltid-openid4vci-wallet/src/.../TokenRequestBuilder.kt`**
   - Added dpopProofGenerator parameter
   - Added DPoP nonce retry flow
   - Added DPoP header to token requests

3. **`waltid-openid4vc-wallet-server/src/.../Wallet2RouteHandler.kt`**
   - Auto-inject wallet's staticKey for client_assertion (PAR)
   - Auto-inject wallet's staticKey for DPoP (token exchange)

### Conformance Runner

4. **`waltid-openid4vp-conformance-runners/src/.../VciWalletConformanceAdapter.kt`**
   - New adapter for VCI wallet tests
   - Handles credential offer → auth flow → callback
   - Stores tokenEndpoint from auth response

5. **`waltid-openid4vp-conformance-runners/src/.../VciWalletAdapterMain.kt`**
   - Standalone runner for the adapter

6. **`waltid-openid4vp-conformance-runners/src/.../VciWalletTestPlan.kt`**
   - Test plan definition for VCI wallet tests

## Commits (This Session)

1. `417650c8e` - fix(wallet): Use credential configuration scope in authorization request
2. `dfc7b7f96` - feat(wallet): Add PAR (Pushed Authorization Request) support
3. `fff792c7c` - feat(wallet): Add DPoP (RFC 9449) support for token exchange
4. `1cad8ead8` - feat(wallet): Add DPoP nonce retry support (RFC 9449 §5)

## Current Test Configuration

- **Host IP:** `10.0.0.79`
- **VCI Wallet Adapter:** `http://10.0.0.79:7007`
- **wallet-api2:** `https://localhost:7005`
- **Wallet ID:** `e6c69ed4-d2ec-4e68-b106-1aacb79768dd` (with static key)
- **Conformance Suite:** `https://localhost.emobix.co.uk:8443`
- **Test Plan:** `oid4vci-1_0-wallet-test-credential-issuance`
- **Test Variant:** `dpop-private_key_jwt-sd_jwt_vc-issuer_initiated-simple-immediate-unsigned-authorization_code-by_value-plain-vci`
