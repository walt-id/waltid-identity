# Open questions

1. Which existing module contains the canonical 18013-5 DeviceRequest builder?
   - Answer:
     - Primary model (aligned with current verifier-side mdoc verification stack): `waltid-identity/waltid-libraries/credentials/waltid-mdoc-credentials2`
       - DeviceRequest type: `id.walt.mdoc.objects.deviceretrieval.DeviceRequest` in `waltid-identity/waltid-libraries/credentials/waltid-mdoc-credentials2/src/commonMain/kotlin/id/walt/mdoc/objects/deviceretrieval/DeviceRequest.kt`
       - CBOR encode/decode pattern: `id.walt.cose.coseCompliantCbor.encodeToByteArray(DeviceRequest.serializer(), ...)` (see `waltid-identity/waltid-libraries/credentials/waltid-mdoc-credentials2/src/commonTest/kotlin/MdlCborTest.kt`)
     - Legacy builder (has convenience `toCBOR()` / `toCBORHex()`): `waltid-identity/waltid-libraries/credentials/waltid-mdoc-credentials`
       - Builder-style class: `id.walt.mdoc.dataretrieval.DeviceRequest` in `waltid-identity/waltid-libraries/credentials/waltid-mdoc-credentials/src/commonMain/kotlin/id/walt/mdoc/dataretrieval/DeviceRequest.kt`

2. Where is the existing mdoc validator entrypoint used by verifier2 (policy integration point)?
   - Answer:
     - Main “format validator” entrypoint used by verifier2 code: `id.walt.openid4vp.verifier.verification.Verifier2PresentationValidator.validatePresentation(...)` in `waltid-identity/waltid-libraries/protocols/waltid-openid4vp-verifier/src/commonMain/kotlin/id/walt/openid4vp/verifier/verification/Verifier2PresentationValidator.kt`
       - For `mso_mdoc`, this dispatches to `MdocPresentationValidator.validateMsoMdocPresentation(...)` in `waltid-identity/waltid-libraries/protocols/waltid-openid4vp-verifier/src/commonMain/kotlin/id/walt/openid4vp/verifier/verification/MdocPresentationValidator.kt`
       - Which then calls the actual mdoc verifier: `id.walt.mdoc.verification.MdocVerifier.verify(mdocString, context)` in `waltid-identity/waltid-libraries/credentials/waltid-mdoc-credentials2/src/commonMain/kotlin/id/walt/mdoc/verification/MdocVerifier.kt`
     - Policy integration point (verifier2 “new pipeline”):
       - `PresentationVerificationEngine.verifySinglePresentation(...)` -> `VPPolicyRunner.verifyPresentation(...)` in `waltid-identity/waltid-libraries/protocols/waltid-openid4vp-verifier/src/commonMain/kotlin/id/walt/openid4vp/verifier/verification2/PresentationVerificationEngine.kt`
       - mdoc VP policies live in `waltid-identity/waltid-libraries/credentials/waltid-verification-policies2-vp/src/commonMain/kotlin/id/walt/policies2/vp/policies/mso_mdoc/` (example: `DeviceAuthMdocVpPolicy.kt`), and they also use `MdocVerifier` + `MdocVerificationContext` derived from the session context.

3. Do we already have HPKE primitives in the repo (or in waltid-crypto), and do they support:
   - P-256 KEM
   - HKDF-SHA256
   - AES-128-GCM
   - single-shot APIs?
   - Answer:
     - No HPKE implementation is present in the current repo modules.
     - What exists today:
       - Data model for Annex C “HPKE parameters”: `DCAPIEncryptionParameters` in `waltid-identity/waltid-libraries/credentials/waltid-mdoc-credentials2/src/commonMain/kotlin/id/walt/mdoc/objects/dcapi/DCAPIEncryptionParameters.kt` (nonce + recipient public COSE_Key), but no encrypt/decrypt logic.
       - JOSE JWE helpers (not HPKE): `JWKKey.encryptJwe/decryptJwe` supports `ECDH-ES + A128GCM` on P-256 in `waltid-identity/waltid-libraries/crypto/waltid-crypto/src/jvmMain/kotlin/id/walt/crypto/keys/jwk/JWKKey.jvm.kt`.
     - The Annex C kickoff pack should contain a placeholder `HpkeDcapi.kt` (e.g. `TODO("Implement...")`) under `waltid-identity/annex-c-codex-kickoff-pack-full/waltid-libraries/waltid-18013-7-verifier/` (create the module sources as part of Milestone 1).
     - Conclusion: HPKE Base / P-256 / HKDF-SHA256 / AES-128-GCM (single-shot) must be implemented (or added via a dependency) for Annex C.

4. Where should Annex C sessions be stored (existing verifier2 session store vs new in-memory store)?
   - Answer:
     - Today, `waltid-verifier-api2` stores OpenID4VP sessions in-memory in `Verifier2Service.sessions: HashMap<String, Verification2Session>` (`waltid-identity/waltid-services/waltid-verifier-api2/src/main/kotlin/id/walt/openid4vp/verifier/OSSVerifier2Service.kt`).
     - Annex C transactions require different state (HPKE recipient private key, nonce, serialized `encryptionInfo`, origin, TTL) and are not naturally represented as `Verification2Session` (OpenID4VP authorization request fields, `state`, etc.).
     - Recommendation for PoC / portal test: introduce a dedicated in-memory TTL store for Annex C transactions (per ADR-0004/ARCH “short-lived transaction session”), and keep it separate from `Verifier2Service.sessions`. If SSE/webhooks are needed, reuse the same `waltid-ktor-notifications` mechanism with a transaction-specific update payload.

5. What is the expected response model to return to portal client (match existing verifier2 result)?
   - Answer:
     - Current verifier2 API behavior:
       - `POST /verification-session/{id}/response` returns only an acknowledgement/redirect (`Map<String,String>`) and the actual verification results are written into the session (`Verification2Session.policyResults`, `Verification2Session.presentedCredentials`) and streamed via SSE (`Verifier2SessionUpdate`) / retrievable via `GET .../info`.
     - To “match existing verifier2 result” for Annex C verification:
       - Prefer returning the same core fields the portal can already consume from verifier2 sessions:
         - `status` (success/fail)
         - `policyResults` (reuse `id.walt.openid4vp.verifier.verification2.Verifier2PolicyResults`)
         - `presentedCredentials` (validated credentials extracted from the decrypted DeviceResponse)
         - optional error list / status reason
       - If a dedicated result DTO is preferred (as in SPEC.md), define it as a thin projection over those existing fields rather than inventing a new policy result format.
