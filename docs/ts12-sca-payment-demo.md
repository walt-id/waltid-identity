# SD-JWT payment demo

The demo issues a synthetic payment card and presents one nested
`urn:eudi:sca:payment:1` transaction using the existing TS-12 proof and native
signing support with authoritative, localized app review when enabled. This is not a registered banking attestation, certified wallet
or complete regulated SCA implementation.

## Service setup

Run matching issuer2/verifier2 locally or deploy the [backend configuration](ts12-sca-backend.md)
to the public services. The issuer must advertise `sca_payment_card_sd_jwt`
(`dc+sd-jwt`), issued through profile `scaPaymentCardSdJwt`. Its public-demo VCT is
`https://issuer2.demo.walt.id/openid4vci/sca_payment_card_sd_jwt` and its synthetic
card claims are scheme `demo`, last four digits `4242` and holder `Jane Doe`.

Use verifier2's **\[openid4vp-dc_api\]\[sd-jwt demo payment\]
urn:eudi:sca:payment:1** OpenAPI example. It requests the three card claims under
DCQL query `sca_payment`, with a signed request and encrypted response. The example
`x509_san_dns:verifier.example.com` client uses a certificate independently pinned
by the demo wallet; a different verifier requires matching client/trust configuration.

The transaction refers to the DCQL query ID, not a stored credential ID:

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

SD-JWT binds hashes of the original encoded transaction entries in the KB-JWT.
The existing mdoc flow instead uses device-signed transaction data with MSO key
authorizations. Display normalization must not change either proof's input.

## Authoritative consent

The selected credential designates its SCA type metadata; verifier profile allowlists
remain admission controls, not the authority for payment instructions. The demos
independently pin the issuer's public key. A different deployment requires matching
issuer URL, VCT and key configuration. Missing trust configuration blocks authoritative app review.

Shared Kotlin authenticates the credential and prepares one localized consent snapshot.
Compose Android/iOS and SwiftUI render that model before submitting its opaque revision.
The revision binds the request, selected credentials/disclosures, signing key and locales;
invalid or stale revisions block app-reviewed submissions before signing. URL payments
also reject missing revisions. Failed app-reviewed attempts require fresh review;
URL submissions also require a new preview. The immediate
URL presentation shortcut cannot bypass consent. Dismissal/expiry cancels in-flight work.

Android DC-API submissions without an app-review revision rely on platform confirmation,
including when **Show wallet review** is disabled. They do not resolve the authoritative
display metadata. Native SCA authorization and transaction-byte binding remain enforced;
Credential Manager's payment summary does not establish TS-12 display conformance.

This slice supports one SD-JWT authorizing credential plus ordinary disclosures:

- Required one-off payment values, optional timestamp, payee logo/website, PISP
  details and payment flags. Every supplied leaf needs a label; URIs remain plain text.
  Amounts use exact decimal handling and ISO 4217 minor units, without rounding.
- Inline claims/UI labels or HTTPS references, with at most three documents,
  256 KiB each, three redirects each and ten seconds per document. Every redirect
  must satisfy HTTPS and the configured `WALLET2_PAYMENT_METADATA` URL policy.
- Supplied integrity references verified over fetched bytes using the strongest
  supported SRI algorithm. Unknown/invalid tokens and unknown options follow SRI
  parsing; wallet policy rejects a pin without a supported digest. Metadata is
  frozen for the review, without persistent caching.
- One complete language range from ordered preferences, with regional/script fallback.
  Required field/action labels must exist. Title, hint and denial label may be absent;
  absent hints are not shown and denial uses wallet text. Supplied invalid or untranslated
  optional text blocks consent; that stricter language rule is wallet policy.
- Visualisation 1/2/3/4 maps to prominent/main/details/omitted; the default is 3.
  Omitted values remain validated and signed. Ordinary disclosure review and the
  wallet's independent unsigned-request warning remain visible.

Unknown payment fields, unsupported currencies, scheduling/recurrence, arbitrary JSON
Schema and inherited metadata block consent. PaSO, offline metadata caching and Wallet2
HTTP-service consent are outside this slice. Legacy mdoc uses generic review when enabled.

