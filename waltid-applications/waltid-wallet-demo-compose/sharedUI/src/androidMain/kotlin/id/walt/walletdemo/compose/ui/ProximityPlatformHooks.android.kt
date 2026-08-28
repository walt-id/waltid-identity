package id.walt.walletdemo.compose.ui

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
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
internal actual fun rememberProximityHostActionExecutor(): WalletDemoProximityHostActionExecutor {
    val context = LocalContext.current
    var permissionRequest by remember {
        mutableStateOf<CompletableDeferred<MobileWalletProximityHostActionResult>?>(null)
    }
    var systemSurface by remember {
        mutableStateOf<CompletableDeferred<MobileWalletProximityHostActionResult>?>(null)
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
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

    return remember(context, permissionLauncher, systemSurfaceLauncher) {
        WalletDemoProximityHostActionExecutor { action ->
            when (action) {
                MobileWalletProximityRemediationAction.RequestBluetoothPermission -> {
                    val permissions = bluetoothPermissions()
                    if (permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) {
                        MobileWalletProximityHostActionResult.Completed
                    } else if (permissionRequest != null) {
                        MobileWalletProximityHostActionResult.Failed
                    } else {
                        CompletableDeferred<MobileWalletProximityHostActionResult>().let { result ->
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
                }
                MobileWalletProximityRemediationAction.OpenApplicationSettings ->
                    launchSystemSurface(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        ),
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
                MobileWalletProximityRemediationAction.Retry ->
                    MobileWalletProximityHostActionResult.Completed
                MobileWalletProximityRemediationAction.UseSupportedDevice ->
                    MobileWalletProximityHostActionResult.Cancelled
            }
        }
    }
}

@Composable
internal actual fun ProximityPlatformSessionEffect(
    active: Boolean,
    qrVisible: Boolean,
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
}

private fun bluetoothPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_ADVERTISE,
    )
} else {
    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
}

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
