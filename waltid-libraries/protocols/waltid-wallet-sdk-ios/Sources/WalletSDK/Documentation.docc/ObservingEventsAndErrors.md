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

The public API throws ``WalletError`` so Swift consumers can handle SDK failures
without depending on generated Kotlin exception types.

```swift
do {
    _ = try await wallet.receive(offer: credentialOfferURL)
} catch let error as WalletError {
    showWalletError(error.localizedDescription)
} catch {
    showWalletError(error.localizedDescription)
}
```

> Important: The current error surface is intentionally small. More structured
> typed failures can be added without exposing Kotlin bridge internals to iOS
> app code.
