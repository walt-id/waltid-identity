# External wallet reviews

A recognized credential offer or presentation request opens a wallet-owned review sheet. Unlock and signing setup, when needed, stay in that sheet; the request is prepared once the wallet is ready. Reviewing never accepts an offer or sends credentials. Links opened with the in-app scanner remain full-screen journeys.

## Ownership and navigation

- Reopening the same external URL preserves its review, choices and detail presentation.
- Another request cannot replace a credential exchange that is already committing, a pending presentation continuation or an active nearby session. A notice asks the user to finish the operation.
- Closing a review cancels its pending preview and returns to the wallet. Android finishes the Activity when it was launched for this external request, returning to its caller. A warm request in the existing wallet returns to the wallet home.
- A receive receipt remains visible until Done; successful issuance does not silently remove the external review.
- Credential information has its own Close action. Closing it must leave the enclosing review and choices intact.
- Dismissal is blocked during authentication, identity changes and consuming operations. Explicit protocol decline remains separate from closing an unsubmitted review.
- A callback with no recoverable authorization session explains that the session is unavailable. It does not replay issuance or label credentials already in storage as the result of that callback. Existing recoverable-session support is used where the wallet backend supplies it; mobile process-loss recovery is not asserted by this UI change.

## Platform surfaces

Compose uses the same receive/share content in full-screen and modal hosts. Android's translucent Activity permits a caller to remain visible when that caller is beneath the wallet in the same task. A task-root launch or warm existing wallet uses its own neutral background. The test caller is a separate debug-only, non-exported Activity in the preview package; it proves Activity stacking and recreation, not every browser's task flags or an OS-owned credential picker.

Native SwiftUI uses an app-owned sheet above a neutral wallet background. Unlock, setup and accessibility text use a large detent; ordinary review supports medium and large detents. The switch after unlock waits for keyboard dismissal. This does not promise transparency through an iOS app window to another app. Apple credential-provider UI remains a separate system integration.

Compose iOS uses the shared sheet content over its own neutral app background. External deep-link ownership and platform-provider ownership remain separate.

## Evidence

`external.receiving.review` and `external.callback.unavailable` in the visual catalogue render production content with synthetic inputs. Compose captures the modal window; native snapshots capture bounded content and chrome. Native XCTest separately exercises real scene URLs, PIN entry, sheet presentation and Safari warm handoff with the mock wallet. Android instrumentation exercises cold entry, PIN, Activity recreation, close-to-caller, expired callbacks and live issuer receipt retention.

The controller/model tests cover preparation gating, idempotent entry, late-preview cancellation, commit guards and nearby ownership. The optional Android `authorizationCodeRetainsCopiesThroughBrowserReturn` integration case additionally exercises the real Credential Manager create picker, unattended fixture IdP and Chrome authorization callback, asserting the same provider Activity and two stored copies. It requires the coordinated fixture and an emulator build without signing prompts. A dedicated `walt-wallet-create://authorize` callback prevents Chrome’s `CLEAR_TOP` from clearing the provider request. Unmatched callbacks close without opening the wallet; process-loss callbacks are correlated before being queued for wallet-side recovery. These lanes do not establish physical-device biometric, BLE/NFC, Chrome as the DC API caller, live process-loss recovery or formal TS-12 conformance. Record-only screenshot runs are not verification.
