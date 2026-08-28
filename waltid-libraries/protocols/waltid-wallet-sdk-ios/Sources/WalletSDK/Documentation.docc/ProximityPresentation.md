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

### Lifecycle

Only one proximity session can be active per wallet. Cancellation is available
in every non-terminal state where the SDK reports it as legal. Call
``ProximityPresentationSession/close()`` when navigation or app lifecycle ends
the journey. Closing is idempotent, and a new session always creates fresh
engagement identifiers and ephemeral key material.
