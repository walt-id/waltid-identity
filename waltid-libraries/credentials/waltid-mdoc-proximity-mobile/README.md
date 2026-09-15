<div align="center">
  <h1>Mobile mdoc proximity transports</h1>
  <p>by <a href="https://walt.id">walt.id</a></p>
  <p>Android and iOS adapters for the shared ISO mdoc holder protocol.</p>
</div>

## Overview

`waltid-mdoc-proximity-mobile` connects the
[shared proximity engine](../waltid-mdoc-proximity/README.md) to platform radios.
It owns BLE role setup, Ident checks, GATT/L2CAP framing, NFC handover/APDU framing
and Wi-Fi Aware transport. The shared engine owns engagement coordination,
session encryption, request processing, consent and trust decisions.

| Transport | Android | iOS |
|---|---|---|
| BLE central/client and peripheral/server | Native Bluetooth adapters; GATT and L2CAP | CoreBluetooth adapters; GATT and L2CAP |
| Conventional NFC engagement and retrieval | Host-card emulation adapter | Host boundary consumed by Swift WalletSDK's entitlement-gated CardSession adapter |
| Provisional NFCv2 | Explicit edition-2 draft path | Same host boundary; platform access and reader support required |
| ISO Wi-Fi Aware, mandatory NCS-SK-128 | API 33+ with the required hardware, cipher and NAN facilities | Reports unimplemented for this ISO transaction-service model |

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

### Check Android BLE availability

```kotlin
import android.content.Context
import id.walt.mdoc.proximity.mobile.*

suspend fun checkBle(context: Context): BleProximityAvailability {
    val factory = AndroidBleProximityTransportFactory(context)
    return factory.capability(BleMdocRoleSelection.DUAL)
}
```

`capability` checks exactly the selected roles without generating keys, creating
transaction UUIDs or starting radio resources. Handle `Available` and
`Unavailable` explicitly in the host; an unavailable result supplies a stable
code and diagnostic message for the application's remediation UI.

### Create a provider for one transaction

After prerequisites pass, the integration supplies the role configuration and
exact tagged `EDeviceKeyBytes` from that transaction's Device Engagement:

```kotlin
import id.walt.mdoc.proximity.ImmutableBytes
import id.walt.mdoc.proximity.ReaderSelectedTransportProvider
import id.walt.mdoc.proximity.mobile.*

fun createBleProvider(
    factory: BleProximityTransportFactory,
    roles: BleMdocRoles,
    eDeviceKeyBytes: ImmutableBytes,
): ReaderSelectedTransportProvider = factory.create(
    BleProximityTransportConfiguration(
        roles = roles,
        bearerPolicy = BleBearerPolicy.PreferL2cap,
        eDeviceKeyBytes = eDeviceKeyBytes,
    ),
)
```

Use `IosBleProximityTransportFactory` for the equivalent iOS integration. Factory
creation does not start BLE: the engine prepares the provider for engagement and
owns its lifetime. Prepared listeners remain available until a connection wins
or the session closes, so an advertised QR cannot outlive its retrieval path.

## Platform setup and lifecycle

Android hosts need the merged manifest declarations and runtime permissions for
the selected roles. Apple hosts need `NSBluetoothAlwaysUsageDescription`; the
provider runs CoreBluetooth on its main queue and leaves authorization prompts
to the app. See the platform provider KDoc for the permission matrix.

Both central adapters follow the same Ident, bearer-selection, subscription and
START order. Bounded GATT queues fail with `ble_receive_overflow` rather than
retaining incomplete messages; callbacks after closure are ignored. Android
blocking socket operations register cancellation-driven closure before native
connect, accept, read or write operations begin.

Active connections expose passive closure observation to the shared engine. BLE
observes closure of its platform packet channel without consuming packets or
starting the receive inactivity timeout.

NFC host deactivation ends a direct APDU connection even while the wallet awaits
consent. Conventional handover does not close the selected BLE connection; NFCv2
hybrid retrieval remains viable while its alternate bearer is active or connecting.

Wi-Fi Aware forwards local and observed platform closure without starting an
additional socket read.

Successful L2CAP completion permits a one-second drain before native cleanup.
This allows queued response bytes to leave the radio stack; it is not a reader
acknowledgement. Cancellation and error cleanup remain immediate.

## Wi-Fi Aware

`AndroidWifiAwareProximityTransportFactory` requires API 33+, Wi-Fi Aware,
NCS-SK-128, a 2.4 GHz NAN band and available publish/NDP resources. Android 13+
hosts request `NEARBY_WIFI_DEVICES`; target-37 hosts also request
`ACCESS_LOCAL_NETWORK`. Capability checks perform no radio attach or prompt.

The provider publishes the transaction-derived service, establishes a secure
responder data path and serves bounded sequential `POST /mdoc` exchanges. Each
provider owns one publisher and one accept operation. Concurrent QR/NFC routes
use separate keys and service names; never share a provider between them.
Callback resources enter their owner before coroutine delivery, and cancellation
closes blocking sockets before joining workers. NFCv2 HTTP reads and duplicate
responses respect request/response ordering.

The iOS factory reports a specific unimplemented result because Apple's paired,
statically declared DNS-SD service model cannot express the ISO transaction
service name.

## Tests and design references

From the unified-build root:

```bash
./gradlew :waltid-libraries:credentials:waltid-mdoc-proximity-mobile:allTests -PenableAndroidBuild=true -PenableIosBuild=true
```

- [BLE building blocks and qualification boundary](docs/adr/0001-ble-building-block-selection.md)
- [NFC, handover and provisional NFCv2](docs/adr/0002-nfc-building-block-selection.md)
- [Wi-Fi Aware sources and platform scope](docs/adr/0003-wifi-aware-building-block-selection.md)
- [Wallet integration, reader trust and credential selection](../../protocols/waltid-openid4vc-wallet-mobile/README.md#in-person-proximity-presentation)
