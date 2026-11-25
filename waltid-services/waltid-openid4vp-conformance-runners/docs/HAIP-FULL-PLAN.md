# Local HAIP full-plan gap runs

`CONFORMANCE_EXECUTION_MODE=full-plan` is an opt-in gap-analysis mode. Ordinary CI
continues using its existing suite, module selection, acceptance rules and report
locations. A full-plan gap run is not a certification submission.

Use the OSS issuer, verifier and Wallet2 adapters. Wallet2 adapter results establish
backend/API behavior; they do not establish mobile-wallet or DC API UI conformance.

## Suite and local environment

Build conformance-suite `release-v5.3.4` at
`23353b36957d6466010de767c812d2c91ed37638` with Java 21. The runner verifies both
`version` and `revision` from the running suite's `/api/server` before creating a
plan. A matching image tag alone is insufficient.

Keep the suite's development profile local: bind published ports to loopback,
use a separate Compose project and MongoDB volume, and preserve other local
services. Configure HTTPS callbacks reachable from the suite containers and trust
the local certificate using `CONFORMANCE_EXTRA_CA_PEM`. Do not change the shared
conformance deployment for a local gap run.

Record the Identity and Unified build revisions, the uncommitted harness diff,
suite revision, selected variant axes and local configuration before each run.
Raw suite/application logs contain fixture keys and credentials; keep them in
ignored local artifacts. Review and redact any evidence before sharing it.

## Strict issuer fixture

The combined basic/HAIP CI fixture leaves PAR and PKCE optional and contains static
mdoc signing material and sample data. Use the opt-in generator for a strict local
HAIP issuer run; it does not change the ordinary CI fixture:

```sh
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:prepareHaipFullPlanIssuerFixture \
  -PhaipIssuerBaseUrl=https://localhost.emobix.co.uk:9443 --no-configuration-cache
```

The output is `build/conformance/haip-issuer-fixture/` inside the runner module:

- `issuer-service.conf`: the existing CI service settings with PAR and PKCE required.
- `issuer2-profiles.conf`: inherited HAIP profiles with fresh signer certificates,
  bounded mdoc validity, current age claims, two-letter nationality, and absent
  optional biometric values omitted. Basic profiles are preserved.
- `credential-trust-anchor.pem`: the credential issuer trust anchor for this run.
- `issuer.crl`: a fresh signed CRL, to serve at the configured base URL followed by
  `/conformance/issuer.crl` with content type `application/pkix-crl`.

Point the isolated issuer at the generated service/profile files and give the runner
the generated credential trust anchor. Configure OAuth login, metadata, callbacks
and HTTPS separately as described in [VCI-ISSUER.md](VCI-ISSUER.md). Serve only the
public CRL file; do not expose the configuration directory. It contains public
test private keys, which must never be used for production or copied into reports.
Generate these files again before a new run; the certificates and CRL are time-bound.

Targeted setup verification may use ordinary module filters and a separate report
directory. Label it as targeted evidence: it does not replace a complete full-plan
inventory or establish that all certification cases pass.

## Selection

Full-plan mode rejects `OPENID4VCI_CONFORMANCE_MODULES`,
`OPENID4VCI_CONFORMANCE_EXCLUDED_MODULES` and
`OPENID4VCI_CONFORMANCE_ADDITIONAL_MODULES`. Module groups must be unset or `all`.
It also rejects the deferred-only issuer slice and VCI wallet `requiredModules` /
`requiredVariant` slices. Selecting a complete profile/variant remains supported.
Discovery-only issuer settings are rejected, and a missing issuer target fails the
full-plan task rather than skipping its test.

The VP verifier and VP wallet matrix entry points select their two HAIP variants
in this mode. VCI issuer variant filters select formats, initiation flows,
and proof modes independently of module selection. The suite owns the encryption
module variants of a HAIP issuer plan; full-plan mode runs all of them and
deduplicates equivalent plan requests. An encryption-only slice is rejected. Use
`VciWalletConformanceTests.vciWalletSdJwtVcAuthorizationCodeHaipFullTarget` for the
existing SD-JWT, issuer-initiated, by-value HAIP wallet profile.

Run from the Unified build checkout with the appropriate local service setup:

