# CoreBluetooth callback tests

`IosCentralGattCallbackTest` and `IosPeripheralGattCallbackTest` invoke the production Kotlin
CoreBluetooth delegates with a controlled native callback schedule. They assert peer identity,
exact packet order, write backpressure, disconnect/unsubscribe, cancellation and fresh recovery.
The enclosing shared transport already tests its finite write/finish deadlines.

`objc/CoreBluetoothTestDoubles.m` contains test-only Objective-C selector doubles. Manager and peer
doubles inherit NSObject and do not initialize CoreBluetooth services. The request double is an
inert CBATTRequest subclass allocated without its unavailable initializer so the production
delegate's CBATTRequest type filter is exercised. It overrides every property the adapter reads;
it makes no assumptions about private ivar layouts. No double calls a radio API.

The Gradle test compilation alone imports their C header and links their object file. That object
is an explicit test-link input, so changing a double must relink the executable. Main compilations,
release libraries and WalletCore frameworks do not contain these fixtures. The only construction
seams in production are internal factories retaining the original CoreBluetooth defaults.

Run the owning module's `iosSimulatorArm64Test` with selector `*Ios*GattCallbackTest*` and
`-PenableIosBuild=true`. These are deterministic simulator tests, not BLE/L2CAP radio or signed-host
qualification. A callback double cannot establish actual OS callback timing or delivery to a peer.
