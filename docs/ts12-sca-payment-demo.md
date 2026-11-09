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

- Required payment values, optional timestamp, payee logo/website, PISP details,
  payment flags, scheduled execution and recurring payment terms. Every supplied leaf needs issuer claim metadata.
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

Unknown payment fields, unsupported currencies, arbitrary JSON
Schema and inherited metadata block consent. PaSO, offline metadata caching and Wallet2
HTTP-service consent are outside this slice. Legacy mdoc uses generic review when enabled.

The contract pins [TS-12 v1.0.1](https://github.com/eu-digital-identity-wallet/eudi-doc-standards-and-technical-specifications/blob/365f734ac95b858343b09aaf56a80f3e1d684119/docs/technical-specifications/ts12-electronic-payments-SCA-implementation-with-wallet.md)
and [SD-JWT VC draft 16 metadata](https://www.ietf.org/archive/id/draft-ietf-oauth-sd-jwt-vc-16.html#section-4).
Transaction claim paths are payload-relative, following TS-12 §3.3.2; its informative
example's `payload` prefix is not treated as another supported contract. Claim display
uses `locale`/`label` from SD-JWT VC; TS-12 UI catalogue entries use `lang`/`value`.
Mandatory claim presence is checked; unknown metadata extensions are ignored, and
transaction claim `sd` is inapplicable. These choices do not claim a resolved standards
erratum or full TS-12 conformance.

### Payment payload contract

The built-in payment type supports `execution_date` or `recurrence`, never both.
All timestamps (`date_time`, `execution_date`, `recurrence.start_date`,
`recurrence.end_date`) use RFC 3339 date-time strings with offsets. This follows the
published JSON Schema where the TS-12 prose describes some values as dates.
`execution_date` must remain at or after the current instant through submission and
native signing; a request that expires during review or signing produces no usable proof.

Recurrence requires `frequency` and supports `start_date`, `end_date`, `number` and
`mit_options`. The latter supports `amount_variable`, `min_amount`, `max_amount`,
`total_amount`, `initial_amount`, `initial_amount_number` and `apr`. All twelve TS-12
frequency codes have English and German display strings. Missing value translations
use the existing preferred-language fallback or block consent. Monetary terms use the
payment currency's exact decimal precision; APR is a percentage independent of currency.
Counts are mathematical integers (`2`, `2.0` and `2e0` are equivalent). No extra payment
network rules or recurrence ordering constraints are inferred.

The four timestamp paths use the demos' shared date/time display policy: current device
locale and timezone, with an explicit timezone label. Consent field paths carry the semantics
through both mobile adapters; labels, exact original values and proof inputs stay unchanged.
Unsupported display spellings remain visible verbatim. See the
[date/time policy](../waltid-applications/waltid-wallet-demo-test-fixtures/date-time-display.md).

`transaction_id` is limited to 1–36 Unicode characters. Unknown fields are rejected
at every object level; nested rejection is product policy because the published schema
explicitly closes only its root. Nonblank strings, a 128-character numeric-token limit
and exponents in -128…128 remain product restrictions. Original encoded transaction
entries are always the input to proof hashing; normalized display strings are not re-encoded.

Community and Enterprise Verifier2 return HTTP 400 for invalid built-in payment payloads
before creating a session. Community's custom-type admission behavior is unchanged;
Enterprise still applies each service's effective type registry. Profile discovery adds
read-only `payloadSchema` for this type. Stored profiles and CRUD inputs retain their
existing fields; schema descriptors cannot override payment rules or issuer instructions.
Both portal editors provide typed controls plus JSON editing, preserve absent versus
false/zero values and exact decimal tokens, and generate future dates for example requests.

### App-reviewed payment display

Compose Android, Compose iOS and native SwiftUI group payee fields in one card, with
level-3 fields under **Payee details**. The exact amount and ISO currency code share
one row under the amount label, including at large text sizes.
They wrap only when the content cannot fit the available width. Required recurring
terms remain on the main review in separate **Payment timing** and **Payment terms** cards.
PISP information is grouped within expandable **Payment details**. Payment cards use
neutral surfaces across all three demos; issuer main/prominent fields never start collapsed.
Grouping never demotes a value from its issuer-defined visibility, and level-4 fields
remain hidden. The demo's [caption policy](../waltid-applications/waltid-wallet-demo-test-fixtures/visual/payment-consent.md)
documents the TS12 rulebook permission needed to omit the two redundant labels in production.

The shared wallet resolves frequency and Boolean values in the selected consent
language: English or German, including regional fallback. Missing complete translations
block authorization; raw codes or `true`/`false` are not display fallbacks.

A valid embedded PNG/JPEG payee logo appears beside the payee name without a visible
logo caption; its issuer label remains available to assistive technology. Remote HTTPS
logos require an explicit **Load payee logo** action and use no cookies, credentials
or redirects. Images are bounded to 512 KiB and 2048 pixels per dimension. Missing,
unsupported or failed images leave no placeholder; branding does not establish payee
trust. HTTPS websites open only on a tap and show the full destination. Other URI
schemes remain plain text. These display conveniences never replace validation or
alter the original transaction bytes.

### Keeping the schema current

The upstream schema is pinned by commit and SHA-256 in
`scripts/ts12-payment-schema.json`; `Ts12PaymentSchema.kt` is generated from that source.
The normal Linux CI job runs the following check. A local mismatch, upstream change,
or failed upstream fetch fails the job; no stale-cache fallback is used.

```bash
python3 scripts/sync-ts12-payment-schema.py --check-upstream
```

For an offline pin check, use `--check`. When upstream changes, review the schema diff
and governing TS-12 text, then explicitly select the reviewed revision:

```bash
python3 scripts/sync-ts12-payment-schema.py --update --ref <reviewed-commit>
```

Update any affected validators, issuer labels, portal controls and independent test vectors,
then run the shared, service and affected platform suites. CI checks upstream when it runs;
there is no transaction-time schema fetch. Adding fields or changing an integrity-pinned
metadata document requires a coordinated rollout and, where necessary, credential reissuance.

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
| Shared TS12 contract and consent tests | All optional leaves, formats, exact numbers, deadlines and English/German frequency values | JVM, JS Node and iOS Simulator |
| Issuer2 integration tests | Configured issuer → wallet → verifier, exact hashes and required policies | Local services and synthetic authorization |
| `paymentDemoTest` | Same successful scenario against public issuer2/verifier2 | Deployment acceptance; no native authentication |
| Android DC-API suite | Payment issuance, registration, selection, both preview settings and rejection of unprotected signing | Ordinary emulator keys cannot authorize SCA |
| Physical Android test | App setup/issuance, Credential Manager, review, native signing and verifier acceptance | Explicit operator interaction |
| Existing iOS app suites | Corresponding physical URL-payment flow in both demos | Explicit operator interaction |

Both portal directories expose `npm test` for their Node utility tests. After building the
Community portal with `npm run build`, run `npm run test:proxy` to exercise the actual
Nitro request-body and HTTP forwarding boundary with independent decimal tokens and
malformed JSON. The proxy test starts isolated local fixtures and creates no real session.

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

Public-demo scheduled/recurring acceptance is tracked separately in [WAL-1665](https://linear.app/walt-new/issue/WAL-1665) and remains blocked on independently approved service/metadata deployments and fresh credentials. Local integration and deterministic UI checks do not require that rollout.
