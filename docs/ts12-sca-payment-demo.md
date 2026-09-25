# SD-JWT payment demo

This demo issues a synthetic payment card and presents it with one nested
`urn:eudi:sca:payment:1` transaction. It builds on the TS-12 proof and native
signing support from WAL-1423. It is not a registered banking attestation,
certified wallet, or complete regulated SCA implementation.

## Service setup

Run the matching issuer2 and verifier2 locally, or deploy them before using the
public endpoints. See [backend setup](https://github.com/walt-id/waltid-identity/blob/550a1c1dbdaa5a87c0d853956f2a1d89f242cb81/docs/ts12-sca-backend.md). The required profile is:

- Profile: `scaPaymentCardSdJwt`
- Credential configuration: `sca_payment_card_sd_jwt` (`dc+sd-jwt`)
- Public-demo VCT: `https://issuer2.demo.walt.id/openid4vci/sca_payment_card_sd_jwt`
- Synthetic claims: card scheme `demo`, last four digits `4242`, holder `Jane Doe`.

The VCT uses issuer2's existing self-hosted type endpoint. Verify the credential
configuration appears in issuer metadata before creating an offer. A missing
rollout is a failed prerequisite; substituting an ordinary credential does not
qualify this scenario.

Use the verifier2 OpenAPI **[openid4vp-dc_api][sd-jwt demo payment] urn:eudi:sca:payment:1** example. It requests the three
card claims under DCQL query ID `sca_payment`, a signed request and an encrypted
DC API response. Its `x509_san_dns:verifier.example.com` client uses the example
certificate independently pinned by the demo wallet. Change both the client ID
and configured trust anchors when using a different verifier. Never learn trust
from an incoming request's certificate alone.

The transaction binds the query ID (not a stored wallet credential ID):

```json
{
  "type": "urn:eudi:sca:payment:1",
  "credential_ids": ["sca_payment"],
  "transaction_data_hashes_alg": ["sha-256"],
  "payload": {
    "transaction_id": "8D8AC610-566D-4EF0-9C22-186B2A5ED793",
    "payee": { "name": "Super Store", "id": "merchant-001" },
    "currency": "EUR",
    "amount": 11.56
  }
}
```

Keep `amount` numeric. SCA review remains mandatory when ordinary Credential
Manager previews are disabled. SD-JWT payments use the strict metadata-driven
review below; legacy mdoc payments retain the mandatory generic transaction view.

## Authoritative payment consent (WAL-1417)

The deployed issuer configuration must also publish the full SD-JWT type metadata,
including `category`, the permitted TS-12 transaction type, its built-in schema
URN, field claims and UI labels. The checked-in service and Docker configurations
publish matching English/German catalogues through the existing VCT endpoints.
The mobile apps independently pin the demo issuer's public signing key. A different
deployment must update the issuer URL, public key and VCT configuration together.
No key from an untrusted credential header becomes a trust anchor.

The shared wallet core authenticates the issuer-signed credential, validates its
validity/disclosures, obtains `vct` from those authenticated claims and resolves
one consent snapshot for the actual selected credentials and disclosures. Missing
trust configuration blocks SD-JWT payments. This narrow configured-key policy is
not a general issuer trust registry or certificate-chain qualification system.

Supported scope is one payment authorizing credential plus ordinary disclosures:

- Built-in `urn:eudi:sca:payment:1` schema only, with exactly `transaction_id`,
  `payee.name`, `payee.id`, `currency`, and numeric `amount` in `payload`.
- Exact decimal handling and ISO 4217 minor units; no floating-point rounding.
  Unsupported currencies/shapes or extra fields block consent.
- Inline claims/UI labels or HTTPS references. Resolution is limited to three
  documents, 256 KiB each, three redirects per document, twelve requests total
  and ten seconds per document. Every redirect must remain HTTPS and satisfy
  the configured `WALLET2_PAYMENT_METADATA` URL policy.
- Integrity references are optional; every supplied supported reference is
  verified over the fetched bytes using the strongest supported SRI algorithm.
  Unknown options and unsupported/malformed tokens are ignored as in W3C SRI
  2016; a mismatch still blocks. Wallet policy additionally rejects a supplied
  pin with no supported digest, rather than treating it as unpinned metadata.
  Dangling references and unsupported schema/inheritance block consent.
  A frozen reviewed snapshot is used for submission.
- One complete language range from the host's ordered preferences, using RFC
  4647 progressive lookup (including script and regional fallback). Exact tags
  win, then publisher order resolves regional alternatives; labels can use
  different tags that match that range. No unrelated-language fallback is allowed. Required
  field labels and the affirmative action must all exist in that language.
  Title, hint and denial label may be absent; supplied but invalid/untranslated
  values block consent. Requiring a common language for supplied optional text
  is a conservative wallet policy, not an extra TS-12 SHALL; fallback is tried
  before reporting unavailable translations.
- Claim display follows SD-JWT VC draft 16 section 4.6.2: required `locale`
  and `label`. TS-12 UI catalogue entries separately use `lang` and `value`.
  Unknown metadata extensions are ignored; they never substitute for required
  fields. Transaction claim `sd` is inapplicable.
- Claim paths are resolved against the transaction `payload` for this profile,
  following TS-12 section 3.3.2. No automatic prefix removal is performed.
  TS-12's informative example uses a `payload` prefix and the earlier `lang`
  spelling; this ambiguity is recorded rather than treated as a second wire
  contract. Providers using that representation are not qualified by this demo.
- UI levels 1/2/3/4 mean prominent/main/details/omitted. Omitted values remain
  validated and cryptographically bound. Ordinary disclosure review remains
  visible. Issuer hints never replace the wallet's unsigned-request warning.

URL and Android DC API flows prepare consent under the retained preview's lease.
The UI displays the result before passing its opaque revision on confirmation.
A changed selection, request, signing key, language, or credential invalidates it.
Missing/stale consent blocks the whole selection before any credential is signed.
A cancellation or failure consumes the acknowledgment: DC API retries need a new
review; URL submissions require a new preview. Dismissal/expiry cancels in-flight
work and suppresses late authorization results. The immediate `present` shortcut
cannot authorize an SD-JWT payment.

Persistent/offline type-metadata caching, dynamic JSON Schema evaluation,
inherited type metadata, general action batches,
PaSO, and Wallet2 HTTP-service consent wiring are outside this mobile-demo scope.
See the [pinned TS-12 specification](https://github.com/eu-digital-identity-wallet/eudi-doc-standards-and-technical-specifications/blob/ee91a294c833af5188726fd8c302c641212192aa/docs/technical-specifications/ts12-electronic-payments-SCA-implementation-with-wallet.md)
for the source requirements; this supported subset is not a full-conformance claim.

## Wallet identity

Use an isolated demo installation. In signing-identity setup, select no recovery,
hardware-backed storage and **Current biometrics only**. The SDK discovers
available combinations; it must offer a hardware-backed P-256 key with
`BiometricCurrentSet` before this scenario can proceed. Complete real native
biometric prompts during setup, issuance and presentation. Reopening the wallet
preserves this policy.

Existing timed-biometric and unprotected keys keep their previous behavior and
cannot authorize this payment. Changing protection uses the existing explicit
reprovisioning flow and requires reissuance; it does not change a credential's
holder binding. Enrollment changes can invalidate the current-set key. No
software-key or timed-authorization fallback is permitted.

## Android operator lane

The dedicated `ScaPaymentE2ETest` drives normal app setup and issuance before
opening Credential Manager. It shares the DC API harness with the unattended suite. Use a fresh **preview** app (`id.walt.wallet.compose.test`),
an enrolled physical device with Google Play services and the deployed profile.
It refuses an installation containing wallet material and retains its app-created
wallet for inspection. Use a fresh isolated preview installation for each run.
Unattended CI excludes the shared `id.walt.mobile.test.PhysicalDeviceTest` marker; missing device
capability in this explicit lane fails rather than becoming a passing skip.

From the Identity repository, set `ANDROID_SERIAL` to the intended device and run:

```bash
./gradlew :waltid-applications:waltid-wallet-demo-compose:androidApp:connectedPreviewDebugAndroidTest \
  -PenableAndroidBuild=true \
  -Pandroid.testInstrumentationRunnerArguments.class=id.walt.walletdemo.compose.android.ScaPaymentE2ETest \
  -Pandroid.testInstrumentationRunnerArguments.wallet.sca=approve
```

Approve setup/issuance prompts, then approve the payment prompt after reviewing
its values. Run again with `wallet.sca=cancel`: approve setup and issuance, then
leave the final biometric prompt untouched. The test observes it and presses
Android Back, which also works on devices without a Cancel button. It requires
no credential response and no verifier success. The test allows three minutes for the
payment operation. Never reset an existing preview wallet merely to satisfy the
test's empty-installation precondition without its owner's approval.

The success case checks the issued holder binding, real Credential Manager
registration and selection, mandatory review with the setting disabled, and the
returned KB-JWT's audience, nonce, `jti`, response mode, native factor categories,
SHA-256 algorithm and independently computed hash over the original encoded
entry. It uses a signed request with a clear response so it can inspect the
proof, then requires verifier acceptance and execution of
`dc+sd-jwt/transaction-data-hash-check`. The OpenAPI example separately demonstrates
the encrypted response. Cancellation must produce no successful credential response.

For `wallet.sca=missing-translation`, use an isolated issuer fixture that publishes
the affirmative action only in a language absent from the other payment labels.
Approve setup and issuance. The test requires the visible missing-language error,
disabled submission and no response; it must never reach payment authorization.
Restore the positive metadata after this run. Never change a shared demo deployment
to create a negative fixture.

Native factors remain possession/inherence `other`: the platform contract does
not attest a biometric modality or a certified WSCD category. For SD-JWT the
transaction proof is in the KB-JWT. The existing mdoc demo uses device-signed
transaction data plus MSO authorizations instead; no mdoc authorization field is
added to this SD-JWT issuer profile.

## Unattended checks

The Android DC API CI phase and both iOS demo CI lanes also run four real-app
payment cases: approval, review cancellation, denied authentication and missing
required translations. Each provisions through the normal setup and issuance UI.
Approval checks the returned KB-JWT and executed verifier policies; negative cases
require no credential response and no verifier success. The iOS cases use URL
presentation; Android uses Credential Manager with ordinary previews disabled.

Only authentication is simulated. A dedicated Gradle init script selects a test
implementation at compile time and redirects every output into `build/sca-app-e2e`.
Normal builds always compose the native authorizer. There is no runtime flag or
public SDK option to bypass authorization. Android uses a separate application ID,
`id.walt.wallet.compose.sca.e2e`; iOS uses an isolated simulator XCFramework.
Publishing and production APK tasks through this init script are rejected.
These artifacts are test fixtures and must never be distributed.

Run from the Identity repository against a deployment containing the matching
issuer profile and complete metadata:

```bash
.github/scripts/mobile-ci/run-android-sca-app-tests.sh
.github/scripts/mobile-ci/run-ios-sca-app-tests.sh \
  'platform=iOS Simulator,id=<simulator UUID>' native compose
```

Use a dedicated Android emulator with the same Google Play services/DC API
prerequisites as the ordinary suite. Its isolated SCA app data is reset by the
runner, and the isolated test packages are uninstalled afterwards. The negative
language fixture restricts the wallet's preferences to French,
which the demo metadata does not supply; production language fallback is unchanged.
A backend without the payment profile fails the lane rather than skipping it.
Deploy the backend slice before relying on public-demo CI acceptance. Local issuer
and verifier deployments can qualify the app changes before that rollout, using
local endpoint and trust configuration; record that configuration with the results.

`ScaPresentationInteropTest` independently verifies real software signatures and
nested transaction hashes with Nimbus. Shared consent tests cover metadata trust,
language lookup, changed selections, stale acknowledgment and cancellation during
metadata preparation. Ordinary mdoc/DC API tests retain the normal SDK composition.
Simulated authentication proves app integration, never native factors or regulated SCA.

## iOS and evidence

Compose iOS and native SwiftUI expose and restore the same per-use signing
policy. Use their existing OpenID4VP URL/deep-link entry points on a physical
Secure Enclave iPhone. Create a `cross_device` verifier session with the
same DCQL query and transaction data and open its returned request URL in the
wallet. The automated iOS fixture uses an unsigned `direct_post` request bound to
its response URI and retains the wallet warning; it does not qualify signed or
encrypted URL responses. Both demos expose
`PublicDemoBackendE2ETests/testScaPaymentWithNativeAuthorization`; explicitly select
that method with `WALLET_SCA_OPERATOR=approve` in the XCTest runner environment.
Each creates a unique wallet, drives no-recovery / Secure Enclave / current-set
setup, receives through the app, reviews the nested transaction, signs natively,
and requires verifier success. Unselected or simulator runs skip this physical
method; those skips do not count as native acceptance. The iOS Identity Document provider extensions handle mdoc Annex C; they
are not an SD-JWT provider route.

Record source revisions and distinguish local issuer/presenter tests, simulator
UI checks, physical Android Credential Manager execution, physical iOS URL
execution and hosted CI. A compiled operator test, a synthetic authorizer, or a
simulator screenshot is not evidence of successful native key authorization.

### Standards baseline and evidence

Normative requirements govern this implementation; provider acceptance alone is
not a conformance result. The metadata slice pins [TS-12 v1.0.1](https://github.com/eu-digital-identity-wallet/eudi-doc-standards-and-technical-specifications/blob/ee91a294c833af5188726fd8c302c641212192aa/docs/technical-specifications/ts12-electronic-payments-SCA-implementation-with-wallet.md)
and [SD-JWT VC draft 16 metadata](https://www.ietf.org/archive/id/draft-ietf-oauth-sd-jwt-vc-16.html#section-4).
This does not change the OpenID4VCI/OpenID4VP credential-format version contract.
The draft-16 metadata sections 4.3.1/4.3.4/4.6/5 correspond to TS-12's older
section references 6.3.1/6.3.4/9/7. Claim `locale` replaced `lang` in draft 12;
TS-12's own UI catalogue continues to require `lang`.

The payload-relative claim-root choice above is an explicit interpretation of
TS-12 section 3.3.2, not a resolved standards erratum. The bounded schema,
no-inheritance implementation, fixed payment shape and configured issuer trust
are product scope limits. Local service, independent provider, native-device
and formal conformance evidence must be reported separately.
