# VCI Wallet Conformance Test Backlog

## Status Legend
- 🔴 **BLOCKED** - Cannot proceed, dependency issue
- 🟡 **TODO** - Not started
- 🟢 **DONE** - Completed
- 🔵 **IN PROGRESS** - Currently working on

---

## Phase 1: Token Endpoint Authentication (CRITICAL)

### 🟡 1.1 Add client_assertion to Token Request

**Problem:** Token endpoint requires `private_key_jwt` authentication but we only send it for PAR.

**Failures from conformance test:**
- `Could not find client assertion in request parameters`
- `client_assertion_type missing from request parameters`
- `kid value in JWT header is missing/null/empty`
- `private_key_jwt aud claim does not match the authentication server issuer url`

**Solution:**
1. Update `TokenRequestBuilder.exchangeAuthorizationCode()` to accept client assertion parameters
2. Generate client_assertion JWT with:
   - Header: `alg`, `typ=jwt`, `kid` (key ID)
   - Payload: `iss` (client_id), `sub` (client_id), `aud` (token endpoint or issuer URL), `jti`, `iat`, `exp`
3. Add `client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer` to token request
4. Update `WalletIssuanceHandler.exchangeCode()` to generate and include client assertion

**Files to modify:**
- `waltid-openid4vci-wallet/src/.../TokenRequestBuilder.kt`
- `waltid-openid4vc-wallet/src/.../WalletIssuanceHandler.kt`
- `waltid-openid4vc-wallet-server/src/.../Wallet2RouteHandler.kt`

**Acceptance criteria:**
- Token request includes `client_assertion` JWT
- Token request includes `client_assertion_type`
- JWT header includes `kid`
- JWT `aud` claim matches AS issuer URL (not token endpoint)

---

### 🟡 1.2 Fix client_assertion JWT `aud` Claim

**Problem:** The `aud` claim must be the authorization server's issuer URL, not the token endpoint.

**Current behavior:** Unknown (may be using token endpoint)

**Required behavior:** Use `issuer` from AS metadata (e.g., `https://localhost.emobix.co.uk:8443/test/a/vci_wallet_sdjwt_dpop/`)

**Solution:**
1. Pass AS issuer URL to `exchangeCode()` request
2. Use issuer URL as `aud` in client_assertion JWT

**Files to modify:**
- `waltid-openid4vc-wallet/src/.../WalletIssuanceHandler.kt` - ExchangeCodeRequest
- `waltid-openid4vp-conformance-runners/src/.../VciWalletConformanceAdapter.kt`

---

### 🟡 1.3 Add `kid` to JWT Headers

**Problem:** Both client_assertion and potentially DPoP proofs need `kid` in header.

**Solution:**
1. Extract key ID from JWK (`kid` field)
2. Include in JWT header alongside `alg`, `typ`, `jwk`

**Files to modify:**
- `waltid-openid4vc-wallet/src/.../WalletIssuanceHandler.kt` - generateDpopProof(), generateClientAssertion()

---

### 🟡 1.4 Prevent client_assertion JWT Reuse

**Problem:** `Detected reuse of client_assertion JWT for jti=...`

**Solution:**
- Ensure unique `jti` for each request
- Currently using `Uuid.random()` which should be unique, but verify it's generated fresh each time

---

## Phase 2: Credential Request

### 🟡 2.1 Implement Credential Fetch Endpoint

**Problem:** After token exchange, we need to request the actual credential.

**Solution:**
1. Implement `POST /wallet/{id}/credentials/receive/fetch` endpoint
2. Send credential request to issuer's credential endpoint
3. Include:
   - `Authorization: DPoP <access_token>` header
   - `DPoP` header with new proof for credential endpoint
   - Body with `format`, `credential_configuration_id`, `proof`

**Credential request body:**
```json
{
  "format": "vc+sd-jwt",
  "credential_configuration_id": "org.iso.18013.5.1.mDL",
  "proof": {
    "proof_type": "jwt",
    "jwt": "<key_proof_jwt>"
  }
}
```