The contract pins [TS-12 v1.0.1](https://github.com/eu-digital-identity-wallet/eudi-doc-standards-and-technical-specifications/blob/ee91a294c833af5188726fd8c302c641212192aa/docs/technical-specifications/ts12-electronic-payments-SCA-implementation-with-wallet.md)
and [SD-JWT VC draft 16 metadata](https://www.ietf.org/archive/id/draft-ietf-oauth-sd-jwt-vc-16.html#section-4).
Transaction claim paths are payload-relative, following TS-12 §3.3.2; its informative
example's `payload` prefix is not treated as another supported contract. Claim display
uses `locale`/`label` from SD-JWT VC; TS-12 UI catalogue entries use `lang`/`value`.
Mandatory claim presence is checked; unknown metadata extensions are ignored, and
transaction claim `sd` is inapplicable. These choices do not claim a resolved standards
erratum or full TS-12 conformance.

## Wallet setup

Use an isolated demo installation with no recovery, hardware-backed storage and
**Current biometrics only**. The SDK must offer hardware-backed P-256 with
`BiometricCurrentSet`. Approve native prompts during setup, issuance and payment.
Reopening the wallet preserves this policy. Existing timed-biometric or unprotected
keys cannot authorize this payment; changing the key requires explicit reprovisioning
and credential reissuance. Enrollment changes can invalidate a current-set key.

Android uses Credential Manager. Compose iOS and native SwiftUI use the existing
OpenID4VP URL/deep-link route with a `cross_device` verifier session. Apple's mdoc-only
Identity Document provider extensions are outside this SD-JWT route.

Android's **Show wallet review** setting applies to payments too. When disabled,
the demo relies on Credential Manager confirmation and submits without an app review.
Native SCA authorization and transaction-byte binding remain enforced; the platform
summary does not establish complete TS-12 display compliance.

## Coverage

| Existing suite | Payment coverage | Boundary |
| --- | --- | --- |
| Protocol SCA tests | Exact hashes, KB-JWT claims, transport variants and authorization failures | Synthetic authorization |
| Issuer2 integration tests | Configured issuer → wallet → verifier, exact hashes and required policies | Local services and synthetic authorization |
| `paymentDemoTest` | Same successful scenario against public issuer2/verifier2 | Deployment acceptance; no native authentication |
| Android DC-API suite | Payment issuance, registration, selection, both preview settings and rejection of unprotected signing | Ordinary emulator keys cannot authorize SCA |
| Physical Android test | App setup/issuance, Credential Manager, review, native signing and verifier acceptance | Explicit operator interaction |
| Existing iOS app suites | Corresponding physical URL-payment flow in both demos | Explicit operator interaction |

The existing Linux Gradle job runs `paymentDemoTest` for `ci:mobile-dc-api` PRs
and eligible main builds, reporting through its ordinary JUnit check and `ci-gate`.
The task never reuses cached success. Both it and the automatic DC-API payment
case require the service configuration described above.

```bash
./gradlew :waltid-services:waltid-issuer-api2:paymentDemoTest
```

For a local service deployment, this Gradle task accepts explicit
`-Ppayment.issuerUrl=https://...` and `-Ppayment.verifierUrl=https://...` overrides.
The app fixtures use their configured demo endpoints. Missing required issuer
instructions prevent app-reviewed payment authorization and signing.

## Physical acceptance

The Android method `ScaPaymentE2ETest.sharesScaSdJwtWithNativeAuthorization` shares
the existing DC-API harness. Select an enrolled physical device using `ANDROID_SERIAL`
and a fresh preview installation (`id.walt.wallet.compose.test`):

```bash
./gradlew :waltid-applications:waltid-wallet-demo-compose:androidApp:connectedPreviewDebugAndroidTest \
  -PenableAndroidBuild=true \
  -Pandroid.testInstrumentationRunnerArguments.class=id.walt.walletdemo.compose.android.ScaPaymentE2ETest \
  -Pandroid.testInstrumentationRunnerArguments.wallet.sca=approve
```

The test refuses existing wallet material. It checks holder binding, wallet review
with previews enabled, native authorization, KB-JWT claims/exact hashes
and required verifier policies. It uses a signed request with a clear response so
it can inspect the proof; the OpenAPI example demonstrates response encryption.

Each iOS demo uses `PublicDemoBackendE2ETests/testScaPaymentWithNativeAuthorization`;
select that method on a physical iPhone with `WALLET_SCA_OPERATOR=approve` in the
XCTest runner environment. It creates a unique wallet and exercises setup, issuance,
review and native signing using unsigned `direct_post`, retaining the wallet warning.
It does not qualify signed/encrypted iOS URL responses.

Unattended Android lanes exclude `PhysicalDeviceTest`; unselected/simulator iOS runs
skip the physical method. Such exclusions are not native-authentication evidence.
The returned possession/inherence categories remain `other`, since the platform
contract does not attest a biometric modality or certified WSCD category. Record
source revisions and backend endpoints with physical results; live ITB and formal
conformance remain separate evidence.
