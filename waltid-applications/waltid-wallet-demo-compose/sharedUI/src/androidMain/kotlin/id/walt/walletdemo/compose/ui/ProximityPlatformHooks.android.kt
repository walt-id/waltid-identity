package id.walt.walletdemo.compose.ui

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.tech.IsoDep
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import id.walt.wallet2.mobile.MobileWalletProximityHostActionResult
import id.walt.wallet2.mobile.MobileWalletProximityRemediationAction
import id.walt.walletdemo.compose.logic.WalletDemoProximityHostActionExecutor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred

@Composable
internal actual fun rememberProximityHostActions(): WalletDemoProximityHostActions {
    val context = LocalContext.current
    val activity = context.findActivity()
    val permissionHistory = remember(context.applicationContext) {
        AndroidPermissionRequestHistory(context.applicationContext)
    }
    var permissionRequest by remember {
        mutableStateOf<CompletableDeferred<MobileWalletProximityHostActionResult>?>(null)
    }
    var systemSurface by remember {
        mutableStateOf<CompletableDeferred<MobileWalletProximityHostActionResult>?>(null)
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        permissionHistory.recordDecision(grants.keys)
        permissionRequest?.complete(
            if (grants.isNotEmpty() && grants.values.all { it }) {
                MobileWalletProximityHostActionResult.Completed
            } else {
                MobileWalletProximityHostActionResult.Cancelled
            }
        )
        permissionRequest = null
    }
    val systemSurfaceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        systemSurface?.complete(MobileWalletProximityHostActionResult.Completed)
        systemSurface = null
    }
    fun permissionRoute(permissions: Array<String>): AndroidRuntimePermissionRoute =
        androidRuntimePermissionRoute(
            permissions = permissions.asList(),
            isGranted = { permission ->
                ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            },
            wasRequested = permissionHistory::wasRequested,
            shouldShowRationale = { permission ->
                activity?.shouldShowRequestPermissionRationale(permission) == true
            },
        )
    suspend fun requestPermissions(permissions: Array<String>): MobileWalletProximityHostActionResult {
        when (permissionRoute(permissions)) {
            AndroidRuntimePermissionRoute.Granted -> return MobileWalletProximityHostActionResult.Completed
            AndroidRuntimePermissionRoute.OpenSettings -> return launchSystemSurface(
                applicationSettingsIntent(context),
                current = { systemSurface },
                setCurrent = { systemSurface = it },
                launch = systemSurfaceLauncher::launch,
            )
            AndroidRuntimePermissionRoute.Request -> Unit
        }
        if (permissionRequest != null) return MobileWalletProximityHostActionResult.Failed
        return CompletableDeferred<MobileWalletProximityHostActionResult>().let { result ->
            permissionRequest = result
            try {
                permissionLauncher.launch(permissions)
                result.await()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                MobileWalletProximityHostActionResult.Failed
            } finally {
                if (permissionRequest === result) permissionRequest = null
            }
        }
    }

    return remember(context, permissionLauncher, systemSurfaceLauncher, activity) {
        WalletDemoProximityHostActions(
            executor = WalletDemoProximityHostActionExecutor { action ->
                when (action) {
                    MobileWalletProximityRemediationAction.RequestBluetoothPermission ->
                        requestPermissions(bluetoothPermissions())
                    MobileWalletProximityRemediationAction.RequestNearbyWifiPermission ->
                        requestPermissions(arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES))
                    MobileWalletProximityRemediationAction.RequestLocalNetworkPermission ->
                        requestPermissions(arrayOf(ACCESS_LOCAL_NETWORK_PERMISSION))
                    MobileWalletProximityRemediationAction.OpenApplicationSettings ->
                        launchSystemSurface(
                            applicationSettingsIntent(context),
                            current = { systemSurface },
                            setCurrent = { systemSurface = it },
                            launch = systemSurfaceLauncher::launch,
                        )
                    MobileWalletProximityRemediationAction.EnableBluetooth ->
                        launchSystemSurface(
                            Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),
                            current = { systemSurface },
                            setCurrent = { systemSurface = it },
                            launch = systemSurfaceLauncher::launch,
                        )
                    MobileWalletProximityRemediationAction.EnableWifi ->
                        launchSystemSurface(
                            Intent(Settings.ACTION_WIFI_SETTINGS),
                            current = { systemSurface },
                            setCurrent = { systemSurface = it },
                            launch = systemSurfaceLauncher::launch,
                        )
                    MobileWalletProximityRemediationAction.EnableNfc ->
                        launchSystemSurface(
                            Intent(Settings.ACTION_NFC_SETTINGS),
                            current = { systemSurface },
                            setCurrent = { systemSurface = it },
                            launch = systemSurfaceLauncher::launch,
                        )
                    MobileWalletProximityRemediationAction.Retry ->
                        MobileWalletProximityHostActionResult.Completed
                    MobileWalletProximityRemediationAction.UseSupportedDevice ->
                        MobileWalletProximityHostActionResult.Cancelled
                }
            },
            actionForDisplay = { action ->
                val permissions = runtimePermissionsFor(action)
                if (permissions != null && permissionRoute(permissions) == AndroidRuntimePermissionRoute.OpenSettings) {
                    MobileWalletProximityRemediationAction.OpenApplicationSettings
                } else {
                    action
                }
            },
            automaticallyPerform = { action ->
                runtimePermissionsFor(action)?.let { permissions ->
                    permissionRoute(permissions) != AndroidRuntimePermissionRoute.OpenSettings
                } ?: true
            },
        )
    }
}