**Files to modify:**
- `waltid-openid4vc-wallet/src/.../WalletIssuanceHandler.kt`
- `waltid-openid4vc-wallet-server/src/.../Wallet2RouteHandler.kt`
- `waltid-openid4vp-conformance-runners/src/.../VciWalletConformanceAdapter.kt`

---

### 🟡 2.2 Implement Key Proof JWT Generation

**Problem:** Credential request requires a `proof` object with a JWT proving possession of wallet's key.

**Key proof JWT structure:**
- Header: `alg`, `typ=openid4vci-proof+jwt`, `kid` or `jwk`
- Payload: `iss` (client_id), `aud` (credential issuer), `iat`, `nonce` (c_nonce from token response)

**Solution:**
1. Add `generateKeyProof()` function in WalletIssuanceHandler
2. Use `c_nonce` from token response
3. Sign with wallet's key

---

### 🟡 2.3 Handle DPoP for Resource Requests

**Problem:** Credential endpoint also requires DPoP proof.

**Solution:**
1. Generate new DPoP proof for credential endpoint (different `htu`)
2. May need to include `ath` (access token hash) claim
3. Handle nonce retry for resource server too

---

## Phase 3: Adapter Completion

### 🟡 3.1 Complete Credential Flow in Adapter

**Problem:** Adapter only handles auth → token, not credential fetch.

**Solution:**
1. After successful token exchange, call fetch endpoint
2. Return credential to conformance suite
3. Store credential in wallet

---

### 🟡 3.2 Error Handling and Reporting

**Problem:** Errors are not well-reported to conformance suite.

**Solution:**
1. Proper error responses with details
2. Logging for debugging
3. Conformance-compatible error format

---

## Phase 4: Additional Test Variants

### 🟡 4.1 Support Pre-Authorized Code Flow

**Problem:** Current implementation focuses on authorization_code flow.

**Solution:**
1. Test and fix pre-authorized code path
2. Handle `tx_code` (PIN) if required

---

### 🟡 4.2 Support Different Credential Formats

**Problem:** Test uses `vc+sd-jwt`, may need to support others.

**Formats to support:**
- `vc+sd-jwt` (current)
- `jwt_vc_json`
- `mso_mdoc`

---

### 🟡 4.3 Support Deferred Issuance

**Problem:** Some credentials may be deferred.

**Solution:**
1. Handle `transaction_id` in response
2. Implement polling of deferred endpoint

---

## Phase 5: Test Automation

### 🟡 5.1 Automated Test Runner

**Problem:** Currently manual test execution.

**Solution:**
1. JUnit test that:
   - Starts adapter
   - Creates test plan in conformance suite
   - Triggers test execution
   - Validates results
2. Integrate with CI/CD

---

### 🟡 5.2 Multiple Test Plan Support

**Problem:** Only one test variant configured.

**Solution:**
1. Support multiple variants:
   - With/without DPoP
   - With/without private_key_jwt
   - Different credential formats
   - Pre-auth vs auth-code

---

## Priority Order

1. **1.1** Add client_assertion to Token Request (CRITICAL - blocks all progress)
2. **1.2** Fix client_assertion JWT `aud` Claim
3. **1.3** Add `kid` to JWT Headers
4. **2.1** Implement Credential Fetch Endpoint
5. **2.2** Implement Key Proof JWT Generation
6. **2.3** Handle DPoP for Resource Requests
7. **3.1** Complete Credential Flow in Adapter
8. **3.2** Error Handling and Reporting
9. **5.1** Automated Test Runner
10. **4.x** Additional variants (as needed)

---

## Quick Reference: Required JWT Claims

### client_assertion JWT (for token endpoint)
```
Header: { "alg": "ES256", "typ": "JWT", "kid": "<key_id>" }
Payload: {
  "iss": "<client_id>",
  "sub": "<client_id>",
  "aud": "<as_issuer_url>",  // NOT token endpoint!
  "jti": "<unique_id>",
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
  "nonce": "<c_nonce_from_token_response>"
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
