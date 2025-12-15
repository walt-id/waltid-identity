# Annex C Support – Implementation Plan (Codex)

Rules:
- Small diffs
- Add tests as you go
- Run Gradle tests after each step

Milestone 1: Scaffolding
- [x] Add Gradle module `waltid-libraries:waltid-18013-7-verifier`
- [x] Compile + basic unit tests

Milestone 2: Request builder
- [x] base64url(no pad) helpers
- [x] Build EncryptionInfo = ["dcapi",{nonce,recipientPublicKey(COSE_Key)}]
- [x] Build / reuse DeviceRequest builder
- [x] Expose library API that returns `{ deviceRequestB64, encryptionInfoB64 }` for `/annex-c/request`

Milestone 3: Transcript + HPKE
- [x] Compute SessionTranscript and HPKE info
- [x] Implement `AnnexCResponseVerifier.decryptToDeviceResponse(...)` (HPKE decrypt to DeviceResponse bytes)

Milestone 4: Verifier2 API endpoints
- [ ] /annex-c/create -> create session, store origin + policies
- [ ] /annex-c/request -> call `waltid-18013-7-verifier` to build `{deviceRequestB64,encryptionInfoB64}`, return protocol object for DC API
- [ ] /annex-c/response -> store response, call `AnnexCResponseVerifier.decryptToDeviceResponse(...)` in async validation job
- [ ] /annex-c/info -> return session + validation result

Milestone 5: Deterministic tests
- [ ] Load `ANNEXC-REAL-001.json`
- [ ] Verify transcript hash matches
- [ ] If skR present, verify HPKE decrypt produces expected DeviceResponse hash
