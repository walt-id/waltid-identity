@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package id.walt.mdoc.proximity.mobile

import id.walt.mdoc.proximity.ReaderSelectedTransportProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBCentralManagerDelegateProtocol
import platform.CoreBluetooth.CBCentralManagerStatePoweredOn
import platform.CoreBluetooth.CBCentralManagerStateUnknown
import platform.CoreBluetooth.CBManagerAuthorizationAllowedAlways
import platform.CoreBluetooth.CBManagerAuthorizationDenied
import platform.CoreBluetooth.CBManagerAuthorizationNotDetermined
import platform.CoreBluetooth.CBManagerAuthorizationRestricted
import platform.darwin.NSObject
import kotlin.time.Duration.Companion.seconds

/**
 * Apple CoreBluetooth ISO mdoc BLE provider.
 *
 * The containing application must provide `NSBluetoothAlwaysUsageDescription`. This provider does
 * not trigger authorization UI from [capability]; authorization and powered-off states are surfaced
 * through the proximity capability or preparation error contracts.
 */
internal class IosBleProximityTransportProvider(
    configuration: BleProximityTransportConfiguration,
) : ReaderSelectedTransportProvider by DefaultBleProximityTransportProvider(
    configuration,
    IosBlePlatformAdapter(),
)

/** iOS BLE preflight and provider factory. */
public class IosBleProximityTransportFactory : BleProximityTransportFactory {
    /** Checks CoreBluetooth authorization and runtime state without preparing BLE roles. */
    override suspend fun capability(roles: BleMdocRoleSelection): BleProximityAvailability =
        IosBlePlatformAdapter().capability()

    /** Creates a session-configured provider without starting BLE operations. */
    override fun create(configuration: BleProximityTransportConfiguration): ReaderSelectedTransportProvider =
        IosBleProximityTransportProvider(configuration)
}

internal class IosBlePlatformAdapter : BlePlatformAdapter {
    override suspend fun capability(): BleProximityAvailability = withContext(Dispatchers.Main) {
        when (CBCentralManager.authorization) {
            CBManagerAuthorizationAllowedAlways -> {
                val state = withTimeoutOrNull(2.seconds) { IosBluetoothStateProbe().state() }
                    ?: return@withContext BleProximityAvailability.Unavailable(
                        "ble_state_unknown",
                        "CoreBluetooth did not report its state",
                    )
                if (state == CBCentralManagerStatePoweredOn) {
                    BleProximityAvailability.Available
                } else {
                    BleProximityAvailability.Unavailable(
                        "ble_powered_off",
                        "CoreBluetooth is unavailable in state $state",
                    )
                }
            }
            CBManagerAuthorizationNotDetermined -> BleProximityAvailability.Unavailable(
                "ble_permission_not_determined",
                "Bluetooth authorization has not been requested by the application",
            )
            CBManagerAuthorizationDenied -> BleProximityAvailability.Unavailable(
                "ble_permission_denied",
                "Bluetooth access is denied",
            )
            CBManagerAuthorizationRestricted -> BleProximityAvailability.Unavailable(
                "ble_permission_restricted",
                "Bluetooth access is restricted",
            )
            else -> BleProximityAvailability.Unavailable(
                "ble_permission_unknown",
                "Bluetooth authorization is unavailable",
            )
        }
    }

    override suspend fun prepareCentralClient(
        serviceUuid: BleServiceUuid,
        expectedIdent: ByteArray,
        preferL2cap: Boolean,
        sessionScope: CoroutineScope,
    ): BlePreparedPlatformRole = withContext(Dispatchers.Main) {
        IosBleCentralRole(serviceUuid, expectedIdent.copyOf(), preferL2cap, sessionScope)
    }

    override suspend fun preparePeripheralServer(
        serviceUuid: BleServiceUuid,
        preferL2cap: Boolean,
        sessionScope: CoroutineScope,
    ): BlePreparedPlatformRole = withContext(Dispatchers.Main) {
        IosBlePeripheralRole.create(serviceUuid, preferL2cap, sessionScope)
    }
}

private class IosBluetoothStateProbe {
    private val states = Channel<Long>(Channel.CONFLATED)
    private val delegate = object : NSObject(), CBCentralManagerDelegateProtocol {
        override fun centralManagerDidUpdateState(central: CBCentralManager) {
            states.trySend(central.state)
        }
    }
    private val manager = CBCentralManager(delegate = delegate, queue = null, options = null)

    suspend fun state(): Long = try {
        var current = manager.state
        while (current == CBCentralManagerStateUnknown) current = states.receive()
        current
    } finally {
        manager.delegate = null
        states.close()
    }
}
