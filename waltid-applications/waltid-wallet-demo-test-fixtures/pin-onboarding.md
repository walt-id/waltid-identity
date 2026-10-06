# PIN onboarding

[WAL-1440](https://linear.app/walt-new/issue/WAL-1440/follow-up-on-wal-749) governs this flow: two screens and OS-driven biometric opt-in. Choose, Confirm and Unlock use exactly four ASCII digits on Compose Android, Compose iOS and native SwiftUI. This is app unlock; signing approval remains a separate SDK-controlled capability.

## Interaction

1. **Choose a PIN — Step 1 of 2:** one secure input with four underline placeholders. Continue becomes available after four digits. The PIN is still uncommitted.
2. **Confirm your PIN — Step 2 of 2:** a fresh secure input with the same four positions. Back returns to Choose, retains the chosen PIN and discards confirmation. A mismatch clears confirmation and shows an error for retry. No PIN is saved and no biometric prompt opens on mismatch.
3. Entering a matching fourth digit saves the PIN and automatically offers the platform's biometric authentication when available. Success enables biometric unlock. Cancellation or failure presents an explicit choice: **Try again** or **Use PIN only**. Unavailability explains the limitation and offers PIN-only continuation. Cancellation never implies refusal. Unresolved setup survives a restart and resumes only after verifying the saved PIN. While authentication is pending, editing and actions are disabled; cancellation, lock or reset invalidates late results. A save failure stays on confirmation with a retry action.
4. Continue to the existing signing-key summary. App unlock never substitutes for signing consent.

Both renderers use the real numeric keyboard and a **single** secure editing control. Separate visual positions are decorative, so screen readers encounter one labelled password input. Entered digits are always masked, including during paste. The component accepts editing, deletion and paste, filters ASCII digits, and caps input at four characters. The current empty underline has an accent color; errors mark all positions and include readable text. Actions stay outside the scroll region and above the keyboard; native numeric input has an accessible Done accessory. Choose and Confirm use directional transitions with a reduced-motion alternative.

Choose and Confirm automatically focus the secure input and show the numeric keyboard, including after Back or a failed save. Unlock gives configured, available biometrics the first attempt with the keyboard hidden. Cancellation or failure then focuses the PIN automatically, without repeatedly reopening the biometric prompt. When biometrics are unavailable or disabled, Unlock shows the keyboard immediately. An explicit wallet lock begins a fresh unlock attempt; foreground notifications cannot duplicate its biometric prompt. Manual keyboard dismissal stays effective until the step changes or authentication completes.

The same fixed four-digit validation applies to creation and unlock. There is no variable-length compatibility path or PIN migration. The stored verifier format and PIN derivation are unchanged.

## UI references and decision

Inspected on 5 October 2026; adapt interaction and hierarchy, without copying source or assets:

| Pattern | Example and assessment |
| --- | --- |
| Hollow/filled circles | [NL Wallet's six-digit screen and golden image](https://github.com/MinBZK/nl-wallet/blob/main/wallet_app/test/src/feature/change_pin/goldens/change_pin/select_new_pin.light.png). Useful progress pattern. Earlier enlarged circles were superseded by the user's 6 October request for quieter underlines. |
| Separate masked cells | [EUDI's secure PIN component](https://github.com/eu-digital-identity-wallet/eudi-app-android-wallet-ui/blob/main/ui-logic/src/main/java/eu/europa/ec/uilogic/component/wrap/WrapSecurePinTextField.kt) and [PIN screen](https://github.com/eu-digital-identity-wallet/eudi-app-android-wallet-ui/blob/main/common-feature/src/main/java/eu/europa/ec/commonfeature/ui/pin/PinScreen.kt). Useful single-editor and completion patterns. Rectangular outlines add too much form chrome for the requested design. |
| Underlined digit positions | Selected for this refinement: four generous positions, masked dots, active underline and explicit error feedback. This keeps length visible with less visual weight. |
| Custom keypad | NL Wallet also shows a custom numeric keypad. Retain each platform's keyboard for this change, preserving normal editing, accessibility and keyboard-inset behavior without creating another input subsystem. |

## Evidence

The deterministic catalogue covers Choose, Confirm, mismatch, pending OS authentication, 320×568 dark confirmation with large text, and unlock on all three mobile renderers. The pending screenshot shows **only the app**, not fabricated system UI. Model tests prove fixed-length validation, two-stage gating, editing invalidation, persistence retry, OS success/decline/unavailability, duplicate submission prevention and stale-result rejection. UI journeys prove secure semantics, separate inputs, Back, mismatch correction and keyboard-reachable actions. The native secure editor retains its focus request until its own window is key and its scene is active. UIKit owns actual first-responder changes, including manual dismissal and refocusing. Physical biometric sensor/permission interaction remains a separate evidence lane.
