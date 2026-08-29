# ADR 0002: NFC building-block selection

- Status: accepted for implementation; authoritative NFCv2 reconciliation and physical qualification pending
- Ticket: [WAL-1347](https://linear.app/walt-new/issue/WAL-1347/implement-nfc-engagement-and-device-retrieval)
- Architecture contract: [waltid-architecture PR #60](https://github.com/walt-id/waltid-architecture/pull/60)
- Date: 2026-08-29

## Context

The holder must support conventional NFC Static and Negotiated Handover, NFC device retrieval,
and the newer NFCv2 engagement with NFC-only or NFC-plus-negotiated-bearer hybrid retrieval. Exact Handover Select and
Handover Request bytes, bounded peer input, ISO 7816 APDU state, engagement/retrieval races,
session-message sequencing, and cleanup must remain walt-owned common behavior. Platform code is
limited to APDU routing and contactless-session lifecycle events.

The normative implementation baseline is the authorized `ISO/IEC DIS 18013-5:2025(E)` retained in
the architecture repository, SHA-256
`6bf2cdfbc89ed992d4822d6f4f2ee30bdc1443bd8db35c5ab1b562c3811af7f4`. The complete applicable
ISO/IEC 7816-4 and NFC Forum Connection Handover, NDEF, Type 4 Tag, and TNEP texts are not yet held
locally, so their final clause-level reconciliation remains a qualification gate.

The DIS also retains unresolved placeholders for a SessionEstablishment-during-Negotiated-Handover
TNEP service. The later NFCv2 implementation described by Multipaz PR #1726 follows WG10 Action
Point 112.05 and uses a dedicated AID, CBOR handover, and the same APDU channel instead. Both paths
address obtaining a reader request before retrieval setup and consent, but the available sources do
not prove their formal relationship.

## Decision

Use walt-owned common protocol and state code over narrow Android HCE and Apple CardSession
adapters. Do not add an NFC runtime dependency and do not vendor an upstream implementation.

Use the following pinned sources as behavioral and test-design oracles only:

| Source | Exact input | Useful boundary | Decision |
| --- | --- | --- | --- |
| [OWF Multipaz](https://github.com/openwallet-foundation/multipaz) | release `0.100.0`, commit [`7c0988be`](https://github.com/openwallet-foundation/multipaz/commit/7c0988bee3384d13a0732e0c33336ae0faf3b863), Apache-2.0 | NFCv2 AID, CBOR handover, NFC-only and NFC-plus-negotiated-bearer hybrid transport, emitted `seq`, conventional NDEF/APDU behavior, Android service lifecycle, and deterministic vectors | Rejected as a dependency or source import. The useful code is coupled to Multipaz models, CBOR, crypto, I/O, coroutine, logging, document, and platform layers. Exact files remain a provisional NFCv2 compatibility oracle and an independent conventional-NFC oracle. |
| [Axle](https://github.com/openwallet-foundation-labs/axle) | commit [`f8b0697a`](https://github.com/openwallet-foundation-labs/axle/commit/f8b0697abec4260bb2a7b872108e6b1d4f2edebe), Apache-2.0 | Focused conventional NDEF, TNEP, Connection Handover, Type 4 Tag, Android HCE, and Kotlin/Swift parity behavior and tests | Rejected as a dependency or source import. Its focused implementation is useful for differential review, but it neither supplies the full required NFCv2/session contract nor removes the walt common state machine and platform boundary. |

“Oracle” means an implementation used for differential review and test design. It does not mean
selected source or copied code. No upstream implementation file is copied or adapted by this
decision, so no source-derived attribution notice is required beyond these references.

The provisional session-message profile follows the exact behavior verified in the pinned source:
outgoing `seq` values are direction-local, start at zero, and increment per message. The pinned
receiver does not validate inbound presence or ordering, so walt accepts missing, duplicate,
replayed, and out-of-order values after structural CBOR/type validation rather than inventing a
rejection rule. Conventional sessions reject any `seq` field. Authoritative AP 112.05
reconciliation may replace this provisional receive policy.

### Pinned file audit

The implementation decision was made against these immutable Git blob identities. Blob hashes make
the audit reproducible even if paths later move inside the pinned repositories.

Multipaz NFCv2 compatibility surface:

- `multipaz/src/commonMain/kotlin/org/multipaz/mdoc/nfc/MdocNfcV2EngagementHelper.kt`
  (`8045e0e2ba776281b841332d2883db72191aedd7`)
- `multipaz/src/commonTest/kotlin/org/multipaz/mdoc/nfc/MdocNfcV2EngagementHelperTest.kt`
  (`311db5c38fd66334717f91320aebbf1d7884aabd`)
- `multipaz/src/commonMain/kotlin/org/multipaz/mdoc/connectionmethod/MdocConnectionMethodNfcV2.kt`
  (`99dd6cb0284ae170f3302614efed3be3476cb05f`)
- `multipaz/src/commonMain/kotlin/org/multipaz/mdoc/nfc/mdocReaderNfcHandover.kt`
  (`8a450059becb8c670326a1e42addbb440d400d1a`)
- `multipaz/src/commonMain/kotlin/org/multipaz/mdoc/transport/NfcHybridTransportMdoc.kt`
  (`bf5dca11afe626550e3de69884c82a7dd4c6a599`)
- `multipaz/src/commonMain/kotlin/org/multipaz/mdoc/transport/NfcHybridTransportMdocReader.kt`
  (`c08fbaf52c165149dc3ff1c5162b77007b2c884e`)
- `multipaz-compose/src/androidMain/kotlin/org/multipaz/compose/mdoc/MdocNfcV2Service.kt`
  (`b9f0bef87dbc472ec67289cac0b8c09b8ad65a96`)

Multipaz conventional NFC/APDU comparison surface:

- `multipaz/src/commonMain/kotlin/org/multipaz/mdoc/nfc/MdocNfcEngagementHelper.kt`
  (`dc85cdea40795dc111de28f5c67ab621eada7497`)
- `multipaz/src/commonTest/kotlin/org/multipaz/mdoc/nfc/MdocNfcEngagementHelperTest.kt`
  (`1112ecdd041f8bd3457bcc2c868bbf76cc7fdffc`)
- `multipaz-compose/src/androidMain/kotlin/org/multipaz/compose/mdoc/MdocNdefService.kt`
  (`5afdfc5d8f5db686d5edc724688ac2e6da80ebc1`)
- `multipaz/src/commonMain/kotlin/org/multipaz/nfc/NdefMessage.kt`
  (`44c1551c818f460d8d76db710675c8c96271fb04`)
- `multipaz/src/commonMain/kotlin/org/multipaz/nfc/CommandApdu.kt`
  (`9169830935f41066e91dbab580836ee4f83249d1`)
- `multipaz/src/commonMain/kotlin/org/multipaz/nfc/ResponseApdu.kt`
  (`1fd83f12bf842d300eabea2c4a3ac59ee599b75e`)
- `multipaz/src/commonMain/kotlin/org/multipaz/nfc/HandoverRequestRecord.kt`
  (`eb57912b063d85e9538e5a4f390baf1cf1782fd9`)
- `multipaz/src/commonMain/kotlin/org/multipaz/nfc/HandoverSelectRecord.kt`
  (`d40eec29fe6bdf6cf02af1e664141790ff72ff2a`)
- `multipaz/src/commonMain/kotlin/org/multipaz/nfc/ServiceParameterRecord.kt`
  (`ef45936b44b96639753086fa7449f9f986218e97`)
- `multipaz/src/commonMain/kotlin/org/multipaz/nfc/TnepStatusRecord.kt`
  (`54b3e0091ed9192cecf45351c7d48bf276460c8e`)

Axle independent conventional comparison surface:

- `kotlin/proximity/src/main/kotlin/com/hopae/eudi/wallet/proximity/Ndef.kt`
  (`6214860bf03fa64d4ede17bd23b019db362cf1d9`)
- `kotlin/proximity/src/main/kotlin/com/hopae/eudi/wallet/proximity/NfcTnep.kt`
  (`ea71f37095e7492d668a56d742778e770696b124`)
- `kotlin/proximity/src/main/kotlin/com/hopae/eudi/wallet/proximity/MdocNfcEngagement.kt`
  (`efa1ab96887d0fdd6e8baafcfd8f8c336b0845c4`)
- `kotlin/proximity/src/main/kotlin/com/hopae/eudi/wallet/proximity/NfcEngagementProcessor.kt`
  (`1b280720953acbc628396d3e43c0431455e78ee3`)
- `kotlin/proximity/src/main/kotlin/com/hopae/eudi/wallet/proximity/MdocNfcHandover.kt`
  (`8775a164606ba02b4387b66fb5887868126a9d2e`)
- `kotlin/proximity/src/test/kotlin/com/hopae/eudi/wallet/proximity/MdocNfcEngagementTest.kt`
  (`cc7c6ce0945fb6d290b6bcdcea5ac0b2632fcca2`)
- `kotlin/proximity/src/test/kotlin/com/hopae/eudi/wallet/proximity/MdocNfcHandoverTest.kt`
  (`a900d6aca15497a4e862367e5f8d5a17e050bdea`)
- `android/proximity/src/main/kotlin/com/hopae/eudi/wallet/android/proximity/NfcEngagementService.kt`
  (`df2136622418313129b475f1818b38ef64632071`)
- `android/proximity/src/main/res/xml/nfc_apduservice.xml`
  (`11f132270ad2fdb27b04844c1711439ba95eb67d`)
- `swift/Sources/Proximity/MdocNfcEngagement.swift`
  (`cf8d684058c6f61111ab734802ceca111efa6a11`)
- `swift/Sources/Proximity/Ndef.swift`
  (`b4a3927e425b1d17a01702ab6b1a45ebb187a2cf`)
- `swift/Tests/ProximityTests/MdocNfcEngagementTests.swift`
  (`57d1534e368826d501f6febb12f9d403c7808256`)

The audit selected no upstream source file for copying or adaptation. Multipaz remains the provisional
NFCv2 wire/interoperability oracle. Its broader model, crypto, I/O, coroutine, logging, and lifecycle
coupling would import a second architecture. Axle independently confirms useful conventional handover,
TNEP sequencing, role-selection, transcript, and Android lifecycle cases, but its deliberately minimal
parsers are not bounded or complete enough to adopt. Walt therefore implements fresh bounded common
code and retains only exact vectors and behavior comparisons in tests.

NFCv2 is implemented behind an explicitly provisional session profile until authoritative
second-edition text is available. The implementation follows only behavior confirmed by the pinned
Multipaz source and vectors:

- dedicated AID `A0000002480401`;
- a distinct NFCv2 retrieval method encoded as `[5, 1, {0: apduResponseMaxSize}]`; this reader-owned
  maximum response-data size remains separate from the holder's SELECT-response maximum command-data
  size;
- a positive SELECT-response maximum APDU command-data size in `1..65536`, represented separately
  from conventional NFC connection-method limits;
- CBOR Handover Request and Handover Select with exact encoded bytes retained for the transcript;
- exactly one selected retrieval method;
- NFC-only SessionEstablishment and SessionData when NFCv2 is selected;
- hybrid NFC-plus-selected-bearer delivery otherwise: each outgoing message is sent or queued on both,
  the first incoming copy by bearer-local ordinal is conveyed, and a redundant copy is suppressed;
- direction-local outgoing `seq` values starting at zero and incrementing once per emitted encrypted
  session message.

Multipaz 0.100.0 decodes incoming `seq` as ordinary message data and does not reject missing,
duplicate, replayed, or out-of-order values. The provisional interoperability profile therefore
requires and generates ordered outgoing values but does not invent stricter inbound rejection.
Inbound values are structurally decoded and accepted; authoritative reconciliation may introduce a
different receive policy later. Sequence numbers remain separate from AES-GCM IV counters and APDU
chaining.

Do not implement or advertise the unresolved older TNEP placeholder service. Ordinary NFC Forum
Static and Negotiated Handover through `urn:nfc:sn:handover` remain supported independently.

The exact DIS D.3.2 Handover Request vector includes an embedded NFC Forum Collision Resolution
record. Multipaz 0.100.0 generates conventional Handover Requests without that record. The holder
is unambiguously the Handover Selector in this flow, so the common parser retains and validates the
DIS form while tolerating the Multipaz form; all Alternative Carrier and referenced-record
invariants remain identical. Walt's deterministic test encoder follows the pinned Multipaz form
because it is an interoperability fixture, not a general Handover Requester implementation. Final
Connection Handover 1.5 reconciliation remains part of the qualification gate.

The DIS still contains an unresolved placeholder for the assigned 16-bit BLE service-data UUID
used to carry the L2CAP PSM. The pinned Multipaz implementation uses little-endian `0xFF01` as a
temporary November 2025 interoperability value. The codec isolates and documents that provisional
constant; it must be reconciled before a final generic ISO conformance claim and must not silently
be presented as an assigned value.

## Responsibility split

`waltid-mdoc-credentials2` owns exact normative models and serialization, including distinct
conventional NFC and provisional NFCv2 connection methods and explicit session-message sequence
numbers.

`waltid-mdoc-proximity` owns exact handover variants, engagement/retrieval selection, transcript
construction, session-profile selection, reader request/consent integration, timeouts, races,
generation ownership, and cleanup.

`waltid-mdoc-proximity-mobile/commonMain` owns bounded NDEF, Connection Handover, Type 4 Tag/TNEP,
ISO 7816 command/response, conventional retrieval, and NFCv2 APDU state. Android and iOS source
sets own only service/session eligibility, APDU delivery, response completion, deactivation, and
platform lifecycle callbacks.

Wallet configuration and capabilities decide whether NFC is permitted and currently available.
The existing transport-neutral review, consent, sending, repeated-request, and result journey owns
all user interaction.

## Consequences and evidence boundary

The common wire behavior can be tested deterministically without NFC hardware, upstream runtime
types do not enter the public ABI, and both platforms consume the same state machines. Walt owns
the resulting protocol surface and must refresh the pinned-oracle comparison deliberately.

When refreshing an oracle, record the new tag/commit, license, inspected files, behavior changes,
vector differences, and whether any provisional assumption can be retired. An upstream disagreement
never overrides the authorized standard.

This ADR does not claim ISO or platform conformance. Draft implementation may proceed while source
access and Apple entitlement approval are pending, but final qualification requires clause-level
reconciliation, physical Android interoperability, and separately authorized positive iOS evidence
when an eligible signed CardSession environment exists.
