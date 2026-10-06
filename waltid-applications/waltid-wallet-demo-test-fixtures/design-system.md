# Wallet demo UI conventions

These rules apply to Compose Android/iOS/Web, the native SwiftUI demo, and wallet-owned provider content. The SDK owns issuance, selection, consent, trust and signing policy. A view renders that state; changing its host must not create a new operation.

## Components and hierarchy

- Use the existing branding/theme injection and system text sizes. Group related facts using `WalletSection`, navigation rows and label/value rows, following Settings. Separate actionable rows from facts.
- Use `WalletActions` for decisions and `WalletActionBar`/`ReviewScaffold` for persistent review actions. Keep one primary action trailing in reading order, with secondary actions before it. Keep actions on one line when they fit; wrap or stack when text needs space. Never shrink or truncate a consent label to force a row.
- Use rounded buttons with intrinsic widths. Preserve 48 dp Compose and 44 pt Apple touch targets. Copy steppers have smaller visible capsules and separate accessible increase/decrease actions, values and limits.
- Use `WalletSymbol` for Compose icons and platform symbols in SwiftUI. Decorative icons have no duplicate announcement; standalone controls need a meaningful accessible name. Keep child identifiers on children, since a SwiftUI container identifier can replace them.
- Use the same credential identity, art and typed values in every context. Review thumbnails center the logo on the supplied background; the title sits alongside. Follow the [credential-information contract](credential-information.md) for metadata, disclosure scope and unavailable values.
- Offer rows move their Receive switch below the identity when the title needs more horizontal space. Keep the logo beside the title, retain copy counts during resizing and preserve separate selection/information actions.
- Make compact reviews answer who, what information and what action. Keep required payment fields in their authoritative positions. Technical details are reachable but do not displace human-readable information.

## Screen chrome, feedback and motion

Each active destination has one header owner. Compose uses `WalletScreenHeader`; native screens use their navigation title/toolbar. Content owns section labels and credential identity. Information reviews have one `WalletDetailSheet` navigation host: Requested → All → Technical pushes within it. Back preserves the preceding page; Close returns to the unchanged review and never grants consent. Native pages receive the presentation's dismissal action through the host environment.

Use `MetadataDisclosure` for expandable fact groups. Its full row is a single named button with heading and expanded/collapsed semantics, a semibold body label and a rotating directional chevron. Keep 48 dp / 44 pt targets, wrapping labels and compact content spacing. Expansion state belongs to its page or request revision; a replacement payment request starts collapsed.

`WalletFooter` owns the background and padding for feedback/actions. Transient success and ongoing operation feedback belongs immediately above actions or bottom navigation. Keep input errors, request failures, trust warnings, transaction terms, consent and receipts in context. Never place a global status over an embedded proximity flow. Secondary actions are text; the trailing primary action is filled. A footer with only Cancel or Close uses a trailing text action. A numeric keyboard's Done action shares this row; text fields use their keyboard submit key. Avoid an independent keyboard toolbar overlapping pinned controls.

Compose `ReviewScaffold` measures header and footer before the body, so its interactive scroll viewport ends above the actual footer. The captured source can draw behind the material without extending hit testing or accessibility scrolling there. Native hosts use a single `safeAreaInset` for the footer. The final item must scroll fully clear of feedback, wrapped actions and the keyboard; do not substitute a fixed bottom spacer.

| Renderer | Footer treatment and accessibility fallback |
|---|---|
| Compose Android | API 30–32 uses the opaque surface after the Android 12 physical release comparison. API 33+ uses Haze core + blur in its default Balanced mode, one screen-owned source, 18 dp uniform blur and background tint. Increased contrast or unavailable rendering uses the opaque background. No experimental window-backdrop or optical-refraction API. |
| Compose iOS / Wasm | The same bounded Haze blur/tint; available reduced-transparency and contrast preferences use the opaque background. Clear unavailable sources rather than retaining credential pixels. |
| Native SwiftUI | Regular system material; Reduce Transparency or increased contrast uses the opaque grouped background. Native navigation owns presentation/back transitions. |

Use existing navigation/animation APIs. Hierarchical Compose navigation uses a short directional slide/fade with matching Back and RTL direction; card/detail transitions follow selected identity, without delayed callbacks. Disclosure height and chevron animate together. Reduce Motion removes custom spatial movement; removed/hidden cards cannot remain accessible or actionable. Animations never submit, retry or authorize a wallet operation. Test rapid reversal, interruption and disposal, and inspect short real-renderer clips separately from settled screenshots.

## Navigation and state

Wallet home exposes Scan or paste, Share nearby and Settings. The scanner resolves supported links automatically; users do not choose a protocol. Unknown content remains editable. Resolving a link never submits it. A FIDO hybrid code receives an honest platform-camera instruction, not a pretend wallet flow.

In-app requests use full-screen flows; supported external entry uses a sheet. Share the review state/content and pin actions outside the content scroll area. Preserve selection and transaction-code edits during detail navigation and host recreation. Busy operations prevent duplicate submission. A new incoming link cannot replace an operation already consuming an offer or submitting consent. See [external flows](visual/external-flows.md).

PIN onboarding uses separate Choose and Confirm screens, with four large circular masked positions backed by one secure input. A matching confirmation opens the available OS biometric prompt; its outcome controls opt-in without an app toggle. Cancellation completes PIN-only setup. Follow the [PIN interaction contract](pin-onboarding.md). Signing-key setup starts with supported defaults and one Create/Restore confirmation; individual rows open focused customization. Settings reuses the same summary, with immutable properties read-only and existing reset consequences explicit. App unlock never substitutes for signing approval.

## Structure and evidence workflow

Prefer one public rendering entry per file, with small private helpers beside it. Split orchestration, reusable content and platform hosting when they have different responsibilities. Extract stable concepts, not identical-looking fragments with different semantics. Reuse the Kotlin shared UI and the existing shared Swift package instead of adding a new public SDK UI layer.

For each changed route:

1. Check this contract and the [payment](visual/payment-consent.md), [nearby](visual/nearby.md) or credential contract that governs it.
2. Choose existing shared synthetic fixture data; add a distinct state only when it exposes a different risk. Use the same renderable content for previews and tests, without starting services.
3. Add semantic assertions and the applicable renderer cells to the [catalogue](visual/catalogue.json). Cover compact width, large text, dark mode or RTL where the change affects layout. Use real host tests for keyboard, lifecycle and system UI.
4. Deliberately record, inspect, then run [the comparison runner](visual/README.md) with recording disabled. A record-mode run is not a pass. Never accept new baselines automatically in CI.
5. Keep local screenshots, hosted checks, live issuer/verifier interactions and physical-device proof separate. A component image cannot prove a system picker, biometric prompt, BLE/NFC exchange or TS-12 compliance.

The selected patterns adapt grouped Settings rows, digital-wallet disclosure/detail hierarchy and compact payment-sheet actions. Borrow hierarchy and interaction ideas while preserving the wallet's metadata and consent contract; do not copy third-party branding, assets or unrelated behavior.

The repository runner and these contracts are the canonical workflow. Existing KMP validation, Apple tooling and mobile capture skills remain sufficient; a separate installed wallet skill should link here rather than duplicate commands or policy. Global skill installation is not required to maintain these screens.
