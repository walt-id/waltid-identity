
# waltid-18013-7-verifier

Verifier-side scaffolding for ISO/IEC 18013-7 Annex C (DC API / Apple Wallet) support.

Currently exposed (Milestone 1):
- `id.walt.iso18013.annexc.AnnexC` (protocol constant)
- `id.walt.iso18013.annexc.AnnexCResponseVerifier` (decrypt API contract)
- `id.walt.iso18013.annexc.cbor.Base64UrlNoPad` (base64url helper)

## Build & test

- Build: `./gradlew :waltid-libraries:protocols:waltid-18013-7-verifier:build`
- Tests only: `./gradlew :waltid-libraries:protocols:waltid-18013-7-verifier:test`

The JVM test harness loads `src/jvmTest/resources/annex-c/ANNEXC-REAL-001.json` and validates that the vector is
structurally sane (base64url decode + basic fields).

`recipientPrivateKeyHex` in the sample vector is generated via `id.walt.iso18013.annexc.SkrGenerator` and is only a
placeholder for scaffolding; it does not correspond to the captured `recipientPublicKey` in `encryptionInfoB64`.
