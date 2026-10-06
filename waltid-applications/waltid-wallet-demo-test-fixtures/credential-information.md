# Credential information contract

Compose Android, Compose iOS and Web use the shared Kotlin normalizer. The native app and both Apple provider extensions use `WalletDemoSharingUI`. Keep these two render adapters consistent with `resources/files/credential-information.json`; protocol selection, required disclosures, payment consent and trust decisions remain SDK-owned.

## Identity and metadata

Claim IDs preserve namespaces, punctuation and array positions. Group IDs are stable semantic keys, independent of translated headings. Never use a label to join claims or to retain selection. Requested disclosure controls keep their original SDK index/path even when issuer metadata changes display order.

The issuance adapter provides `credentialClaims` alongside the existing `issuerDisplay` and `credentialDisplay` sidecar metadata. The same ordered, locale-preserving metadata accompanies batch previews, immediate storage and deferred continuation. It contains definitions, not issued values. The narrow SDK addition preserves information already available during issuance; UI code does not fetch another metadata source. The released Kotlin offer-preview model is unchanged; the additional map belongs to the batch session introduced by the batch issuance work.

Use the existing ordered locale selector, then authoritative claim labels, defined format vocabulary and a readable path fallback. Metadata changes labels/order only. It cannot change a value, required status, optional selection, response protection or trust assertion. Duplicate or malformed stored metadata falls back without crashing; this tolerant rendering does not replace protocol validation. The current SDK exposes string-only claim paths. Array-index and wildcard metadata support is not claimed; never erase an index to force a match with a property path.

## Context and navigation

Stored details show all readable data. Technical fields and local storage facts have a labelled destination. Sharing details start with the requested data and existing required/optional controls. “All credential information” is a separate, read-only destination, explicitly including information outside the request. Closing either destination preserves consent choices; viewing extra data never selects it for sharing.

An offer can show issuer-provided claim definitions and mandatory/optional facts. Values must be described as not yet received. Completion and stored details use actual returned credential IDs and values. A successful local send does not establish the verifier's result.

Offer rows reuse credential thumbnails and provide separate inclusion, copy-count and information controls. Opening or closing information does not change selection. Unselected rows remain inspectable. Definitions use issuer order and labels with the same vocabulary fallback as stored details; the UI never inserts sample values. The shared batch fixture covers two selected types/three copies, no selection, and definitions. Compact dark fixtures exercise scrolling and retained actions at large text sizes.

Review thumbnails use the credential logo centered on the supplied background color, ignoring credential artwork when that color is available. Without a usable background color, use available credential artwork; when artwork is missing or fails to load, use the supplied logo on the default background. A missing logo stays absent. The title is rendered beside the thumbnail, never overlaid inside it. Supplied artwork pixels remain intact, including embedded lettering. A single offered configuration uses larger full artwork; multiple offered configurations and sharing use compact thumbnails. At large text sizes the thumbnail becomes smaller and the title wraps beside it; the user's chosen text size is preserved.

Details push within their current screen or sheet with one navigation header. Back restores the offer or sharing choices. Requested data, all readable data and technical details remain separate destinations; no live acceptance or sharing actions remain behind a detail page.

Stored data, requested disclosures and offered definitions share `CredentialDataRow`: a small secondary label above the full-sized value/status, grouped spacing and dividers. Optional disclosure controls sit beside the same row and retain their original selection identities. Context changes the information and available controls, not the label/value hierarchy. Technical summaries remain clearly separate.

## Values and media

Keep null, empty text, false, zero, empty objects and empty lists distinct. Collections initially show 25 items, their total and an explicit next-page action. Preserve indices when revealing more items. Retain deferred, bounded image decoding; loading and failed images have visible placeholders. Full-screen image inspection is available after successful decoding. Credential artwork identifies the credential and stays separate from claim images and issuer/verifier logos.

## Evidence

`CredentialInformationContractTest` and native `CredentialDisplayNormalizerTests` consume the same locale/namespace fixture. The core batch metadata test covers immediate copies and a persisted deferred continuation, and asserts that previewing does not redeem the offer. The shared sharing-review behavior test opens all credential information and proves that only the user's original optional selection is submitted. Visual evidence complements these checks; see [the visual catalogue](visual/README.md).
