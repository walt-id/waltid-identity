# Payment review contract

Both payment paths use one display model and renderer per platform. Issuer-instructed payments project the wallet SDK's immutable, localized consent into that model. Legacy mdoc payments project their existing normalized transaction fields, emphasizing amount, currency and payee. Both use the same field hierarchy, details controls and payment actions. Consent loading or failure never falls back to legacy transaction values.

The renderers do not fetch payment metadata, validate signatures, choose a transaction schema, invent issuer instructions, or reconstruct transaction values. These responsibilities remain in the wallet core. This document maps the issuer-instructed UI to [TS12 v1.0.1, revision 365f734a](https://github.com/eu-digital-identity-wallet/eudi-doc-standards-and-technical-specifications/blob/365f734ac95b858343b09aaf56a80f3e1d684119/docs/technical-specifications/ts12-electronic-payments-SCA-implementation-with-wallet.md); the shared layout does not establish conformance for either path.

| Requirement | UI behavior and evidence |
| --- | --- |
| §3.1: unsigned request | Show the warning in the payment review. The prescribed affirmative action opens a separate explicit confirmation. Back/dismiss performs no submission. Compose sharing tests and native `MockWalletUITests.testUnsignedPaymentShowsLocalizedFieldsAndRequiresSeparateConfirmation` exercise this journey. Read-only SDK preparation precedes the warning; the UI never overrides a rejected signature or policy. |
| §3.2: validated transaction | Loading and blocked consent disable confirmation. Missing issuer labels cannot fall back to a generic transaction card. `payment.loading` and `payment.blocked` cover the renderers; controller/view-model tests cover preparation failure and late results. |
| §3.3.1: requested attributes | `payment.mixed_credentials.*` includes the payment attestation and an ordinary identity credential. Requested information remains inspectable independently of transaction details; opening additional stored information does not change disclosure selection. |
| §3.3.1: levels 1–4 | Prominent and main fields remain in the main review's scroll area. Details fields have an explicit expansion control; omitted fields are intentionally absent. The UI uses the SDK's placement, including its default-level resolution. Compose placement assertions and the native localized interaction check exercise values and omission. |
| §§3.3.1–3.3.2: localized labels | Issuer labels, descriptions and exact string values are retained. The demo caption policy below omits two redundant visible labels. The common fixture includes German labels and a long affirmative action at compact width/large text. Missing required metadata labels is a blocked state. |
| §3.3.1: rulebook display modes | The SDK retains textual metadata and does not grant rulebook permissions. The demos apply only the caption policy below; general rulebook-driven display modes need an explicit SDK contract and accessible representation. |
| §3.3.3: actions/title/hint | The SDK's affirmative action is used for both review and unsigned confirmation. Optional denial/title use wallet strings when absent. An absent security hint stays absent. Length/schema validation remains in the SDK. `payment.localized.compact_large_text` exercises long actions, absent title/denial and a supplied hint. |
| §4.1: metadata authority | Issuer-instructed payment fields, placement and action wording come only from the SDK consent model. Localized application resources contain wallet wording, never fallback issuer instructions. SDK integrity/provenance tests remain necessary; screenshots cannot establish authenticity. |
| Current revision binding | Selection changes discard outstanding consent preparation. A rendered action must still match the current request, selections and resolved payment state; an old action cannot authorize a newer review. Changing consent or disabling the action dismisses an outstanding unsigned confirmation. Kotlin controller/Compose tests and native presentation-model tests cover these transitions. The SDK remains the final authority for request/transaction/key/revision binding. |

`payment.sheet.*` captures the actual Compose modal host. Native system-alert interactions are retained as XCTest attachments; SwiftUI component captures do not reproduce Apple's identity-document provider geometry. The same review/actions are reused by supported wallet-owned hosts.

Android's additional-review preference remains intact. With wallet preview disabled, Credential Manager owns the confirmation UI; these wallet screenshots do not establish its TS-12 coverage. That platform limitation remains tracked by WAL-1513. Live signing, system sheets, physical hardware and formal conformance require separate evidence.

`payment.scheduled.*` and `payment.recurring.*` use complete, synthetic consent fields from the
shared fixture. Every renderer uses the full sharing review, including the verifier and requested credentials.
Main states start at the top; detail states show a scrolled continuation. They cover timing, frequency, MIT terms, ordinary optional
fields, a valid embedded logo, clickable HTTPS website and localized Boolean values. The recurring compact/large-text state covers scrolling while the
actions remain pinned. Compose tests also expand details, reach every field and cancel without
submission; native disclosure interaction retains its separate app-host test. These fixtures do
not replace the shared validator, issuer-metadata or end-to-end proof tests.

Payee identity and its details share a card. Amount and the ISO currency code share a row
under the amount label, including at large text sizes. They wrap only when their actual
content cannot fit the available width, without truncating either value. Timing
and recurring monetary terms use separate neutral cards, preserving issuer placement.
Level-1/2 terms remain visible in the main review; only supplementary fields start collapsed.
Payment details group the PISP separately.

Logos are optional display assets: a valid bounded PNG/JPEG is shown without a visible
caption, retaining its issuer label for accessibility. Missing or failed images leave
no placeholder. Remote loading requires an explicit action and uses no redirects,
cookies or credentials. Only HTTPS websites are actionable;
other URI schemes remain text. Branding and links do not imply an authenticated payee.
`MockWalletUITests.testRecurringPaymentDetailsScrollAndCancelWithoutSubmitting` exercises
native expansion, long exact values, scrolling, hidden invalid logos and cancellation.

The demo omits the redundant visible currency label when it is paired with the amount,
and the logo caption. Metadata and translation checks still require both issuer labels;
all descriptions and values retain their issuer-defined placement. TS12 §3.3.1 permits
label omission when an applicable SCA Attestation Rulebook allows it for self-explanatory
values. This demo presentation policy does not establish that permission for a production
rulebook or imply full TS12 Wallet Unit conformance.
