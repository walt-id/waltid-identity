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

## Selection and commands

Full-plan mode selects the two HAIP verifier variants, the two HAIP VP wallet
variants, and the existing SD-JWT issuer-initiated/by-value VCI wallet profile.
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
