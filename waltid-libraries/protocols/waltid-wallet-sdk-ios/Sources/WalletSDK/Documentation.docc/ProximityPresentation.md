# In-Person Proximity Presentation

Present mdoc credentials to a nearby reader through the Wallet SDK's
authoritative ISO/IEC 18013-5 session.

## Overview

Proximity presentation is separate from the OpenID4VP URL flow. Query
``Wallet/proximityPresentationCapabilities(configuration:)`` without creating
session material, then start one single-use
``ProximityPresentationSession``:

```swift
let configuration = ProximityPresentationConfiguration()
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
    case .reviewRequired(let review):
        showReview(review)
    case .completed(let exchanges, _):
        showCompletion(exchanges: exchanges)
    case .failed(let error):
        showFailure(error)
    default:
        showProgress(state)
    }
}
```

The default configuration selects QR engagement and Bluetooth Low Energy
retrieval. Capabilities keep implementation, profile permission, runtime
availability, and selection separate for QR, NFC, BLE, and Wi-Fi Aware. A
selected unavailable method prevents preparation rather than being silently
substituted.

Select NFC through the typed engagement and retrieval contracts. Conventional
NFC retrieval lengths and provisional NFCv2 engagement lengths are intentionally
different types:

```swift
let nfcConfiguration = ProximityPresentationConfiguration(
    engagement: .qrAndNFC(.negotiatedHandover),
    retrieval: .conventional(.init(nfc: .init()))
)
```

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

Device signature is the default holder-authentication policy. Configure
``ProximityDeviceAuthenticationPolicy/macOnly``,
``ProximityDeviceAuthenticationPolicy/preferSignature``, or
``ProximityDeviceAuthenticationPolicy/preferMAC`` explicitly when the selected
profile permits it. The SDK freezes the chosen method on each credential option
before review and never falls back after consent. The pinned EUDI profile
currently requires ``ProximityDeviceAuthenticationPolicy/signatureOnly``.

### Resolve prerequisites

Render ``ProximityPresentationCapabilities/remediationActions`` in product
language. The host app owns permission prompts and settings navigation. After
performing an effect, report its privacy-safe result without attaching platform
objects or raw error text:

```swift
let result = await requestBluetoothPermission()
let outcome: ProximityPresentationHostActionResult = result ? .completed : .failed
_ = try await session.dispatch(
    .reportRemediation(.requestBluetoothPermission, outcome)
)
```

The SDK checks that the remediation belongs to the current prerequisite state,
re-queries platform capability after a completed effect, and only then prepares
fresh engagement material.

### Review and approve

``ProximityPresentationReview`` contains display-safe reader authentication and
trust facts, document requests, retention intent, eligible credentials,
disclosure alternatives, use-case and purpose assertions, and any recognized
application authorization. These are protocol facts, not UI-derived state.

Build ``ProximityPresentationSubmission`` only from the current review. The SDK
binds and revalidates credential, holder-key, reader-trust, status, disclosure,
and application-profile state before it sends a response. A stale or changed
selection returns a typed rejection and does not disclose data.

Reader-authentication statements remain distinct by scope, document index, and
statement index. During protected-key work,
``ProximityPresentationState/authorizingHolderKey(_:)`` carries one
``ProximityHolderAuthorizationRequest`` per approved document so a mixed
signature/MAC response cannot be collapsed into a global authorization method.

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
let configuration = ProximityPresentationConfiguration(
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
``ProximityPresentationSession/close()`` when navigation or app lifecycle ends
the journey. Closing is idempotent, and a new session always creates fresh
engagement identifiers and ephemeral key material.
