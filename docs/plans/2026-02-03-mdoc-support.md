# mDoc/mDL Support Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Enable mDoc (ISO 18013-5) credential issuance to EUDI wallet when SDK adds CWT proof support

**Architecture:** Server-side mDoc generation already works. The blocker is EUDI SDK marking CWT proofs as "Unsupported". When SDK adds support, we need to: (1) verify CWT proofs work end-to-end, (2) add iOS/JS CWT verification if needed, (3) test with real EUDI wallet.

**Tech Stack:** Kotlin Multiplatform, COSE/CBOR, ISO 18013-5 mDoc, OpenID4VCI Draft 13+

---

## Current State Analysis

### What Works (Server-Side)

| Component | Status | Location |
|-----------|--------|----------|
| mDoc generation | ✅ Working | `CIProvider.kt:generateMdocCredential()` |
| COSE Sign1 signing | ✅ Working | `OpenID4VCI.kt:generateMDoc()` |
| CWT proof structure | ✅ Defined | `CwtProofPayload.kt`, `CwtProof.kt` |
| COSE verification (JVM) | ✅ Working | `OpenID4VC.kt:verifyCOSESign1Signature()` |
| Holder key extraction | ✅ Working | `OpenID4VCI.kt:getHolderKeyFromCwtProof()` |
| mDoc metadata config | ✅ Configured | `credential-issuer-metadata.conf` |

### What's Blocked

| Component | Status | Issue |
|-----------|--------|-------|
| EUDI SDK CWT support | ❌ Blocked | SDK marks CWT as `Unsupported` in `ProofTypeMeta.kt` |
| iOS CWT verification | ❌ TODO stub | `OpenID4VC.ios.kt:verifyCOSESign1Signature()` returns `TODO()` |
| JS CWT verification | ⚠️ Untested | May be missing platform implementation |

### EUDI SDK Blocker Detail

The EUDI SDK (`eu.europa.ec.eudi:eudi-lib-jvm-openid4vci-kt:0.9.1`) explicitly marks CWT proofs as unsupported:

```kotlin
// From SDK source: ProofTypeMeta.kt
sealed interface ProofTypeMeta {
    data object Unsupported : ProofTypeMeta  // CWT falls into this
    data class Jwt(...) : ProofTypeMeta
    data class Cwt(...) : ProofTypeMeta      // Parsed but marked unsupported
}
```

This means the wallet cannot build CWT proofs for mDoc credentials, even though the server can process them.

---

## Pre-Implementation Tasks

### Task 0: Monitor EUDI SDK for CWT Support

**This is a BLOCKING dependency. Do not proceed with implementation until CWT support is added.**

**Step 1: Set up SDK monitoring**

Watch the EUDI SDK repository for CWT support:
- Repository: `https://github.com/eu-digital-identity-wallet/eudi-lib-jvm-openid4vci-kt`
- Issue to track: Search for "CWT" or "mDoc" related issues
- Release notes: Check each new version for CWT/mDoc mentions

**Step 2: Test new SDK versions**

When a new SDK version releases:
```bash
# Update SDK version in test project
# Look for CWT support in ProofTypeMeta
grep -r "Cwt" ~/.gradle/caches/modules-2/files-2.1/eu.europa.ec.eudi/
```

**Step 3: Verify CWT is no longer "Unsupported"**

Check that `ProofTypeMeta.Cwt` is properly handled (not falling through to `Unsupported`).

---

## Implementation Tasks (After SDK Support)

### Task 1: Verify Server CWT Proof Handling

**Files:**
- Test: `waltid-services/waltid-issuer-api/src/test/kotlin/id/walt/issuer/CwtProofTest.kt`
- Verify: `waltid-libraries/protocols/waltid-openid4vc/src/commonMain/kotlin/id/walt/oid4vc/OpenID4VCI.kt`

**Step 1: Write CWT proof validation test**

```kotlin
class CwtProofTest {
    @Test
    fun `should validate CWT proof from EUDI wallet`() = runTest {
        // Capture actual CWT proof from EUDI wallet request
        val cwtProofBase64 = "..." // Captured from real request

        val credentialRequest = CredentialRequest(
            format = CredentialFormat.mso_mdoc,
            docType = "org.iso.18013.5.1.mDL",
            proof = ProofOfPossession(
                proofType = ProofType.cwt,
                cwt = cwtProofBase64
            )
        )

        val result = OpenID4VCI.validateProofOfPossession(credentialRequest, testNonce)
        assertTrue(result, "CWT proof should validate")
    }
}
```

**Step 2: Run test to verify it fails (no real CWT yet)**

Run: `./gradlew :waltid-services:waltid-issuer-api:test --tests "CwtProofTest"`
Expected: FAIL (no real CWT proof to test with)

**Step 3: Capture real CWT proof from EUDI wallet**