@Composable
internal actual fun ProximityPlatformSessionEffect(
    active: Boolean,
    qrVisible: Boolean,
    nfcReviewVisible: Boolean,
    onInterrupted: () -> Unit,
) {
    val activity = LocalContext.current.findActivity()
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnInterrupted by rememberUpdatedState(onInterrupted)

    DisposableEffect(active, activity, lifecycleOwner) {
        if (!active || activity == null) return@DisposableEffect onDispose {}
        val keptScreenOn = activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                Lifecycle.Event.ON_STOP -> {
                    if (!keptScreenOn) activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    currentOnInterrupted()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (!keptScreenOn) activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    DisposableEffect(qrVisible, activity, lifecycleOwner) {
        if (!qrVisible || activity == null) return@DisposableEffect onDispose {}
        val previousBrightness = activity.window.attributes.screenBrightness
        fun setBrightness(value: Float) {
            activity.window.attributes = activity.window.attributes.apply { screenBrightness = value }
        }
        setBrightness(1f)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> setBrightness(1f)
                Lifecycle.Event.ON_STOP -> setBrightness(previousBrightness)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            setBrightness(previousBrightness)
        }
    }

    DisposableEffect(nfcReviewVisible, activity, lifecycleOwner) {
        if (!nfcReviewVisible || activity == null) return@DisposableEffect onDispose {}
        val adapter = NfcAdapter.getDefaultAdapter(activity)
            ?: return@DisposableEffect onDispose {}
        val pendingIntent = PendingIntent.getActivity(
            activity,
            NFC_REVIEW_PENDING_INTENT_REQUEST_CODE,
            Intent(activity, activity.javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE
            } else {
                0
            },
        )
        val filters = arrayOf(IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED))
        val technologies = arrayOf(arrayOf(IsoDep::class.java.name))
        var enabled = false

        fun enable() {
            if (enabled || !lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
            adapter.enableForegroundDispatch(activity, pendingIntent, filters, technologies)
            enabled = true
        }

        fun disable() {
            if (!enabled) return
            adapter.disableForegroundDispatch(activity)
            enabled = false
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> enable()
                Lifecycle.Event.ON_PAUSE -> disable()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        enable()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) disable()
        }
    }
}

private const val NFC_REVIEW_PENDING_INTENT_REQUEST_CODE = 0x4D444F43
private const val ACCESS_LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

private fun bluetoothPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_ADVERTISE,
    )
} else {
    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
}

private fun runtimePermissionsFor(action: MobileWalletProximityRemediationAction): Array<String>? = when (action) {
    MobileWalletProximityRemediationAction.RequestBluetoothPermission -> bluetoothPermissions()
    MobileWalletProximityRemediationAction.RequestNearbyWifiPermission ->
        arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES)
    MobileWalletProximityRemediationAction.RequestLocalNetworkPermission ->
        arrayOf(ACCESS_LOCAL_NETWORK_PERMISSION)
    else -> null
}

internal enum class AndroidRuntimePermissionRoute { Granted, Request, OpenSettings }

/** Distinguishes a first request from Android's otherwise identical permanent-denial state. */
internal fun androidRuntimePermissionRoute(
    permissions: List<String>,
    isGranted: (String) -> Boolean,
    wasRequested: (String) -> Boolean,
    shouldShowRationale: (String) -> Boolean,
): AndroidRuntimePermissionRoute {
    val missing = permissions.filterNot(isGranted)
    if (missing.isEmpty()) return AndroidRuntimePermissionRoute.Granted
    return if (missing.any { wasRequested(it) && !shouldShowRationale(it) }) {
        AndroidRuntimePermissionRoute.OpenSettings
    } else {
        AndroidRuntimePermissionRoute.Request
    }
}

private class AndroidPermissionRequestHistory(context: Context) {
    private val preferences: SharedPreferences = context.getSharedPreferences(
        PERMISSION_REQUEST_HISTORY_PREFERENCES,
        Context.MODE_PRIVATE,
    )

    fun wasRequested(permission: String): Boolean = preferences.getBoolean(permission, false)

    fun recordDecision(permissions: Set<String>) {
        if (permissions.isEmpty()) return
        preferences.edit().apply {
            permissions.forEach { putBoolean(it, true) }
        }.apply()
    }
}

private fun applicationSettingsIntent(context: Context): Intent = Intent(
    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
    Uri.fromParts("package", context.packageName, null),
)

private const val PERMISSION_REQUEST_HISTORY_PREFERENCES = "proximity_permission_request_history"

private suspend fun launchSystemSurface(
    intent: Intent,
    current: () -> CompletableDeferred<MobileWalletProximityHostActionResult>?,
    setCurrent: (CompletableDeferred<MobileWalletProximityHostActionResult>?) -> Unit,
    launch: (Intent) -> Unit,
): MobileWalletProximityHostActionResult {
    if (current() != null) return MobileWalletProximityHostActionResult.Failed
    val result = CompletableDeferred<MobileWalletProximityHostActionResult>()
    setCurrent(result)
    try {
        launch(intent)
    } catch (_: Throwable) {
        if (current() === result) setCurrent(null)
        return MobileWalletProximityHostActionResult.Failed
    }
    return try {
        result.await()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        MobileWalletProximityHostActionResult.Failed
    } finally {
        if (current() === result) setCurrent(null)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
