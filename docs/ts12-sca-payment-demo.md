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

Keep `amount` numeric. The generic review shows the nested payee leaves and exact
amount/currency. SCA review remains mandatory when ordinary Credential Manager
previews are disabled. Authoritative localized consent metadata is the separate
WAL-1417 layer; this first demo uses the existing generic transaction renderer.

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

Native factors remain possession/inherence `other`: the platform contract does
not attest a biometric modality or a certified WSCD category. For SD-JWT the
transaction proof is in the KB-JWT. The existing mdoc demo uses device-signed
transaction data plus MSO authorizations instead; no mdoc authorization field is
added to this SD-JWT issuer profile.

## Unattended checks

The Android DC API CI phase and both iOS demo CI lanes also run three real-app
payment cases: approval, review cancellation and denied authentication. Each
provisions through the normal setup and issuance UI.
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
issuer profile:

```bash
.github/scripts/mobile-ci/run-android-sca-app-tests.sh
.github/scripts/mobile-ci/run-ios-sca-app-tests.sh \
  'platform=iOS Simulator,id=<simulator UUID>' native compose
```

Use a dedicated Android emulator with the same Google Play services/DC API
prerequisites as the ordinary suite. Its isolated SCA app data is reset by the
runner, and the isolated test packages are uninstalled afterwards.
A backend without the payment profile fails the lane rather than skipping it.
Deploy the backend slice before relying on public-demo CI acceptance. Local issuer
and verifier deployments can qualify the app changes before that rollout, using
local endpoint and trust configuration; record that configuration with the results.

`ScaPresentationInteropTest` independently verifies real software signatures and
nested transaction hashes with Nimbus. Ordinary mdoc/DC API tests retain the
normal SDK composition.
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