Once SDK supports CWT:
1. Configure mDoc credential in issuer
2. Scan QR with EUDI wallet
3. Capture the credential request from logs
4. Extract the CWT proof for test

**Step 4: Update test with real CWT proof and verify it passes**

Run: `./gradlew :waltid-services:waltid-issuer-api:test --tests "CwtProofTest"`
Expected: PASS

**Step 5: Commit**

```bash
git add waltid-services/waltid-issuer-api/src/test/kotlin/id/walt/issuer/CwtProofTest.kt
git commit -m "test: add CWT proof validation test for mDoc issuance"
```

---

### Task 2: Implement iOS CWT Verification

**Files:**
- Modify: `waltid-libraries/protocols/waltid-openid4vc/src/iosMain/kotlin/id/walt/oid4vc/OpenID4VC.ios.kt`
- Reference: `waltid-libraries/protocols/waltid-openid4vc/src/jvmMain/kotlin/id/walt/oid4vc/OpenID4VC.jvm.kt`

**Step 1: Write failing test for iOS CWT verification**

```kotlin
// In iosTest source set
class IosCwtVerificationTest {
    @Test
    fun `should verify COSE Sign1 signature on iOS`() = runTest {
        val testCwt = "..." // Valid CWT from JVM test
        val result = OpenID4VC.verifyCOSESign1Signature(
            target = TokenTarget.PROOF_OF_POSSESSION,
            token = testCwt
        )
        assertTrue(result)
    }
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :waltid-libraries:protocols:waltid-openid4vc:iosSimulatorArm64Test`
Expected: FAIL with `TODO()` or `NotImplementedError`

**Step 3: Implement iOS COSE verification**

```kotlin
// OpenID4VC.ios.kt
actual suspend fun verifyCOSESign1Signature(
    target: TokenTarget,
    token: String
): Boolean {
    // Decode COSE_Sign1 structure
    val coseBytes = token.base64UrlDecode()
    val coseSign1 = CborDecoder.decode(coseBytes) as CborArray

    // Extract components
    val protectedHeader = coseSign1[0] as CborByteString
    val unprotectedHeader = coseSign1[1] as CborMap
    val payload = coseSign1[2] as CborByteString
    val signature = coseSign1[3] as CborByteString

    // Extract key from unprotected header
    val coseKey = unprotectedHeader[CoseHeaderLabel.COSE_Key.value]

    // Verify signature using iOS Security framework
    // Use SecKeyVerifySignature with kSecKeyAlgorithmECDSASignatureMessageX962SHA256

    return verifyWithSecKey(coseKey, payload.bytes, signature.bytes)
}

private fun verifyWithSecKey(
    coseKey: CborValue,
    message: ByteArray,
    signature: ByteArray
): Boolean {
    // Convert COSE key to SecKey
    // Verify ECDSA signature
    // Implementation uses iOS Security.framework
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew :waltid-libraries:protocols:waltid-openid4vc:iosSimulatorArm64Test`
Expected: PASS

**Step 5: Commit**

```bash
git add waltid-libraries/protocols/waltid-openid4vc/src/iosMain/kotlin/id/walt/oid4vc/OpenID4VC.ios.kt
git add waltid-libraries/protocols/waltid-openid4vc/src/iosTest/kotlin/
git commit -m "feat(ios): implement COSE Sign1 signature verification"
```

---

### Task 3: Verify JS/Browser CWT Support

**Files:**
- Check: `waltid-libraries/protocols/waltid-openid4vc/src/jsMain/kotlin/id/walt/oid4vc/OpenID4VC.js.kt`
- Test: `waltid-libraries/protocols/waltid-openid4vc/src/jsTest/kotlin/`

**Step 1: Check if JS implementation exists**

```bash
grep -r "verifyCOSESign1Signature" waltid-libraries/protocols/waltid-openid4vc/src/jsMain/
```

**Step 2: Write JS verification test**

```kotlin
// In jsTest source set
class JsCwtVerificationTest {
    @Test
    fun `should verify COSE Sign1 signature in browser`() = runTest {
        val testCwt = "..." // Valid CWT
        val result = OpenID4VC.verifyCOSESign1Signature(
            target = TokenTarget.PROOF_OF_POSSESSION,
            token = testCwt
        )
        assertTrue(result)
    }
}
```

**Step 3: Run test**

Run: `./gradlew :waltid-libraries:protocols:waltid-openid4vc:jsTest`
Expected: Depends on implementation status

**Step 4: Implement if missing (similar to iOS)**

Use WebCrypto API for ECDSA verification.

**Step 5: Commit**

```bash
git add waltid-libraries/protocols/waltid-openid4vc/src/jsMain/
git add waltid-libraries/protocols/waltid-openid4vc/src/jsTest/
git commit -m "feat(js): implement COSE Sign1 signature verification for browser"
```

