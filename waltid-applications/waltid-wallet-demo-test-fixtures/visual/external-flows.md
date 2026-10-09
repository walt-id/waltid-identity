# Wallet task sheets

Recognized credential offers and presentation requests open the wallet normally and enter one task sheet over Home. Unlock and signing-key setup remain at the app root; the pending request prepares once the wallet is ready. Scanner/manual resolution advances within the same sheet. Reviewing never accepts an offer or sends credentials. Long reviews expand and scroll; short entry menus use heights measured before presentation.

## Ownership and navigation

- Reopening the same URL preserves its review, choices and detail navigation, including resolved HTTPS links.
- Another request cannot replace a committing exchange, a pending presentation continuation or an active nearby session.
- Close cancels pending preview work and returns to Home. Ordinary Android request closure keeps the opaque wallet Activity open.
- Clean successful wallet tasks retain the existing eligible idle timeout. Deferred, partial and uncertain outcomes require explicit recovery; provider issuance retains explicit Done and result delivery.
- Credential information stays within the receiving task. Back preserves its choices; Close ends the local task. Sharing displays all requested values inline.
- Busy operations prevent dismissal and duplicate submission. Local Close remains distinct from protocol Reject/Decline and provider selector navigation.
- An orphan authorization callback explains unavailable recovery without replaying issuance or labeling previously stored credentials as newly received.
- A request arriving in Settings dismisses Settings before showing its task. Authentication never mounts a second app inside a sheet.

## Platform surfaces

Compose Android, Compose iOS and browser consumers share the modal request host and content. Android MainActivity is opaque and retains singleTop delivery; no singleTask/CLEAR_TOP policy is introduced that could destroy provider activities. The two Credential Manager fulfillment activities remain translucent and retain caller context, result routing and their dedicated authorization callback.

Native SwiftUI keeps Home mounted beneath one scanner/online task presentation. Its measured entry detent stays stable; review expands that presentation rather than dismissing and opening another host. Nearby sharing retains its own SDK cleanup and dismissal owner. Settings, image viewers and platform-owned prompts retain their existing containers.

Apple identity-document provider UI remains in its system-owned extension and uses the shared review content directly. It is not transferred to the main wallet. The DC API additional-review preference is retained; it is unrelated to sheet sizing.

## Evidence

`external.receiving.review` and `external.callback.unavailable` in the visual catalogue render production content with synthetic inputs. Compose captures the modal window; native snapshots capture bounded content and chrome. Native XCTest separately exercises real scene URLs, PIN entry, sheet presentation and Safari warm handoff with the mock wallet. Android instrumentation exercises cold entry, PIN, Activity recreation, close-to-Home, expired callbacks and live issuer receipt retention.

The controller/model tests cover preparation gating, idempotent entry, late-preview cancellation, commit guards and nearby ownership. The optional Android `authorizationCodeRetainsCopiesThroughBrowserReturn` integration case additionally exercises the real Credential Manager create picker, unattended fixture IdP and Chrome authorization callback, asserting the same provider Activity and two stored copies. It requires the coordinated fixture and an emulator build without signing prompts. A dedicated `walt-wallet-create://authorize` callback prevents Chrome’s `CLEAR_TOP` from clearing the provider request. Unmatched callbacks close without opening the wallet; process-loss callbacks are correlated before being queued for wallet-side recovery. These lanes do not establish physical-device biometric, BLE/NFC, Chrome as the DC API caller, live process-loss recovery or formal TS-12 conformance. Record-only screenshot runs are not verification.
