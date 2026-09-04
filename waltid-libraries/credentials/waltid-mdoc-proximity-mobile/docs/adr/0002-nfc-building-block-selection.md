# ADR 0002: NFC building-block selection

- Status: accepted for implementation; authoritative NFCv2 reconciliation and final qualification pending
- Ticket: [WAL-1347](https://linear.app/walt-new/issue/WAL-1347/implement-nfc-engagement-and-device-retrieval)
- Architecture contract: [waltid-architecture PR #60](https://github.com/walt-id/waltid-architecture/pull/60)
- Date: 2026-08-29

## Context

The holder needs conventional NFC Static and Negotiated Handover, NFC device retrieval, and an
explicitly provisional NFC Engagement v2 profile with same-channel or hybrid retrieval. Exact
Handover Select and Handover Request bytes, bounded peer input, ISO 7816 APDU state, engagement and
retrieval races, session-message sequencing, and cleanup must remain consistent across Android and
iOS.

The normative implementation baseline is the authorized `ISO/IEC DIS 18013-5:2025(E)` retained in
the architecture repository, SHA-256
`6bf2cdfbc89ed992d4822d6f4f2ee30bdc1443bd8db35c5ab1b562c3811af7f4`. Final clause-level
reconciliation against the applicable ISO/IEC 7816-4, NFC Forum Connection Handover, NDEF, Type 4
Tag, TNEP, and authoritative second-edition NFCv2 material remains a qualification gate.

The DIS retains unresolved placeholders for a SessionEstablishment-during-Negotiated-Handover TNEP
service. The provisional NFCv2 contract instead uses a dedicated AID, CBOR handover, and the same
APDU channel. Both address obtaining the reader request before retrieval setup and consent, but the
available normative source does not establish their formal relationship.

## Decision

Implement the NFC protocol and state machines as walt-owned common code over narrow Android HCE and
Apple `CardSession` adapters. Add no NFC runtime dependency and expose no external implementation
types through the public ABI.

Common code owns:

- bounded NDEF and Connection Handover parsing and encoding;
- Type 4 Tag, TNEP, ISO 7816 command/response, chaining, status, and termination state;
- conventional NFC retrieval and exact transcript bytes;
- engagement, transport, and hybrid first-arrival races with deterministic loser cleanup;
- the isolated provisional NFCv2 AID, CBOR handover, retrieval method, and session-message profile;
- capability, timeout, error, and lifecycle semantics consumed by the Wallet SDK.

Platform code owns only service or session eligibility, APDU delivery and response completion,
deactivation, foreground routing, and OS lifecycle callbacks. The Wallet SDK exposes sealed Kotlin
and Swift-native configuration variants so conventional NFC and NFCv2 cannot be combined illegally.

### Conventional NFC compatibility boundary

Support NFC Forum Static and Negotiated Handover through `urn:nfc:sn:handover`, retaining the exact
Hs and Hr bytes used by the session transcript. Negotiated Handover accepts only a carrier that an
eligible configured provider can prepare, returns exactly one selected carrier, and closes every
unselected candidate.

The DIS D.3.2 Handover Request vector includes an embedded Collision Resolution record. A reader may
omit that record when the holder has the fixed Handover Selector role. The parser accepts both forms
while applying the same Alternative Carrier and referenced-record invariants; the encoder produces a
deterministic request without pretending to be a general Handover Requester implementation.

Reader-owned BLE endpoints are permitted only in NFC handover placement. QR Device Engagement still
rejects them and still requires RFC 4122 variant-1 BLE UUID encoding. NFC handover retains the exact
128-bit reader service UUID because deployed readers may use a byte pattern that is valid for BLE but
is not an RFC 4122 variant-1 UUID.

The available DIS contains an unresolved placeholder for the assigned 16-bit BLE service-data UUID
used to carry an L2CAP PSM. The codec isolates the temporary interoperability value `0xFF01`; it must
be reconciled before a final generic ISO conformance claim.

### Provisional NFCv2 boundary

NFCv2 remains a separately typed, explicitly provisional profile:

- the reader selects AID `A0000002480401`;
- the holder SELECT response carries a positive maximum command-data size in `1..65536`;
- the reader NFCv2 method is `[5, 1, {0: apduResponseMaxSize}]`, with a separately validated positive
  response-data size in `1..65536`;
- CBOR Handover Request and Handover Select bytes are retained exactly for the transcript;
- the holder selects exactly one retrieval method;
- selecting NFCv2 continues SessionEstablishment and SessionData over the APDU channel;
- selecting an alternate bearer retains NFC and that bearer as a first-arrival-wins hybrid;
- outgoing `seq` values are direction-local, start at zero, and increment once per encrypted session
  message;
- inbound `seq` values are structurally validated but their presence and order are not enforced until
  authoritative material defines a stricter receive policy.

Sequence numbers remain independent from AES-GCM IV counters and APDU chaining. Conventional
sessions reject any NFCv2 `seq` field. The unresolved older TNEP placeholder service is neither
implemented nor advertised.

## Responsibility split

`waltid-mdoc-credentials2` owns exact models and serialization, including distinct conventional NFC
and provisional NFCv2 connection methods and explicit session-message sequence numbers.

`waltid-mdoc-proximity` owns handover variants, engagement and retrieval selection, transcript
construction, session-profile selection, request and consent integration, timeouts, races,
generation ownership, and cleanup.

`waltid-mdoc-proximity-mobile/commonMain` owns NDEF, Connection Handover, Type 4 Tag/TNEP, ISO 7816,
conventional retrieval, and NFCv2 APDU state. Android and Swift code own only the OS boundary.

Wallet configuration and capabilities decide whether NFC is selected and currently available. The
existing transport-neutral review, consent, sending, repeated-request, and result journey owns all
user interaction.

## Consequences and evidence boundary

The common wire behavior is deterministic and testable without NFC hardware, both platforms consume
the same state machines, and platform APIs do not leak into common code or the public Wallet SDK
contract.

Apple's presentment-intent assertion is an optional, short-lived suppression of the default
contactless app, not an HCE capability prerequisite. The Swift adapter attempts to acquire it only
for the user-started presentation, holds it while valid, never renews it without a fresh user action,
and keeps the eligible `CardSession` independent of assertion failure or expiry.

Physical Android evidence proves conventional Negotiated Handover into a reader-selected BLE bearer,
review and consent, response delivery, and deterministic completion. Provisional NFCv2 remains
host-tested until a compatible physical reader run is recorded. Positive iOS card-emulation evidence
remains gated by regenerated provisioning profiles containing Apple's newly approved HCE capability
and AIDs, followed by an eligible signed-device run with NFC reader hardware.

This ADR does not claim final ISO or platform conformance. Final qualification requires reconciling
the now-public ISO/IEC TS 18013-6:2025 test appendices while preserving the still-missing normative
TS text boundary, completing the remaining interoperability matrix, and recording positive iOS
evidence.
