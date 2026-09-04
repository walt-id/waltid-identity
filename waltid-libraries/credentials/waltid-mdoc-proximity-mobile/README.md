# waltid-mdoc-proximity-mobile

Kotlin Multiplatform mobile transports for the radio-independent ISO mdoc proximity engine.

The module owns ISO/IEC 18013-5 BLE roles, Ident verification, GATT and L2CAP framing; bounded NFC
NDEF, Connection Handover, APDU, and retrieval state; Wi-Fi Aware NCS-SK derivation, carrier and
HTTP framing; the Android Bluetooth, host-card-emulation, and Wi-Fi Aware adapters; and the Apple
CoreBluetooth adapter. Its NFC host boundary is consumed by the Swift CardSession adapter in
`waltid-wallet-sdk-ios`. Device engagement coordination, session encryption, request processing,
consent, and trust remain in `waltid-mdoc-proximity`.

Applications must declare and request the platform Bluetooth permissions described by the public
Android and Apple provider KDoc. Bluetooth availability is reported through the proximity
capability contract rather than prompting from inside this library.

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

Prepared BLE listeners remain available until the shared proximity session selects a connection or
closes them. The radio-independent engine owns the advertised engagement lifetime so a displayed QR
code cannot outlive its BLE retrieval path; the BLE module still bounds radio setup and
post-connection inactivity.

Android applications need the merged manifest permissions plus runtime grants for the selected
role. Apple applications need `NSBluetoothAlwaysUsageDescription`; the provider uses CoreBluetooth
on its main queue and does not request authorization itself.

See [ADR 0001](docs/adr/0001-ble-building-block-selection.md) for the BLE standards baseline,
upstream candidates, selected native composition, ownership boundary, and qualification status.
See [ADR 0002](docs/adr/0002-nfc-building-block-selection.md) for the equivalent NFC decision and
the provisional NFCv2 evidence boundary. See [ADR 0003](docs/adr/0003-wifi-aware-building-block-selection.md)
for Wi-Fi Aware sources, Android/iOS disposition, security scope, and evidence gates. Physical-device
and external-reader qualification remains required before production support is claimed.