---

### Task 4: End-to-End mDoc Issuance Test

**Files:**
- Test: `waltid-services/waltid-e2e-tests/src/test/kotlin/MdocIssuanceE2ETest.kt`
- Config: `docker-compose/issuer-api/config/credential-issuer-metadata.conf`

**Step 1: Verify mDoc configuration in metadata**

```hocon
// Already configured in credential-issuer-metadata.conf
"org.iso.18013.5.1.mDL" = {
    format = "mso_mdoc"
    doctype = "org.iso.18013.5.1.mDL"
    scope = "org.iso.18013.5.1.mDL"
    cryptographic_binding_methods_supported = ["cose_key"]
    credential_signing_alg_values_supported = ["ES256"]
    proof_types_supported = {
        cwt = { proof_signing_alg_values_supported = ["ES256"] }
    }
}
```

**Step 2: Write E2E test**

```kotlin
class MdocIssuanceE2ETest {
    @Test
    fun `should issue mDL to EUDI wallet`() = runTest {
        // 1. Create pre-authorized offer
        val offer = createCredentialOffer(
            credentialConfigurationId = "org.iso.18013.5.1.mDL",
            preAuthorized = true
        )

        // 2. Simulate wallet token request
        val tokenResponse = requestToken(offer)

        // 3. Simulate wallet credential request with CWT proof
        val credentialResponse = requestCredential(
            accessToken = tokenResponse.accessToken,
            credentialConfigurationId = "org.iso.18013.5.1.mDL",
            proofType = "cwt"
        )

        // 4. Verify mDoc response
        assertNotNull(credentialResponse.credentials)
        val mdoc = credentialResponse.credentials[0].credential
        assertTrue(mdoc.startsWith("o")) // CBOR tag for mDoc
    }
}
```

**Step 3: Run E2E test**

Run: `./gradlew :waltid-services:waltid-e2e-tests:test --tests "MdocIssuanceE2ETest"`
Expected: PASS

**Step 4: Manual test with EUDI wallet**

1. Start services: `docker compose --profile identity up`
2. Create mDL offer via portal or API
3. Scan QR with EUDI wallet
4. Verify credential appears in wallet

**Step 5: Commit**

```bash
git add waltid-services/waltid-e2e-tests/src/test/kotlin/MdocIssuanceE2ETest.kt
git commit -m "test: add E2E test for mDoc/mDL issuance to EUDI wallet"
```

---

### Task 5: Add mDoc Nonce Handling (If Needed)

**Files:**
- Modify: `waltid-libraries/protocols/waltid-openid4vc/src/commonMain/kotlin/id/walt/oid4vc/OpenID4VCI.kt`

**Note:** This task is conditional. Only implement if EUDI wallet sends CWT proofs without nonce (similar to JWT issue we fixed for SD-JWT).

**Step 1: Check if nonce is present in CWT proofs**

Capture CWT proof from EUDI wallet and decode:
```kotlin
val cwtPayload = CwtProofPayload.fromCborBytes(payloadBytes)
println("CWT nonce: ${cwtPayload.nonce}")
```

**Step 2: If nonce is missing, add compatibility handling**

```kotlin
// In validateProofOfPossession()
credentialRequest.proof.isCwtProofType -> {
    val signatureValid = OpenID4VC.verifyCOSESign1Signature(
        target = TokenTarget.PROOF_OF_POSSESSION,
        token = credentialRequest.proof.cwt!!
    )
    val proofNonce = getNonceFromProof(credentialRequest.proof)

    // EUDI wallet SDK might not include nonce - allow for compatibility
    if (signatureValid && proofNonce == null) {
        log.warn { "CWT proof missing nonce - allowing for EUDI compatibility" }
        true
    } else {
        signatureValid && proofNonce == nonce
    }
}
```

**Step 3: Commit if changes were needed**

```bash
git add waltid-libraries/protocols/waltid-openid4vc/src/commonMain/kotlin/id/walt/oid4vc/OpenID4VCI.kt
git commit -m "fix: allow CWT proofs without nonce for EUDI compatibility"
```

---

## Summary

| Task | Status | Blocker |
|------|--------|---------|
| Task 0: Monitor EUDI SDK | 🔄 Ongoing | None |
| Task 1: Server CWT verification | ⏸️ Waiting | EUDI SDK CWT support |
| Task 2: iOS CWT verification | ⏸️ Waiting | EUDI SDK CWT support |
| Task 3: JS CWT verification | ⏸️ Waiting | EUDI SDK CWT support |
| Task 4: E2E mDoc test | ⏸️ Waiting | Tasks 1-3 |
| Task 5: Nonce handling | ⏸️ Waiting | Task 4 results |

**Next Action:** Monitor EUDI SDK releases for CWT proof support. When added, proceed with Task 1.
