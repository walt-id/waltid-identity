# Local HAIP verifier and wallet full-plan runs

`CONFORMANCE_EXECUTION_MODE=full-plan` is an opt-in gap-analysis mode for the
OSS verifier, VP wallet and VCI wallet runners. It inventories the complete
suite-offered modules for each selected profile. A gap run is not a certification
submission. Wallet2 adapter results establish backend/API behavior, not mobile-wallet
or DC API UI conformance.

Issuer-specific plan expansion, browser automation and fixture generation belong
to the subsequent issuer PR. Keep the existing issuer runner and shared remote
suite pin unchanged until the coordinated upgrade is validated.

## Suite and local environment

Build conformance-suite `release-v5.3.4` at
`23353b36957d6466010de767c812d2c91ed37638` with Java 21. Before creating a plan,
the runner checks both `version` and `revision` returned by `/api/server`.
A matching image tag alone is insufficient.

Keep the suite's development profile local: bind published ports to loopback,
use a separate Compose project and MongoDB volume, and preserve other services.
Configure HTTPS callbacks reachable from the suite containers and trust the local
certificate using `CONFORMANCE_EXTRA_CA_PEM`. Do not upgrade the shared deployment
or CI pin for this local verification.

Record the Identity and Unified build revisions, harness changes, suite revision,
selected variants and local configuration. Raw suite/application logs can contain
fixture keys and credentials; keep them in ignored local artifacts and redact
shared evidence. Existing full-run evidence remains tied to its tested source.

## HAIP combinations covered

| Role | Format | Selected combination |
|------|--------|----------------------|
| VP wallet | SD-JWT VC | `x509_hash`, `request_uri_signed`, encrypted `direct_post.jwt` |
| VP wallet | mdoc (`iso_mdl`) | `x509_hash`, `request_uri_signed`, encrypted `direct_post.jwt` |
| VP wallet SDK | SD-JWT VC | `dc_api.jwt`; all suite-provided unsigned, signed and multisigned groups |
| VP wallet SDK | mdoc (`iso_mdl`) | `dc_api.jwt`; all suite-provided unsigned, signed and multisigned groups |
| VP verifier | SD-JWT VC | `x509_hash`, `request_uri_signed`, encrypted `direct_post.jwt` |
| VP verifier | mdoc (`iso_mdl`) | `x509_hash`, `request_uri_signed`, encrypted `direct_post.jwt` |
| VCI wallet | SD-JWT VC | Issuer-initiated authorization code, offer by value, client attestation, DPoP |
| VCI wallet | mdoc | Issuer-initiated authorization code, offer by value, client attestation, DPoP |
| VCI wallet | SD-JWT VC | Issuer-initiated authorization code, offer by reference, client attestation, DPoP |
| VCI wallet | mdoc | Issuer-initiated authorization code, offer by reference, client attestation, DPoP |
| VCI wallet | SD-JWT VC | Wallet-initiated authorization code, client attestation, DPoP |
| VCI wallet | mdoc | Wallet-initiated authorization code, client attestation, DPoP |

These are the twelve selected HAIP wallet/verifier certification combinations. DC API entries exercise the real Wallet2 SDK behind a simulated browser/platform transport boundary; they do not establish browser, OS picker or mobile UI behavior. For each
VCI wallet format, the suite supplies immediate/plain, deferred/plain and
immediate/encrypted credential issuance variants, plus applicable FAPI client tests.
Issuer-side combinations remain in the separate issuer work. Selection does not imply that a combination has passed or has certification evidence; inspect its source-pinned run report.

## Selection and commands

Full-plan mode selects the two HAIP verifier variants, the two HAIP VP wallet
HTTP variants, the two DC API SDK combinations, and six VCI wallet profiles: SD-JWT VC and mdoc, each with issuer-initiated offers by value/by reference and wallet-initiated issuance.
Each VCI wallet profile uses client attestation and DPoP; the suite expands immediate/plain,
deferred/plain and immediate/encrypted issuance plus its applicable FAPI client modules.
The additional mdoc, by-reference and wallet-initiated combinations are enabled only in this upgraded-suite mode; ordinary CI selection stays unchanged.
The VCI wallet runner rejects module/variant slices in full-plan mode.
Ordinary CI retains its existing selection, including deferred and FAPI skips.
Explicitly selected checks can exercise those modules independently.

Run from the Unified build checkout after configuring the role's service setup
from [VP-VERIFIER.md](VP-VERIFIER.md), [VP-WALLET.md](VP-WALLET.md), or
[VCI-WALLET.md](VCI-WALLET.md):

```sh
CONFORMANCE_REQUIRED=true \
CONFORMANCE_EXTRA_CA_PEM=/path/to/local-suite-ca.pem \
VERIFIER_NGROK_URL=https://your-local-verifier-endpoint \
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:haipFullPlanVerifierTest \
  --no-configuration-cache
```

`VERIFIER_NGROK_URL` is the existing endpoint setting; a local HTTPS reverse proxy
can be used instead of a public tunnel. Do not use `-PskipLiveConformance=true`.