```sh
CONFORMANCE_EXECUTION_MODE=full-plan \
CONFORMANCE_REQUIRED=true \
CONFORMANCE_EXTRA_CA_PEM=/path/to/local-suite-ca.pem \
VERIFIER_NGROK_URL=https://your-local-verifier-endpoint \
./gradlew :waltid-services:waltid-openid4vp-conformance-runners:haipFullPlanVerifierTest \
  --no-configuration-cache
```

`VERIFIER_NGROK_URL` is the existing endpoint setting; a local HTTPS reverse proxy
can be used instead of a public tunnel. Do not use `-PskipLiveConformance=true`.
The `haipFullPlanVerifierTest`, `haipFullPlanVpWalletTest`,
`haipFullPlanIssuerTest` and `haipFullPlanVciWalletTest` tasks set full-plan mode and
required-suite handling, always execute, and retain separate JUnit reports without
raw stdout/stderr. Their additional service/profile settings still need configuring.
Run roles sequentially when using the ordinary shared `test` task.

## Evidence and outcomes

Full-plan output is isolated under `build/reports/openid-conformance-full-plan/`:

- `plans/<plan-id>.json`: suite identity, offered modules and module variants,
  requested variant, and a SHA-256 fingerprint of the submitted configuration.
  Configuration values and private keys are excluded.
- `coverage/<plan-id>/<index>.json`: one record per offered module. A module with
  no returned result remains explicitly blocked; a partial run cannot silently
  erase the rest of the plan. These records also preserve duplicate module names
  offered with different variants.
- Role reports retain the existing JSON fields and add execution-mode information.
  Issuer matrix reports retain their existing shape and reference the plan IDs.

Only a completed suite `PASSED` result with a successful observed product outcome
is accepted as an executed pass. `WARNING` and `REVIEW` remain visible and require
follow-up. Empty/unavailable/all-skipped runs cannot qualify as successful full
plans. Suite-declared variant inapplicability is recorded separately.

Old hardcoded VP suite-bug exclusions are not inherited. VCI wallet batch modules
request two distinct holder copies; other modules request one. Deferred issuance
resumes only the new wallet-retained handles and respects their polling intervals,
without redeeming the offer again. FAPI client modules use the suite's exposed
credential issuer and the Wallet2 retained issuance SDK, preserving the complete
authorization callback for its state and issuer checks. A separately running HTTP
adapter without that SDK driver cannot establish FAPI coverage. The default shared
CI profile keeps its existing FAPI exclusion; full-plan mode never inherits it.
For a fast live SDK/PAR/token/credential smoke check before the full matrix, run
`./gradlew :waltid-services:waltid-openid4vp-conformance-runners:vciWalletSdJwtHaipFapiHappyPath`.
That explicit check uses CI-mode reporting and is not certification evidence.

Browser handoffs extract the actual offer from the adapter's HTTP link. Redirects
resolve the new Location without inheriting authorization-request query parameters,
so SDK callback validation receives only the real callback. HTTP error responses
on the issuer's browser path are recorded promptly rather than reported as login
timeouts. The repeated-PAR case leaves the first login page untouched and submits
credentials only after the suite requests the second visit. Error-page screenshot
requests remain explicit blockers after the page is exercised; a URI already in
the visited list can still be requested again for a later error-page check.
Intentional `WaitFor62Seconds` / `WaitForExpiry` pauses use the suite's declared
duration plus the normal processing window. This changes harness polling only;
issuer authorization-code and request-URI lifetimes remain product configuration.
Browser diagnostics omit request query values and encoded authorization state.

Verifier status checks retain signature validation. A revoked-credential rejection
counts as revocation coverage only when the status policy evaluates the revoked
value; unrelated signer or parsing errors remain failures. ISO identifier-list
revocation is recorded as an unsupported product capability before starting a
module, rather than claiming a generic rejection proves identifier-list support. The issuer's user-rejection module is blocked until a real rejection
interaction is provided; the login automation must not approve that request.
Missing result-screen evidence is also a blocker: full-plan mode does not
upload the CI runner's synthetic placeholder images.

Classify findings from the underlying suite checks and application results as
runner/fixture, environment, product, or unresolved. Preserve the original suite
verdict even when a failure is later attributed to the environment. Fixing every
product capability and submitting formal certification evidence are separate work.
