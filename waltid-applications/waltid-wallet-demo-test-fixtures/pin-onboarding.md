# PIN onboarding

[WAL-1440](https://linear.app/walt-new/issue/WAL-1440/follow-up-on-wal-749) governs this flow: two screens and OS-driven biometric opt-in. Choose, Confirm and Unlock use exactly four ASCII digits on Compose Android, Compose iOS and native SwiftUI. This is app unlock; signing approval remains a separate SDK-controlled capability.

## Interaction

1. **Choose a PIN — Step 1 of 2:** one secure input with four underline placeholders. The fourth digit advances automatically. The PIN is still uncommitted.
2. **Confirm your PIN — Step 2 of 2:** the same secure editor is cleared for confirmation. Back returns to a blank Choose screen and discards both drafts. A mismatch clears confirmation and shows an error for retry. No PIN is saved and no biometric prompt opens on mismatch.
3. Entering a matching fourth digit saves the PIN and automatically offers the platform's biometric authentication when available. Success enables biometric unlock. Cancellation or failure presents an explicit choice: **Try again** or **Use PIN only**. Unavailability explains its cause and offers PIN-only continuation plus Open Settings where recovery is possible. Cancellation never implies refusal. Unresolved setup survives a restart and resumes only after verifying the saved PIN. While authentication is pending, editing and actions are disabled; cancellation, lock or reset invalidates late results. A save failure stays on confirmation with a retry action.
4. Continue to the existing signing-key summary. App unlock never substitutes for signing consent.

Both renderers use the real numeric keyboard and a **single** secure editing control. Separate visual positions are decorative, so screen readers encounter one labelled password input. Entered digits are always masked, including during paste. The component accepts editing, deletion and paste, filters ASCII digits, and caps input at four characters. The current empty underline has an accent color; errors mark all positions and include readable text. Feedback and actions reserve stable space above the keyboard; compact layouts scroll so all controls remain reachable; native numeric input has an accessible Done accessory. Choose and Confirm use directional transitions with a reduced-motion alternative.

Choose and Confirm automatically focus the secure input and show the numeric keyboard, including after Back and immediately after a rejected PIN. Short checks and saves retain the same keyboard session. Unlock gives configured, available biometrics the first attempt with the keyboard hidden. Cancellation or failure then focuses the PIN automatically, without repeatedly reopening the biometric prompt. When biometrics are unavailable or disabled, Unlock shows the keyboard immediately. A wrong PIN clears immediately; Clear also discards a partial attempt. A visible biometric retry action requests another native prompt. An explicit wallet lock begins a fresh unlock attempt; foreground notifications cannot duplicate its biometric prompt. Manual keyboard dismissal stays effective until the step changes or authentication completes.

The same fixed four-digit validation applies to creation and unlock. There is no variable-length compatibility path or PIN migration. The stored verifier format and PIN derivation are unchanged.

## Wallet access settings

Settings → Wallet access provides Change PIN and the wallet's biometric-unlock preference. Change PIN verifies the current PIN, then reuses Choose and Confirm. Each complete entry advances automatically. The existing verifier remains valid until matching confirmation commits its replacement. Cancelling, a wrong current PIN, or a failed save does not change that verifier. PIN replacement does not bootstrap the wallet or modify signing keys, DIDs, credentials or signing approval. Biometrics are enabled only after OS authentication succeeds; cancelling preserves the previous preference. Turning them off retains PIN access. Temporary OS unavailability never clears the saved preference.

PIN and biometric attempts belong to one demo-owned access coordinator per rendering family. Cancellation/lock/reset invalidates late callbacks. Operational storage errors have an explicit retry action; wrong PINs stay in the normal entry flow. Only accepted input events trigger automatic completion, so restoration or Back cannot resubmit a complete draft.

## Biometric permission and signing recovery

Face ID access is an app-wide OS permission, shared by unlock and biometric signing. After denial, retrying authentication cannot reopen its permission dialog. Offer Apple's supported [app Settings link](https://developer.apple.com/documentation/uikit/uiapplication/opensettingsurlstring); Android opens biometric enrollment with a Security settings fallback. Recheck both unlock and strong signing availability on return. OS availability is distinct from the saved preferences: recovery never enables unlock without successful authentication, and choosing Use PIN only never changes signing approval.

Enrollment, missing device passcode, lockout, unavailable access and unsupported hardware have separate recovery guidance. Do not label every unavailable result as permission denial. A lockout requires the **device** passcode; the four-digit **wallet** PIN cannot authorize a protected signing key.

The signing summary retains its intended approval even when the SDK filters that option out. Changing storage/recovery, refreshing options or restoring the Compose screen cannot silently select No biometric signing. Create/Restore stays disabled until an SDK option matches the intended approval, or the user explicitly changes approval where allowed. Required signing offers no unprotected alternative. An unavailable existing key stays in place; the demo never resets or replaces it automatically.

## UI references and decision

Inspected on 5 October 2026; adapt interaction and hierarchy, without copying source or assets:

| Pattern | Example and assessment |
| --- | --- |
| Hollow/filled circles | [NL Wallet's six-digit screen and golden image](https://github.com/MinBZK/nl-wallet/blob/main/wallet_app/test/src/feature/change_pin/goldens/change_pin/select_new_pin.light.png). Useful progress pattern. Earlier enlarged circles were superseded by the user's 6 October request for quieter underlines. |
| Separate masked cells | [EUDI's secure PIN component](https://github.com/eu-digital-identity-wallet/eudi-app-android-wallet-ui/blob/main/ui-logic/src/main/java/eu/europa/ec/uilogic/component/wrap/WrapSecurePinTextField.kt) and [PIN screen](https://github.com/eu-digital-identity-wallet/eudi-app-android-wallet-ui/blob/main/common-feature/src/main/java/eu/europa/ec/commonfeature/ui/pin/PinScreen.kt). Useful single-editor and completion patterns. Rectangular outlines add too much form chrome for the requested design. |
| Underlined digit positions | Selected for this refinement: four generous positions, masked dots, active underline and explicit error feedback. This keeps length visible with less visual weight. |
| Custom keypad | NL Wallet also shows a custom numeric keypad. Retain each platform's keyboard for this change, preserving normal editing, accessibility and keyboard-inset behavior without creating another input subsystem. |

## Evidence

The deterministic catalogue covers Choose, Confirm, mismatch, pending OS authentication, 320×568 dark confirmation with large text, and unlock on all three mobile renderers. The pending screenshot shows **only the app**, not fabricated system UI. Model tests prove fixed-length validation, two-stage gating, editing invalidation, persistence retry, OS success/decline/unavailability, duplicate submission prevention and stale-result rejection. UI journeys prove secure semantics, separate inputs, Back, mismatch correction and keyboard-reachable actions. The native secure editor retains its focus request until its own window is key and its scene is active. UIKit owns actual first-responder changes, including manual dismissal and refocusing. The catalogue also captures configured Face ID unavailability and blocked signing approval across all three renderers. Permission-recovery regressions verify independent unlock/signing preferences, explicit approval changes and refreshed SDK handles. Physical biometric sensor/permission interaction remains a separate evidence lane.
