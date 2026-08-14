# DMV Digital Credentials API fixture

- Source: `https://digital-credentials.dev/dmv`
- Captured: 2026-08-13 from Android Chrome `150.0.7871.187` on Android API 37 with Google Play services `26.29.32`
- Request shape: the provider-boundary `requests` object from the real Android `CreateDigitalCredentialRequest.requestJson`; AndroidX supplies the inner `digital` object from the browser envelope
- Original browser-envelope raw request SHA-256: `1526cba9d74ed02968922888af4e9f0121950d99d8e9afaee3c7ccb578727349`
- Sanitization: replaced only `requests[0].data.grants.urn:ietf:params:oauth:grant-type:pre-authorized_code.pre-authorized_code` with `REDACTED_TEST_PRE_AUTHORIZED_CODE`
- Sanitized fixture SHA-256: `a8e1c9169ce72b69a8daffe791208621368343637f87e66dc36b37d3bbee9d51`
- Purpose: deterministic provider-boundary regression; no live network or usable authorization code is required

## Capture record

This record separates the immutable request evidence from the mutable live service. The JSON is
the provider-boundary request after AndroidX extracted the browser's inner `digital` object.

### Input checkpoints

| Checkpoint | Captured value |
| --- | --- |
| Request source | `https://digital-credentials.dev/dmv` |
| Browser/device | Android Chrome `150.0.7871.187`, Android API 37, Google Play services `26.29.32` |
| Verified origin | `https://digital-credentials.dev` |
| DC API protocol | `openid4vci1.0` |
| Credential issuer | `https://digital-credentials.dev` |
| Credential configuration | `com.emvco.payment_card` |
| Grant | `urn:ietf:params:oauth:grant-type:pre-authorized_code` |

### Live exploratory checkpoints

| Checkpoint | Observed result | Classification |
| --- | --- | --- |
| Credential Issuer well-known | `GET https://digital-credentials.dev/.well-known/openid-credential-issuer`; the raw response is not embedded in this deterministic fixture | Evidence record incomplete; refresh before claiming a current live result |
| Authorization Server well-known | `GET https://digital-credentials.dev/.well-known/oauth-authorization-server`; the raw response is not embedded in this deterministic fixture | Evidence record incomplete; refresh before claiming a current live result |
| Captured inline AS capability snapshot | Token `https://digital-credentials.dev/openid4vci/token`; challenge `https://digital-credentials.dev/openid4vci/client_attestation_challenge`; no `dpop_signing_alg_values_supported` member | Requirement not discoverable from the captured profile; interoperability unresolved |
| Token request/response | Exploratory request reached the token boundary and received HTTP `400` with sanitized error `invalid_dpop_proof` | Observed external/profile interoperability finding; not treated as proof of an RFC violation |
| `DPoP-Nonce` | No nonce value is retained in this fixture record | Unknown; do not infer a nonce requirement |
| `OAuth-Client-Attestation-Challenge` | Not present in the retained evidence | Unknown for this profile |
| Credential endpoint | Not reached after the token error | Unknown |

The live observation above is diagnostic evidence only. It is not an automated test, and it does
not establish that the current DMV service still behaves identically. The canonical provider
boundary now rejects the captured non-canonical protocol identifier; the fixture remains unchanged
so that this external input is still reviewable and reproducible.
