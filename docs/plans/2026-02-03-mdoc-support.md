# mDoc/mDL Support Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Enable mDoc (ISO 18013-5) credential issuance to EUDI wallet using JWT proofs

**Architecture:** The EUDI wallet SDK uses JWT proofs for ALL credential types (SD-JWT and mDoc alike) because CWT is marked "Unsupported". The issuer must adapt to accept JWT proofs for mDoc credentials. This is NOT blocked - we can implement it now.

**Tech Stack:** Kotlin, ISO 18013-5 mDoc, OpenID4VCI Draft 13+, JWT proofs

---

## Key Insight: Wallet Dictates Proof Type

The EUDI wallet SDK proof type selection is **credential-format agnostic**:

```kotlin
// From SDK: SubmitRequest.kt - Proof type selection order
// 1. Attestation proof (if supported)
// 2. JWT with key attestation (if supported)
// 3. JWT without attestation (fallback)
// CWT is NEVER used - marked as Unsupported
```

**Implication:** The issuer must advertise `jwt` in `proof_types_supported` for mDoc credentials, and accept JWT proofs when processing mDoc requests.

---

## Implementation Tasks

### Task 1: Update mDoc Metadata to Advertise JWT Proofs

**Files:**
- Modify: `docker-compose/issuer-api/config/credential-issuer-metadata.conf`

**Step 1: Check current mDoc configuration**

Look for existing mDoc/mDL credential configurations.

**Step 2: Update proof_types_supported to include JWT**

Change from:
```hocon
"org.iso.18013.5.1.mDL" = {
    format = "mso_mdoc"
    doctype = "org.iso.18013.5.1.mDL"
    cryptographic_binding_methods_supported = ["cose_key"]
    proof_types_supported = {
        cwt = { proof_signing_alg_values_supported = ["ES256"] }
    }
}
```

To:
```hocon
"org.iso.18013.5.1.mDL" = {
    format = "mso_mdoc"
    doctype = "org.iso.18013.5.1.mDL"
    scope = "org.iso.18013.5.1.mDL"
    cryptographic_binding_methods_supported = ["jwk"]
    credential_signing_alg_values_supported = ["ES256"]
    proof_types_supported = {
        jwt = { proof_signing_alg_values_supported = ["ES256", "ES384", "ES512"] }
    }
}
```

**Key changes:**
- `cryptographic_binding_methods_supported`: `["cose_key"]` → `["jwk"]` (JWT uses JWK)
- `proof_types_supported`: `cwt` → `jwt`

**Step 3: Commit**

```bash
git add docker-compose/issuer-api/config/credential-issuer-metadata.conf
git commit -m "feat: configure mDoc to accept JWT proofs for EUDI wallet compatibility"
```

---

### Task 2: Verify Issuer Accepts JWT Proofs for mDoc

**Files:**
- Verify: `waltid-services/waltid-issuer-api/src/main/kotlin/id/walt/issuer/issuance/CIProvider.kt`
- Verify: `waltid-libraries/protocols/waltid-openid4vc/src/commonMain/kotlin/id/walt/oid4vc/OpenID4VCI.kt`

**Step 1: Trace mDoc credential request handling**

The issuer already handles JWT proofs (we fixed this for SD-JWT). Verify the same code path works for mDoc:

1. `OidcApi.kt`: Receives credential request with JWT proof
2. `OpenID4VCI.validateProofOfPossession()`: Validates JWT signature
3. `CIProvider.generateCredential()`: Generates mDoc with holder key from JWT

**Step 2: Check holder key extraction from JWT for mDoc**

Verify that when generating mDoc credentials, the holder key is correctly extracted from the JWT proof (not expecting CWT):

```kotlin
// In CIProvider.kt - generateMdocCredential() should use JWT proof's key
val holderKey = OpenID4VCI.getHolderKeyFromProof(credentialRequest.proof)
```

**Step 3: If changes needed, implement and commit**

```bash
git add <modified-files>
git commit -m "fix: extract holder key from JWT proof for mDoc credentials"
```

---

### Task 3: Add mDoc Credential Configuration for PID

**Files:**
- Modify: `docker-compose/issuer-api/config/credential-issuer-metadata.conf`

**Step 1: Add EU PID as mDoc format**

```hocon
"eu.europa.ec.eudi.pid_mso_mdoc" = {
    format = "mso_mdoc"
    doctype = "eu.europa.ec.eudi.pid.1"
    scope = "eu.europa.ec.eudi.pid_mso_mdoc"
    cryptographic_binding_methods_supported = ["jwk"]
    credential_signing_alg_values_supported = ["ES256"]
    proof_types_supported = {
        jwt = { proof_signing_alg_values_supported = ["ES256", "ES384", "ES512"] }
    }
    display = [{
        name = "EU PID (mDoc)"
        locale = "en"
    }]
}
```

**Step 2: Commit**

```bash
git add docker-compose/issuer-api/config/credential-issuer-metadata.conf
git commit -m "feat: add EU PID mDoc credential configuration"
```

---

### Task 4: Test mDoc Issuance with EUDI Wallet

**Step 1: Restart issuer with new configuration**

```bash
cd docker-compose
docker compose --profile identity down
docker compose --profile identity up
```

**Step 2: Create mDoc credential offer**

Use the issuer API or portal to create a pre-authorized offer for `org.iso.18013.5.1.mDL` or `eu.europa.ec.eudi.pid_mso_mdoc`.

**Step 3: Scan QR with EUDI wallet**

The wallet should:
1. Recognize the mDoc credential offer
2. Send a credential request with JWT proof (not CWT)
3. Receive the mDoc credential

**Step 4: Verify credential in wallet**

Check that the mDoc credential appears correctly in the EUDI wallet.

**Step 5: Commit any fixes discovered during testing**

---

### Task 5: Handle Draft 13+ Response Format for mDoc

**Files:**
- Verify: `waltid-services/waltid-issuer-api/src/main/kotlin/id/walt/issuer/issuance/OidcApi.kt`

**Step 1: Verify mDoc responses use Draft 13+ format**

The SD-JWT fix already converts responses to Draft 13+ format with `credentials` array. Verify this also applies to mDoc responses.

**Step 2: If mDoc responses don't use `credentials` array, fix it**

The response format conversion in `OidcApi.kt` should be format-agnostic (apply to all credential types).

---

## Verification

After implementation:

```bash
# 1. Start services
cd docker-compose
docker compose --profile identity up

# 2. Check metadata advertises JWT for mDoc
curl -s http://localhost:7002/.well-known/openid-credential-issuer | jq '.credential_configurations_supported["org.iso.18013.5.1.mDL"].proof_types_supported'
# Expected: { "jwt": { ... } }

# 3. Create offer and scan with EUDI wallet
# Wallet should accept the offer and receive mDoc credential

# 4. Check issuer logs for successful issuance
docker compose logs waltid-issuer-api | grep -i mdoc
```

---

## Summary

| Task | Description | Status |
|------|-------------|--------|
| Task 1 | Update mDoc metadata for JWT proofs | ✅ Done |
| Task 2 | Verify issuer accepts JWT proofs for mDoc | ✅ Done (extractHolderKey already handles JWT) |
| Task 3 | Add EU PID mDoc configuration | ✅ Done (already configured) |
| Task 4 | Test with EUDI wallet | 🔲 Ready |
| Task 5 | Verify Draft 13+ response format | ✅ Done (OidcApi.kt already handles it) |

**Implementation complete!** Ready for wallet testing.
