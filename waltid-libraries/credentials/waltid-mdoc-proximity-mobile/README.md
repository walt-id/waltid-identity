<div align="center">
  <h1>Mobile mdoc proximity transports</h1>
  <p>by <a href="https://walt.id">walt.id</a></p>
  <p>Android and iOS adapters for the shared ISO mdoc holder protocol.</p>
</div>

Kotlin Multiplatform mobile transports for the radio-independent ISO mdoc proximity engine.

The module owns ISO/IEC 18013-5 BLE roles, Ident verification, GATT and L2CAP framing; bounded NFC
NDEF, Connection Handover, APDU, and retrieval state; the Android Bluetooth and host-card-emulation
adapters; and the Apple CoreBluetooth adapter. Its NFC host boundary is consumed by the Swift
CardSession adapter in `waltid-wallet-sdk-ios`. Device engagement coordination, session encryption,
request processing, consent, and trust remain in `waltid-mdoc-proximity`.

| Transport | Android | iOS |
|---|---|---|
| BLE central/client and peripheral/server | Native Bluetooth adapters; GATT and L2CAP | CoreBluetooth adapters; GATT and L2CAP |

These are implementation boundaries. Physical-device and independent-reader
qualification remain separate from compilation, unit tests and API availability.

## Getting started

Within the coordinated source build:

```kotlin
commonMain.dependencies {
    implementation(project(":waltid-libraries:credentials:waltid-mdoc-proximity-mobile"))
}
```

Most wallet apps should use the
[Mobile Wallet SDK's `ProximityConfiguration`](../../protocols/waltid-openid4vc-wallet-mobile/README.md#in-person-proximity-presentation)
or [Swift WalletSDK](../../protocols/waltid-wallet-sdk-ios/README.md). Those APIs
coordinate transports, stored credentials, Reader CA policy and the complete
review/approval lifecycle. The transport provider itself does not select
credentials or request consent.

See [ADR 0001](docs/adr/0001-ble-building-block-selection.md) for the BLE standards baseline,
upstream candidates, selected native composition, ownership boundary, and qualification status.
See [ADR 0002](docs/adr/0002-nfc-building-block-selection.md) for the equivalent NFC decision and
the provisional NFCv2 evidence boundary. Physical-device and external-reader qualification remains
required before production support is claimed.
