# ADR 0001: BLE building-block selection

- Status: accepted for implementation; physical and external-reader qualification pending
- Ticket: [WAL-1345](https://linear.app/walt-new/issue/WAL-1345/implement-ble-proximity-transport-for-android-and-ios)
- Architecture contract: [waltid-architecture PR #60](https://github.com/walt-id/waltid-architecture/pull/60)
- Date: 2026-08-27

## Context

The holder must support both ISO mdoc BLE roles on Android and iOS, mandatory GATT transfer, and
LE L2CAP CoC where the platform and peer support it. ISO-specific Ident derivation, service and
characteristic definitions, framing, role selection, timeouts, errors, race ownership, and cleanup
must remain walt-owned common behavior. Platform code should be limited to Bluetooth discovery,
advertising, GATT, sockets/streams, permissions, and lifecycle callbacks.

The implementation baseline is the authorized `ISO/IEC DIS 18013-5:2025(E)` held in the architecture
repository, SHA-256
`6bf2cdfbc89ed992d4822d6f4f2ee30bdc1443bd8db35c5ab1b562c3811af7f4`. Clauses 11.1.1 through
11.1.4 govern the BLE roles, Device Engagement UUIDs, Ident, GATT state/framing, and L2CAP framing.
The [Bluetooth Core Specification 6.0, Vol 3, Part A](https://www.bluetooth.com/wp-content/uploads/Files/Specification/HTML/Core-60/out/en/host/logical-link-control-and-adaptation-protocol-specification.html)
supplies the dynamic LE SPSM range.

## Decision

Use walt-owned common protocol/state code over narrow Android Bluetooth and Apple CoreBluetooth
adapters. Do not add a third-party BLE runtime dependency and do not vendor upstream source.

This composition was selected because every evaluated shared dependency still required material
platform-specific work for the complete two-role GATT/L2CAP matrix. After its adapters and missing
features were counted, none reduced the total owned surface relative to direct system APIs. Direct
adapters also preserve Android API 30, avoid native binary/cinterop packaging, and keep one lifecycle
owner per platform.

The selected runtime closure is therefore:

- existing `waltid-mdoc-proximity` and `waltid-mdoc-credentials2` modules;
- existing coroutines and atomicfu dependencies;
- Android framework Bluetooth APIs, including dynamic
  [`BluetoothServerSocket` L2CAP listeners](https://developer.android.com/reference/android/bluetooth/BluetoothAdapter#listenUsingInsecureL2capChannel%28%29);
- Apple CoreBluetooth, including
  [`openL2CAPChannel`](https://developer.apple.com/documentation/corebluetooth/cbperipheral/openl2capchannel%28_%3A%29)
  and
  [`publishL2CAPChannel`](https://developer.apple.com/documentation/corebluetooth/cbperipheralmanager/publishl2capchannel%28withencryption%3A%29).

There is no new transitive BLE library, native framework, or package-manager dependency.

## Evaluated alternatives

| Candidate | Exact input | Useful boundary | Decision |
| --- | --- | --- | --- |
| [BlueFalcon](https://github.com/Reedyuk/blue-falcon) | tag `3.7.1`, commit [`975b2f04`](https://github.com/Reedyuk/blue-falcon/commit/975b2f04558ec335fb0d9a82937c721a83ace97b), Apache-2.0; `blue-falcon-core`, `blue-falcon-engine-android`, `blue-falcon-engine-ios`, and `blue-falcon-peripheral` | Both GATT roles and client-side L2CAP opening | Rejected as a runtime dependency. It has no Android L2CAP listener or Apple L2CAP publisher in the peripheral surface, so direct native L2CAP and lifecycle code remains necessary. Release provenance is also inconsistent: tag `3.7.2` declares library version `3.7.1`, and tag `3.7.3` declares `3.7.2`. |
| [`com.atruedev:kmp-ble`](https://github.com/gary-quinn/kmp-ble) | tag `v0.13.2`, commit [`eaead681`](https://github.com/gary-quinn/kmp-ble/commit/eaead681a6c3906d4d9a7351016043cebd6044b3), Apache-2.0 | Both GATT roles, both L2CAP directions, fakes, and contracts | Rejected as a runtime dependency or source import. The artifact declares Android minSdk 33 while walt supports API 30, uses Kotlin 2.4.10 and AGP 9.3.1 ahead of this stack, and introduces an Objective-C static-binary/cinterop boundary. The young project and broad adapter surface do not offset those costs. |
| [OWF Multipaz](https://github.com/openwallet-foundation/multipaz) | commit [`6a8a7281`](https://github.com/openwallet-foundation/multipaz/commit/6a8a7281d64ef5afaca56f972138b5c854ae699e), Apache-2.0; focused `BleTransportCentralMdoc.kt`, `BleTransportPeripheralMdoc.kt`, and platform manager files | ISO behavior and interoperability oracle | Rejected as a dependency or source import. The useful transport code is coupled to the broad Multipaz model, I/O, logging, crypto, and platform stack. Exact files remain an independent behavioral oracle. |
| [GOV.UK Android sharing](https://github.com/govuk-one-login/mobile-credential-sharing-android) | commit [`3e3aeb7b`](https://github.com/govuk-one-login/mobile-credential-sharing-android/commit/3e3aeb7bf084462cc959e89c92cfa5a41a892606), MIT | GATT state, queue, chunking, and negative-test oracle | No source imported. The transport has no L2CAP and its published module brings GOV.UK-specific support layers. |
| [GOV.UK iOS sharing](https://github.com/govuk-one-login/mobile-credential-sharing-ios) | tag `1.9.0`, commit [`4ce1cdc4`](https://github.com/govuk-one-login/mobile-credential-sharing-ios/commit/4ce1cdc422cdc9ced81cc66938ca15ce06477d3f), MIT | Focused CoreBluetooth GATT lifecycle and test oracle | No source imported. The focused source target is useful for comparison but has no L2CAP and does not remove the walt common state machine. |

“Oracle” means an implementation used for differential review and test design. It does not mean
selected source or copied code. No upstream implementation file was copied or adapted, so this
change requires no source-derived attribution notice beyond these decision references.

## Responsibility split

`commonMain` owns:

- role and bearer policy types that prevent unsupported Boolean/null combinations;
- exact 16-byte BLE service-UUID validation, QR-specific RFC 4122 variant validation, and dual-role invariants;
- BLEIdent HKDF and constant-time comparison;
- ISO GATT and L2CAP framing, message limits, and truncation detection;
- preparation/connection races, one receive consumer, serialized sends, timeouts, normalized errors,
  and idempotent cleanup.

`androidMain` and `iosMain` own only platform prerequisites and radio operations. Platform exceptions
are normalized at the shared transport boundary, and platform objects never enter the public common
contract. Engagement, session cryptography, requests, consent, trust, wallet policy, and UI remain in
their existing layers.

## Protocol decisions requiring qualification

- GATT is always available in a prepared BLE method; L2CAP is optional and selected only under
  `PreferL2cap`. A missing or failed optional L2CAP connection falls back to GATT, while malformed PSM
  data fails closed.
- Dynamic LE SPSMs are restricted to `0x0080..0x00ff`, as defined by Bluetooth Core 6.0.
- The ISO DIS defines the L2CAP length as a fixed four-byte integer but does not state its byte order.
  This implementation uses unsigned big-endian encoding, matching the inspected Multipaz behavior.
  That interpretation remains an explicit interoperability item until it is proven against the
  pinned external-reader matrix or superseded by authoritative text.
- The providers do not retry or create new engagements. A fresh attempt belongs to the wallet/session
  layer and creates fresh transaction UUIDs and ephemeral material.

## Consequences and evidence boundary

The common protocol and lifecycle behavior can be tested deterministically without radios, and both
platform source sets compile against the same public contract. Direct adapters mean walt owns the
small platform Bluetooth surface and must track Android and Apple callback/API changes.

This ADR does not claim production or ISO conformance. Production qualification requires the
two-device matrix to prove both roles, GATT, supported L2CAP, cancellation, disconnects, radio and
permission changes, and repeated exchanges on physical Android and iOS devices. Pinned Multipaz
Identity Reader, Stelau, EUDI verifier, and independent-reader runs remain external acceptance
evidence. Any failed matrix cell must be classified as implementation, device/platform limitation,
external-reader limitation, or unavailable standard authority rather than being silently dropped.
