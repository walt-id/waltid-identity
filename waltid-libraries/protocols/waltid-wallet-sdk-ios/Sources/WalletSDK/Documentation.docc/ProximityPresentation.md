# In-Person Proximity Presentation

Present mdoc credentials to a nearby reader through the Wallet SDK's
authoritative ISO/IEC 18013-5 session.

## Overview

Proximity presentation is separate from the OpenID4VP URL flow. Query
``Wallet/proximityPresentationCapabilities(configuration:)`` without creating
session material, then start one single-use
``ProximitySession``:

```swift
let configuration = ProximityConfiguration()
let capabilities = try await wallet.proximityPresentationCapabilities(
    configuration: configuration
)
let session = try await wallet.startProximityPresentation(
    configuration: configuration
)

for await state in session.states {
    switch state {
    case .checkingPrerequisites(let current):
        showUnavailableMethods(current)
    case .engagementReady(let engagements):
        showEngagements(engagements)
    case .reviewRequired(let review, _):
        showReview(review)
    case .preparationRequired(let plan, let reason):
        showPreparationReview(plan, reason: reason)
    case .completed(let exchanges, _, _):
        showCompletion(exchanges: exchanges)
    case .noData(let exchange):
        showNoData(exchange: exchange)
    case .failed(let error):
        showFailure(error)
    default:
        showProgress(state)
    }
}
```

The default configuration selects QR engagement and Bluetooth Low Energy
retrieval. Capabilities keep implementation, profile permission, runtime
observation, and selection separate for QR, NFC, BLE, and Wi-Fi Aware. An
unprobed method reports `notChecked`. Hosts derive startability from the viable
selected routes; an unavailable optional route does not block a usable route.

A session variant owns its engagement and compatible retrieval. Optional QR
fallback has its own nonempty plan; it may use different bearer choices. Shared
BLE roles/policy and conventional NFC length limits must match because the
session uses one capability probe and one conventional NFC application. The
provisional NFCv2 variant carries its own command limit and always includes
same-channel retrieval. Both conventional plans and NFCv2's optional hybrid
bearer use `wifiAware: true` to select Wi-Fi Aware. A conventional plan with
`bluetoothLowEnergy: nil, wifiAware: true` is valid. iOS reports that transport
unimplemented; Android provides the mandatory NCS-SK-128 path. There is no public
single-choice cipher policy. Concurrent QR and NFC Wi-Fi routes own independent
ephemeral keys and derived service names, and the winning key stays paired with
its exact engagement and handover:

```swift
let nfcConfiguration = ProximityConfiguration(
    session: .nfc(.init(
        handover: .negotiatedHandover,
        retrieval: .init(nfc: .init()),
        qrFallback: .init()
    ))
)
```

When the user selects a prepared NFC engagement, call
``ProximitySession/presentNfc()`` to open the iOS system sheet. The
request waits for NFC resources without replacing the engagement or session
keys. Repeated requests during emulation have no effect. Calls outside NFC
engagement readiness are ignored, and failures arrive through the state stream.
This explicit action also works when the optional presentment assertion has
expired or cannot be acquired during its cooldown.

On iOS, the SDK installs its `CardSession` adapter automatically, but the host
app must also be approved and provisioned by Apple for HCE. The package includes
`HCE.entitlements.example` as a ready-to-copy template for the three ISO
applications used by this flow:

- `D2760000850101` — NFC Forum Type 4 Tag/NDEF engagement;
- `A0000002480400` — conventional mdoc device retrieval;
- `A0000002480401` — provisional NFC Engagement v2.

The example is deliberately not selected by any build configuration. After the
Apple capability and provisioning profile are available, copy its keys into the
host app's entitlements and select that file through the app target's Code
Signing Entitlements setting. Adding these keys without the corresponding Apple
authorization does not create a usable or validly signed HCE build. Capability
checks remain fail-closed when `CardSession` is unsupported or ineligible, and
physical card emulation cannot be exercised in the iOS Simulator.

The SDK requests Apple's optional `NFCPresentmentIntentAssertion` when the user
starts the NFC presentation. The assertion suppresses interference from the
default contactless app while it remains valid, but its documented 15-second
lifetime is not treated as CardSession availability. The SDK holds a successful
assertion without renewing it automatically and continues the explicitly started
CardSession if assertion acquisition fails or the assertion later expires.

