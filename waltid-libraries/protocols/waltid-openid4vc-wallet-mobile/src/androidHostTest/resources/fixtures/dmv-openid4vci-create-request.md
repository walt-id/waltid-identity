# DMV Digital Credentials API fixture

- Source: `https://digital-credentials.dev/dmv`
- Captured: 2026-08-13 from Android Chrome `150.0.7871.187` on Android API 37 with Google Play services `26.29.32`
- Request shape: the provider-boundary `requests` object from the real Android `CreateDigitalCredentialRequest.requestJson`; AndroidX supplies the inner `digital` object from the browser envelope
- Original browser-envelope raw request SHA-256: `1526cba9d74ed02968922888af4e9f0121950d99d8e9afaee3c7ccb578727349`
- Sanitization: replaced only `requests[0].data.grants.urn:ietf:params:oauth:grant-type:pre-authorized_code.pre-authorized_code` with `REDACTED_TEST_PRE_AUTHORIZED_CODE`
- Purpose: deterministic provider-boundary regression; no live network or usable authorization code is required
