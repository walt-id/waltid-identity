# VCI Wallet Conformance Test Backlog

## Status Legend
- 🔴 **BLOCKED** - Cannot proceed, dependency issue
- 🟡 **TODO** - Not started
- 🟢 **DONE** - Completed
- 🔵 **IN PROGRESS** - Currently working on

---

## Phase 1: Token Endpoint Authentication ✅ COMPLETE

### 🟢 1.1 Add client_assertion to Token Request

**Status:** DONE

Implemented `client_assertion` JWT generation for token requests with:
- Header: `alg`, `typ=JWT`, `kid`
- Payload: `iss`, `sub`, `aud` (AS issuer URL), `jti`, `iat`, `exp`

**Files modified:**
- `waltid-openid4vci-wallet/.../TokenRequestBuilder.kt`
- `waltid-openid4vc-wallet/.../WalletIssuanceHandler.kt`

---

### 🟢 1.2 Fix client_assertion JWT `aud` Claim

**Status:** DONE

The `aud` claim correctly uses the authorization server's issuer URL from AS metadata,
not the token endpoint.

---

### 🟢 1.3 Add `kid` to JWT Headers

**Status:** DONE

Both `client_assertion` and DPoP proofs include `kid` in the header.

---

### 🟢 1.4 Prevent client_assertion JWT Reuse

**Status:** DONE

**Problem:** When DPoP nonce retry occurs, the same `client_assertion` was reused,
causing `jti` reuse violation (RFC 7523).

**Solution:** Changed `TokenRequestBuilder.exchangeAuthorizationCode()` to accept
a `clientAssertionGenerator: (suspend () -> String)?` instead of a pre-built string.
The generator is called fresh for each request attempt, ensuring unique `jti` values.

**Files modified:**
- `waltid-openid4vci-wallet/.../TokenRequestBuilder.kt`
- `waltid-openid4vc-wallet/.../WalletIssuanceHandler.kt`

---

## Phase 2: Credential Request ✅ COMPLETE

### 🟢 2.1 Implement Credential Fetch Endpoint

**Status:** DONE

Implemented `POST /wallet/{id}/credentials/receive/fetch-credential` endpoint that:
- Sends credential request to issuer's credential endpoint
- Includes `Authorization: DPoP <access_token>` header
- Includes `DPoP` proof for credential endpoint
- Handles nonce endpoint fetching

---

### 🟢 2.2 Implement Key Proof JWT Generation

**Status:** DONE

Key proof JWT generation with:
- Header: `alg`, `typ=openid4vci-proof+jwt`, `kid` or `jwk`
- Payload: `iss`, `aud` (credential issuer), `iat`, `nonce`

Uses `JwtProofBuilder.buildJwtProof()` in the wallet library.

---

### 🟢 2.3 Handle DPoP for Resource Requests

**Status:** DONE

DPoP proof generation for credential endpoint includes:
- `ath` (access token hash) claim
- Nonce retry support

---

### 🟢 2.4 Nonce Endpoint Support

**Status:** DONE

**Problem:** Some issuers provide `nonce_endpoint` in metadata instead of `c_nonce` in token response.

**Solution:** 
- Added `nonceEndpoint` to `FetchCredentialRequest` and `GenerateAuthorizationUrlResult`
- `fetchCredential()` fetches nonce from `nonceEndpoint` if provided, falls back to `cNonce`
- Proof is signed internally with the fetched nonce

**Files modified:**
- `waltid-openid4vc-wallet/.../WalletIssuanceHandler.kt`
- `waltid-openid4vc-wallet-server/.../Wallet2RouteHandler.kt`
- `waltid-openid4vp-conformance-runners/.../VciWalletConformanceAdapter.kt`

---

## Phase 3: Adapter ✅ COMPLETE

### 🟢 3.1 Complete Credential Flow in Adapter

**Status:** DONE

Adapter handles full flow: offer → auth → token → credential fetch

---

### 🟢 3.2 Error Handling and Reporting

**Status:** DONE

Proper error responses with logging for debugging.

---

## Phase 4: Additional Test Variants

### 🟡 4.1 Support Pre-Authorized Code Flow

**Status:** Supported but not yet tested with conformance suite

The `receiveCredential` flow supports pre-authorized code, but conformance
testing has focused on authorization_code flow.

---

### 🟡 4.2 Support Different Credential Formats

**Formats:**
- 🟢 `vc+sd-jwt` - Tested and passing
- 🟡 `jwt_vc_json` - Implemented, not tested
- 🟡 `mso_mdoc` - Needs implementation

---

### 🟡 4.3 Support Deferred Issuance

**Status:** TODO

Handle `transaction_id` in response and poll deferred endpoint.

---

## Phase 5: Test Automation

### 🟢 5.1 Automated Test Runner

**Status:** DONE

JUnit test (`VciWalletConformanceTests`) that:
- Starts adapter
- Creates test plan in conformance suite
- Triggers test execution
- Validates results

---

### 🟡 5.2 Multiple Test Plan Support

**Status:** TODO

Add support for additional test variants:
- Without DPoP
- Without private_key_jwt
- Different credential formats
- Pre-auth flow

---

## Completed Conformance Test

**Test:** `oid4vci-1_0-wallet-test-credential-issuance-dpop-private_key_jwt-sd_jwt_vc-issuer_initiated-simple-immediate-unsigned-authorization_code-by_value-plain`

**Result:** 140/140 tests passing ✅

---

## Quick Reference: Required JWT Claims

### client_assertion JWT (for token endpoint)
```
Header: { "alg": "ES256", "typ": "JWT", "kid": "<key_id>" }
Payload: {
  "iss": "<client_id>",
  "sub": "<client_id>",
  "aud": "<as_issuer_url>",  // NOT token endpoint!
  "jti": "<unique_id>",      // Regenerated on each attempt
  "iat": <timestamp>,
  "exp": <timestamp + 300>
}
```

### DPoP Proof JWT (for token endpoint)
```
Header: { "alg": "ES256", "typ": "dpop+jwt", "jwk": { <public_key> } }
Payload: {
  "jti": "<unique_id>",
  "htm": "POST",
  "htu": "<token_endpoint_url>",
  "iat": <timestamp>,
  "nonce": "<server_provided_nonce>"  // If required
}
```

### Key Proof JWT (for credential endpoint)
```
Header: { "alg": "ES256", "typ": "openid4vci-proof+jwt", "kid": "<key_id>" }
Payload: {
  "iss": "<client_id>",
  "aud": "<credential_issuer_url>",
  "iat": <timestamp>,
  "nonce": "<c_nonce_or_from_nonce_endpoint>"
}
```

### DPoP Proof JWT (for credential endpoint)
```
Header: { "alg": "ES256", "typ": "dpop+jwt", "jwk": { <public_key> } }
Payload: {
  "jti": "<unique_id>",
  "htm": "POST",
  "htu": "<credential_endpoint_url>",
  "iat": <timestamp>,
  "ath": "<base64url(sha256(access_token))>",  // Access token hash
  "nonce": "<server_provided_nonce>"  // If required
}
```
