<div align="center">
  <h1>Mobile mdoc proximity transports</h1>
  <p>by <a href="https://walt.id">walt.id</a></p>
  <p>Android and iOS adapters for the shared ISO mdoc holder protocol.</p>
</div>

## Overview

The module owns ISO/IEC 18013-5 BLE roles, Ident verification, GATT and L2CAP framing; bounded NFC
NDEF, Connection Handover, APDU, and retrieval state; Wi-Fi Aware NCS-SK derivation, carrier and
HTTP framing; the Android Bluetooth, host-card-emulation, and Wi-Fi Aware adapters; and the Apple
CoreBluetooth adapter. Its NFC host boundary is consumed by the Swift CardSession adapter in
`waltid-wallet-sdk-ios`. Device engagement coordination, session encryption, request processing,
consent, and trust remain in `waltid-mdoc-proximity`.

| Transport | Android | iOS |
|---|---|---|
| BLE central/client and peripheral/server | Native Bluetooth adapters; GATT and L2CAP | CoreBluetooth adapters; GATT and L2CAP |
| Conventional NFC engagement and retrieval | Host-card emulation adapter | Host boundary consumed by Swift WalletSDK's entitlement-gated CardSession adapter |
| Provisional NFCv2 | Explicit edition-2 draft path | Same host boundary; platform access and reader support required |

`AndroidWifiAwareProximityTransportFactory` supports the mandatory NCS-SK-128 holder path on API
33+ devices that expose Wi-Fi Aware, the cipher suite, a 2.4 GHz NAN band, and available publish/NDP
resources. It performs no prompt or radio attach during capability checks. Android 13+ hosts request
`NEARBY_WIFI_DEVICES`; target-37 hosts also request `ACCESS_LOCAL_NETWORK`. The provider publishes
the transaction-derived service, establishes a secure responder data path, and serves bounded
sequential `POST /mdoc` exchanges. `IosWifiAwareProximityTransportFactory` reports a precise
unimplemented result because Apple's paired, statically declared DNS-SD service model cannot express
the ISO transaction service name.

Use `AndroidBleProximityTransportFactory` or `IosBleProximityTransportFactory` to check the exact
role selection before generating session keys or transaction UUIDs. The probe does not prepare
radio resources. After prerequisites pass, create a validated `BleProximityTransportConfiguration`
for one transaction and ask the same factory for its provider. The provider prepares only methods
that can actually be advertised and exposes them through the shared `ProximityTransportProvider`
contract.

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
the provisional NFCv2 evidence boundary. See [ADR 0003](docs/adr/0003-wifi-aware-building-block-selection.md)
for Wi-Fi Aware sources, Android/iOS disposition, security scope, and evidence gates. Physical-device
and external-reader qualification remains required before production support is claimed.