For background handling, read ``ProximitySession/systemPresentationActive``
at the transition. It becomes true when the adapter enters `startEmulation()` and
clears when emulation ends, fails, is invalidated, or loses to another engagement.
NFC configuration, an armed card session, and the optional presentment assertion
alone do not grant an exemption. The native and Compose hosts preserve the session
only during that actual system-presentment interval; ordinary backgrounding during
QR display, review, or post-handover BLE still interrupts it. This follows Apple's
[CardSession lifecycle](https://developer.apple.com/documentation/corenfc/cardsession).

Device signature is the default holder-authentication policy. Configure
``ProximityDeviceAuthenticationPolicy/macOnly``,
``ProximityDeviceAuthenticationPolicy/preferSignature``, or
``ProximityDeviceAuthenticationPolicy/preferMAC`` explicitly when the selected
profile permits it. The SDK freezes the chosen method on each credential option
before review and never falls back after consent. The pinned EUDI profile
currently requires ``ProximityDeviceAuthenticationPolicy/signatureOnly``.

### Resolve prerequisites

Render ``ProximityCapabilities/remediationActions`` in product
language. The host app owns permission prompts and settings navigation. After
performing an effect, report its privacy-safe result without attaching platform
objects or raw error text:

```swift
let result = await requestBluetoothPermission()
let outcome: ProximityHostActionResult = result ? .completed : .failed
_ = try await session.dispatch(
    .reportRemediation(.requestBluetoothPermission, outcome)
)
```

The SDK checks that the remediation belongs to the current prerequisite state,
re-queries platform capability after a completed effect, and only then prepares
fresh engagement material.

### Review and approve

``ProximityReview`` contains display-safe reader authentication and
trust facts, document requests, retention intent, eligible credentials,
disclosure alternatives, use-case and purpose assertions, and any recognized
application authorization. These are protocol facts, not UI-derived state.

Build ``ProximitySubmission`` only from the current review and dispatch
`.approve(reviewID: review.reviewID, submission: submission)` or
`.decline(reviewID: review.reviewID)`. A valid decision consumes that identity once;
invalid submissions leave the review available for correction. Acceptance records
the holder's decision, while signing and transmission may still fail.

The SDK owns accepted values and detaches host projections. It binds and revalidates credential, holder-key, reader-trust, status, disclosure,
and application-profile state before it sends a response. A stale or changed
selection returns a typed rejection and does not disclose data. Recovery distinguishes
retrying prerequisites in the active session from starting a new session after a
terminal failure.

Reader-authentication statements remain distinct by scope, document index, and
statement index. During protected-key work,
``ProximityState/authorizingHolderKey(_:)`` carries one
``ProximityHolderAuthorizationRequest`` per approved document so a mixed
signature/MAC response cannot be collapsed into a global authorization method.

### Approve before reconnecting

``ProximityApproval`` keeps approval timing separate from transport selection.
The default is `.askEachTime`. `.prepareBeforeSharing` authenticates the reader,
collects its request without credential disclosure, and ends that connection with
`.preparationRequired(plan, reason)` after transport cleanup. Show the plan's
reader, credential choices, selected fields, declared purpose/retention, and
application requirements before calling `try plan.approve(submission)` from the
holder's deliberate approval action. Honour `requiredElements`, including a
requested mDL portrait. A `.prepared(sharing)` result is used only in a new session
with `configuration.withApproval(.prepared(sharing))`.

Plans expire after ten minutes. Prepared approvals are opaque, wallet-bound,
one-use objects with a 60-second monotonic deadline; they are never persisted.
The SDK compares the exact authenticated reader certificate, request, selected
credential contents and application conditions, and repeats normal reader, key
and credential checks. Changed requests require a new holder decision, and
retries cannot reuse the approval. Use `remainingSeconds` for the ready countdown;
revoke and close on cancellation or backgrounding, except during the actively
owned Core NFC sheet. Permission setup must not retain an armed approval when the
holder leaves the app.

The bundled iOS NFC adapter cannot show an app review during NFC-only retrieval.
It collects an eligible request and closes before review, including in
`.askEachTime`, then requires a second connection. NFC-to-Bluetooth handover
retains connected review. Preparation needs one named, authenticated trusted
reader covering all requested documents; other readers require an interactive
route. Protected-key authorization remains mandatory and subject to OS prompt
availability. A completion receipt describes the locally sent selection and
approval timing; it does not confirm reader-side verification.

### Configure reader trust

Cryptographic reader-authentication validity does not establish application
trust. Provision Reader CA certificates through an out-of-band application
channel and inject `ProximityConfiguredReaderTrustEvaluator` when the wallet
requires a trusted reader:

```swift
let readerTrust = ProximityConfiguredReaderTrustEvaluator(
    configuration: ProximityReaderTrustConfiguration(
        trustAnchors: [
            ProximityReaderTrustAnchor(
                certificateDER: readerCA,
                displayName: "Example reader authority"
            )
        ],
        revocationPolicy: .check(applicationRevocationEvaluator)
    )
)
let configuration = ProximityConfiguration(
    readerPolicy: .requireTrusted,
    readerTrustEvaluator: readerTrust
)
```

The shared evaluator validates the ISO certificate profile, time, and path only
against explicit application anchors. It performs no hidden network request and
ships no reader trust list. Certificates carried by the reader are path inputs,
not implicit anchors. Optional RICAL configuration similarly requires explicit
provider roots and application-owned signer-revocation and constraint policies.
A demo can pass a named test anchor through this same initializer; do not ship
test anchors as production defaults.

Use `ProximityCRLRevocationEvaluator` when the application supplies a complete-CRL transport:

```swift
let crlStatus = try ProximityCRLRevocationEvaluator(
    issuerCertificatesDER: [readerCA],
    scope: .readerCertificateAndIssuingAuthorities,
    fetcher: applicationCRLFetcher
)
// Supply crlStatus to ProximityReaderTrustConfiguration(revocationPolicy: .check(crlStatus)).
```

`ProximityCRLFetcher` receives a Foundation `URL` and byte limit and returns
`ProximityCRLFetchResult.available(der:)` or `.unavailable`. The application owns timeouts,
redirects, destination restrictions and caching. The shared verifier authenticates direct complete
v2 CRLs and checks their scope and freshness; unsupported forms or unavailable status stay
indeterminate. Issuer lookup certificates do not establish trust. Demo trust imports do not install
a CRL client, and this CRL path does not implement OCSP.

For holder-managed settings, validate and preview public trust material before
persisting it:

```swift
let current = ProximityReaderTrustSettings()
let preview = try await ProximityReaderTrustSettingsCodec.prepareImport(
    sourceName: selectedURL.lastPathComponent,
    data: selectedData,
    existing: current
)
showImportReview(preview)

// Only after explicit holder confirmation:
let encoded = try ProximityReaderTrustSettingsCodec.encode(preview.resultingSettings)
saveInAppPrivateStorage(encoded)
```

The importer accepts DER or certificate-only PEM Reader CAs and versioned
walt.id JSON trust bundles with static signed RICAL configuration. It rejects
private keys, PKCS#12/PFX, unknown bundle fields or versions, duplicates,
non-current or invalid trust material, and files larger than 1 MiB. It performs
no persistence or network request. Load one immutable settings snapshot when a
new session starts and call ``ProximityReaderTrustSettings/applying(to:)`` so a
settings change cannot mutate an active session.

### Lifecycle

Only one proximity session can be active per wallet. Cancellation is available
in every non-terminal state where the SDK reports it as legal. Call
``ProximitySession/close()`` when navigation or app lifecycle ends
the journey. Closing is idempotent, and a new session always creates fresh
engagement identifiers and ephemeral key material.

A final request with no returnable data ends in `ProximityState.noData(exchange:)`. No credential
data was sent for that request; earlier exchanges in the same session may have
shared approved data. Render `ProximityReview.readerAuthenticationSummary` for
the request summary: it accounts for whole-request authentication coverage while
preserving malformed, invalid, and revoked authentication warnings. Individual
`readerAuthentication` entries remain available for detailed inspection.
