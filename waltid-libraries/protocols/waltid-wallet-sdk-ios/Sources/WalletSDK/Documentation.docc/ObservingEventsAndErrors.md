# Observing Events and Errors

Wallet operations expose native Swift progress and failure signals through
``Wallet/events`` and ``WalletError``.

## Overview

### Observe Progress

``Wallet/events`` is an `AsyncStream` of ``WalletEvent`` values. Observe it from
a task owned by the host app and cancel the task when the UI no longer needs
wallet progress.

```swift
let eventsTask = Task {
    for await event in await wallet.events {
        await updateProgress(
            phase: event.phase,
            status: event.status
        )
    }
}

// Later, when the screen or coordinator is done observing:
eventsTask.cancel()
```

Use ``WalletEventPhase`` and ``WalletEventStatus`` for high-level UI state, and
``WalletEvent/name`` when the app needs lower-level event detail.

### Handle Failures

The public facade throws ``WalletError`` for SDK failures. A cancelled Swift task can throw `CancellationError`.

```swift
do {
    let session = try await wallet.startIssuance(.init(offer: credentialOfferURL))
    await showOffer(session.offer)

} catch is CancellationError {
    throw CancellationError()
} catch let error as WalletError {
    showWalletError(error.localizedDescription)
}
```

When an invalid OpenID4VP request has an unsafe response channel, the SDK throws
`WalletError.presentationValidation(code:message:responseSafetyFailure:)`. The
protocol code and validation message describe the request failure.
`responseSafetyFailure` explains why the wallet kept the error local. Use
`localizedDescription` for display and keep the detailed fields out of UI and logs.
The response-destination safety checks still apply before any remote reporting.

Inspect ``IssuanceOutcome`` after accepting an offer. Its failed and deferred cases carry any credentials already stored and the remaining continuations. A thrown storage, validation, or key-authorization error describes the operation that threw it. A retained presentation or credential-status review that changed must be shown to the holder again. Host callbacks can throw their own Swift errors, and a cancelled task can throw `CancellationError`.

A direct-post rejection returns `PresentationResult.transmitted(.failed(...))`. If the SDK throws `WalletError.deliveryUncertain`, check the original verifier transaction before sharing again. The presentation may already have been disclosed.
