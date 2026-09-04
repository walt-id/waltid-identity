package id.walt.mdoc.proximity.mobile

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import id.walt.mdoc.proximity.ProximityTransportProvider

/**
 * Android ISO mdoc BLE provider.
 *
 * The application remains responsible for explaining and requesting the runtime Bluetooth
 * permissions. Android 12 and newer require `BLUETOOTH_CONNECT`, plus `BLUETOOTH_SCAN` for the
 * central-client role and `BLUETOOTH_ADVERTISE` for the peripheral-server role. Android 11 uses
 * `ACCESS_FINE_LOCATION` for scanning. This provider reports missing grants through [capability]
 * and never launches permission UI.
 */
internal class AndroidBleProximityTransportProvider(
    context: Context,
    configuration: BleProximityTransportConfiguration,
) : ProximityTransportProvider by DefaultBleProximityTransportProvider(
    configuration,
    AndroidBlePlatformAdapter(context.applicationContext, configuration.roles.selection),
)

/** Android BLE preflight and provider factory. */
public class AndroidBleProximityTransportFactory(context: Context) : BleProximityTransportFactory {
    private val applicationContext: Context = context.applicationContext

    /** Checks the permissions and platform facilities required by [roles] without preparing BLE. */
    override suspend fun capability(roles: BleMdocRoleSelection): BleProximityAvailability =
        AndroidBlePlatformAdapter(applicationContext, roles).capability()

    /** Creates a session-configured provider without starting BLE operations. */
    override fun create(configuration: BleProximityTransportConfiguration): ProximityTransportProvider =
        AndroidBleProximityTransportProvider(applicationContext, configuration)
}

internal class AndroidBlePlatformAdapter(
    private val context: Context,
    private val roles: BleMdocRoleSelection,
) : BlePlatformAdapter {
    private val manager: BluetoothManager? = context.getSystemService(BluetoothManager::class.java)
    internal val adapter: BluetoothAdapter? get() = manager?.adapter

    @SuppressLint("MissingPermission")
    override suspend fun capability(): BleProximityAvailability {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            return unavailable("ble_unsupported", "This Android device does not support Bluetooth LE")
        }
        val bluetooth = adapter ?: return unavailable("ble_unsupported", "This Android device has no Bluetooth adapter")
        if (!hasRequiredPermissions()) {
            return unavailable("ble_permission_missing", "The required Android Bluetooth permission is not granted")
        }
        if (!bluetooth.isEnabled) return unavailable("ble_powered_off", "Bluetooth is powered off")
        if (needsCentral() && bluetooth.bluetoothLeScanner == null) {
            return unavailable("ble_scanner_unavailable", "The Android BLE scanner is unavailable")
        }
        if (needsPeripheral() && bluetooth.bluetoothLeAdvertiser == null) {
            return unavailable("ble_advertiser_unavailable", "The Android BLE advertiser is unavailable")
        }
        return BleProximityAvailability.Available
    }

    override suspend fun prepareCentralClient(
        serviceUuid: BleServiceUuid,
        expectedIdent: ByteArray,
        preferL2cap: Boolean,
        sessionScope: kotlinx.coroutines.CoroutineScope,
    ): BlePreparedPlatformRole = AndroidBleCentralRole(
        context,
        requireNotNull(adapter),
        serviceUuid,
        expectedIdent.copyOf(),
        preferL2cap,
        sessionScope,
    )

    override suspend fun preparePeripheralServer(
        serviceUuid: BleServiceUuid,
        preferL2cap: Boolean,
        sessionScope: kotlinx.coroutines.CoroutineScope,
    ): BlePreparedPlatformRole = AndroidBlePeripheralRole.create(
        context,
        requireNotNull(manager),
        requireNotNull(adapter),
        serviceUuid,
        preferL2cap,
        sessionScope,
    )

    private fun needsCentral(): Boolean =
        roles == BleMdocRoleSelection.CENTRAL_CLIENT || roles == BleMdocRoleSelection.DUAL

    private fun needsPeripheral(): Boolean =
        roles == BleMdocRoleSelection.PERIPHERAL_SERVER || roles == BleMdocRoleSelection.DUAL

    private fun hasRequiredPermissions(): Boolean {
        fun granted(permission: String) = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            granted(Manifest.permission.BLUETOOTH_CONNECT) &&
                (!needsCentral() || granted(Manifest.permission.BLUETOOTH_SCAN)) &&
                (!needsPeripheral() || granted(Manifest.permission.BLUETOOTH_ADVERTISE))
        } else {
            !needsCentral() || granted(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun unavailable(code: String, message: String) = BleProximityAvailability.Unavailable(code, message)
}
