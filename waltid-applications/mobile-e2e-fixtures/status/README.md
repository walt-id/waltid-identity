# Controlled credential-status issuer

This test-only, ephemeral issuer supplies real signed credentials and authenticated status lists
through the normal OID4VCI wallet flow. It does not override wallet display state or install a
background scheduler. It is not a production issuer or a conformance implementation.

Run with scoped dependencies, an unused pair of loopback ports and an HTTPS tunnel forwarding
**only the public port**:

```sh
uv run --with cryptography --with pillow python \
  waltid-applications/mobile-e2e-fixtures/status/issuer.py \
  --issuer-url https://YOUR-TEST-ENDPOINT --port 8089 --control-port 8090 --ttl 20
```

The second listener is local control traffic and must never be tunneled. POST `/offers` there
with `{"configuration":"staff_access"}` or `event_pass` to obtain a fresh five-minute offer.
`staff_access_alternative` supplies another option of the workplace-access type. The public listener
exposes only metadata, token/nonce/credential endpoints, credential artwork and signed status lists.
Keys exist only in memory. Offers, tokens and nonces are bounded and single use; access logs are off.
The authorization metadata advertises only the pre-authorized grant and intentionally omits
`response_types_supported`, as OID4VCI 1.0 permits. It does not advertise an interactive flow.
Status entries explicitly include `statusSize: 1`, interoperating with verifier parsers which
require the field while retaining the standard's one-bit semantics.

GET the control listener's `/state` to identify the issued reference index. POST `/status` with
`{"purpose":"revocation","index":0,"value":1}` to revoke the corresponding access credential.
Revocation cannot be undone. Suspension uses purpose `suspension` and permits 1 → 0. POST `/outage`
with `{"enabled":true}` to return HTTP 503 for lists. Cross the short signed freshness deadline
without changing the device clock to inspect retained evidence and original check times.

The fixture uses [OID4VCI 1.0](https://openid.net/specs/openid-4-verifiable-credential-issuance-1_0.html)
pre-authorized issuance with JWT key proofs and the
[Bitstring Status List Recommendation](https://www.w3.org/TR/2025/REC-vc-bitstring-status-list-20250515/).
The signing/list structure follows the existing `StatusTestIssuer` protocol fixture. Normal SDK
discovery, authentication, cache, persistence and sharing guards remain in use. Public-demo PID
issuance and actual verifier sessions are separate journey inputs; this service does not fabricate
verifier acceptance.

## Product journey drivers

`../wallet-gallery-drivers.json` registers `wallet-credential-status` separately from the basic
`wallet-simple` journey. Its exact test identifiers and expected captures are authoritative.
The shared iOS driver normally issues public-demo PID, controlled conference/access credentials,
a clear access alternative and a public-demo EMVCo payment attestation. It covers real suspension,
irreversible revocation, outage/expiry with original evidence times, process relaunch, mixed sharing,
explicit verifier status enforcement, payment review and settings. Android has the corresponding
lifecycle, alternative-selection, enforcement and payment driver. Its initial relaunch checks only
a fresh Activity; the separate `wallet-credential-status-restart` driver checks a changed process ID,
all five stored credentials and the original retained check time with status-list retrieval unavailable.

Run these exact tests serially against one fixture. Set iOS test-runner environment
`WALLET_STATUS_CONTROL_URL=http://127.0.0.1:8090` and `WALLET_GALLERY_CAPTURE=1` through
XcodeBuildMCP, with Debug and the test-owned simulator explicit. Both iOS targets compile the
shared capture implementation in `../ios/WalletGalleryEvidence.swift`.

For Android, use only the disposable Preview Debug app on a named emulator. Reverse loopback
port 8090, provide `walletStatusControlUrl`, `walletGalleryCapture=true`, a fresh 32-hex
`walletGalleryRunId`, and a writable `additionalTestOutputDir` to the registered instrumentation
test. An app-private cache directory works on scoped-storage images; collect it with the debug
app's `run-as` permission. Release any CLI UI-inspection instrumentation before starting the test:
Android allows only one UiAutomation owner. Reset only this isolated test app before a full run.
After a successful lifecycle run, force-stop that Preview app without clearing its data. Start a
separate instrumentation invocation selecting `credentialStatusAfterProcessRestart`, with
`walletStatusRetentionVerification=true` and the same control URL. Its expected IDs/time come from
a test-only private-cache receipt written by the lifecycle driver; it never injects wallet state.

The installed generic live-gallery catalog may not know this product scenario. Invoke the
registered product tests directly until its catalog is extended; naming a scenario does not
establish generic-runner support. Bind final captures to a clean source SHA, companion revisions,
driver/contract hashes, runtime and request digests; require the complete expected capture set and
passing test outcomes, then audit hashes/dimensions and inspect every image. Record the ordinary
public verifier's default-policy result separately from explicit clear-bit enforcement. A rejected
revoked credential only qualifies when credential/presentation authentication succeeded and the
status policy rejected bit 1. The EMVCo journey does not qualify physical biometric TS-12 SCA.
The generic payment authorization binds the PID, using the existing software-key demo path;
the EMVCo attestation remains requested payment data. That public mdoc template lacks the
`keyAuthorizations` needed to bind transaction hashes to it. The journey preserves that SDK
rejection boundary and does not claim an EMVCo-authorized or biometric payment.

Registration and test compilation are not execution qualification. Keep offers, tokens, raw
credentials and diagnostics private; publish only inspected final captures and safe provenance.