The three opt-in tasks are:

- `haipFullPlanVerifierTest`
- `haipFullPlanVpWalletTest`
- `haipFullPlanVciWalletTest`

Each sets full-plan and required-suite handling, always executes, and writes its
own JUnit outputs without raw stdout/stderr. Additional role-specific service and
profile settings still need configuring. These tasks are not added to CI.

The VP wallet's mdoc fixture uses a fresh document-signer certificate and signed
CRL generated from committed public test keys. The adapter serves the public CRL;
MSO validity is bounded by the signer's validity. Never use test keys in production.

## Evidence and acceptance

Full-plan output is isolated under `build/reports/openid-conformance-full-plan/`:

- `plans/<plan-id>.json`: suite identity, offered modules and variants, requested
  variant, and SHA-256 of the submitted configuration. Private configuration values
  and keys are excluded.
- `coverage/<plan-id>/<index>.json`: one record per offered module/variant. A partial
  run leaves unexecuted modules explicitly blocked rather than omitting them.
- Role reports retain existing JSON fields and preserve product outcomes separately
  from screenshot/manual evidence gates.

Only completed suite `PASSED` results with the expected observed product outcome
qualify as executed passes. Warnings, review requirements and blockers are counted
separately from failures, remain unaccepted, and prevent successful qualification.
Empty/unavailable/all-skipped runs cannot qualify. Suite-declared inapplicability
remains visible.

Old hardcoded VP exclusions are not inherited. Verifier status checks retain
signature validation; revoked-credential rejection counts as revocation coverage
only when the status policy evaluates the revoked value. Unrelated signer or parsing
errors do not establish revocation. Unsupported ISO identifier-list checking is an
explicit product-capability blocker before module creation.

Result-screen requirements need actual evidence: full-plan mode uploads no
synthetic screenshots. Product defects and genuine UI evidence remain separate
follow-ups; do not weaken validation or acceptance to make a gap run green.

## Wallet driver and ordinary CI behavior

VCI batch modules request two distinct stored holder copies; other modules request
one. Deferred progress resumes only handles created by the current offer, respecting
updated intervals and never redeeming the single-use grant again. An adapter-owned
polling deadline returns a failed claim; parent cancellation propagates.

FAPI modules use the Wallet2 retained issuance SDK and suite-exposed issuer,
preserving the full callback for SDK state and issuer validation. Redirect resolution
does not inherit authorization-request query parameters. For a focused live smoke
check, explicitly run `vciWalletSdJwtHaipFapiHappyPath`. It uses CI-mode reporting and
does not replace a complete full-plan inventory.

The new verifier status/type expectations and expanded deferred/FAPI selection are
opt-in full-plan or explicit-check behavior. Shared driver correctness fixes remain
active in ordinary CI: failed claims return HTTP 400 and unexpected wallet outcomes
cannot pass solely because the suite accepted a protocol exchange. Existing CI
selection, summary fields, acceptance rules and artifact names remain compatible.

## Coordinated rollout

This is the verifier/wallet foundation (PR1). The issuer upgrade will be stacked on
it separately (PR2). Report functionality gaps for allocation rather than including
unassigned product fixes. Validate the combined local HAIP results before upgrading
the shared remote suite pin and merging the combined work into main.

## Additional VCI entry paths

By-reference offers remain references when passed to Wallet2: the adapter wraps
`credential_offer_uri` in the deep-link source expected by the API and routes the
authorization-code grant through its authorization flow. It does not substitute
inline metadata for the by-reference case.

Wallet-initiated modules start the retained SDK session with the suite-exposed
credential issuer and a configuration matching the selected format, without a
credential offer. Batch modules select two configured wallet holder keys. The SDK
continues to own metadata discovery, PAR, PKCE/state, DPoP, client attestation,
credential requests and storage. Deferred session outcomes are polled by their
returned handles and intervals until stored, rejected or the adapter's polling
budget expires. Parent cancellation still propagates.

## DC API SDK transport boundary

The upgraded-suite VP wallet task includes `runDcApiConformanceTests` for both
formats. Ordinary CI leaves this test disabled. The suite creates every offered
request group inside each plan; module applicability uses the group's effective
variants, and reports distinguish repeated module names by those variants.

The driver takes the caller origin from the configured suite origin, passes the
request to Wallet2's real DC API preview and submit handlers, and relays the
returned protocol/data envelope to the suite's browser API submission endpoint.
Only the browser/platform boundary is simulated: no suite-generated mock wallet
response or synthetic UI evidence is used. Foreign-origin submission endpoints
are rejected before invoking the wallet. Unsupported SDK protocols remain
explicit capability blockers, including negative modules whose intended guard
was not reached. SDK rejection and caller cancellation stay distinct.

This mode requires no physical device. Genuine browser/OS integration, wallet
consent and result screenshots remain separate evidence obligations. Neither a
successful SDK response nor the suite result replaces those observations.
