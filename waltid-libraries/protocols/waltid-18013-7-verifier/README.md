
# waltid-18013-7-verifier

Verifier-side scaffolding for ISO/IEC 18013-7 Annex C (DC API / Apple Wallet) support.

Currently exposed (Milestone 1):
- `id.walt.iso18013.annexc.AnnexC` (protocol constant)
- `id.walt.iso18013.annexc.AnnexCResponseVerifier` (decrypt API contract)
- `id.walt.iso18013.annexc.AnnexCResponseVerifierJvm` (JVM HPKE decrypt implementation)
- `id.walt.iso18013.annexc.AnnexCHpkeKeyGeneratorJvm` (JVM HPKE recipient keypair generator)
- `id.walt.iso18013.annexc.cbor.Base64UrlNoPad` (base64url helper)
- `id.walt.iso18013.annexc.AnnexCRequestBuilder` (builds `deviceRequestB64` + `encryptionInfoB64`)
- `id.walt.iso18013.annexc.AnnexCTranscriptBuilder` (builds `SessionTranscript` + HPKE `info`)

## Build & test

- Build: `./gradlew :waltid-libraries:protocols:waltid-18013-7-verifier:build`
- Tests only: `./gradlew :waltid-libraries:protocols:waltid-18013-7-verifier:jvmTest`

## Developer notes

### Request (browser-facing)

Build the DC API request payload (both values are base64url, no padding, CBOR):
- `deviceRequestB64` = `DeviceRequest` CBOR
- `encryptionInfoB64` = `EncryptionInfo` CBOR = `["dcapi",{nonce,recipientPublicKey(COSE_Key)}]`

### Response (wallet -> backend)

Wallet returns:
- `response` = base64url(no-pad) CBOR `EncryptedResponse` = `["dcapi",{enc,cipherText}]`

Decrypt on JVM:
- `AnnexCResponseVerifierJvm.decryptToDeviceResponse(encryptedResponseB64, encryptionInfoB64, origin, recipientPrivateKey)`

Transcript / HPKE binding (ISO 18013-7 Annex C):
- `dcapiInfo = [ encryptionInfoB64, serializedOrigin ]`
- `SessionTranscript = [ null, null, ["dcapi", sha256(cbor(dcapiInfo))] ]`
- HPKE `info` = `cbor(SessionTranscript)` (implemented by `AnnexCTranscriptBuilder.computeHpkeInfo(...)`)

The JVM test harness loads `src/jvmTest/resources/annex-c/ANNEXC-REAL-001.json` and validates that the vector is
structurally sane (base64url decode + basic fields).

Deterministic HPKE test vectors:
- `src/jvmTest/resources/annex-c/ANNEXC-DETERMINISTIC-001.json` (transcript hash + HPKE decrypt hash assertions)

`recipientPrivateKeyHex` in the sample vector is generated via `id.walt.iso18013.annexc.SkrGenerator` and is only a
placeholder for scaffolding; it does not correspond to the captured `recipientPublicKey` in `encryptionInfoB64`.
