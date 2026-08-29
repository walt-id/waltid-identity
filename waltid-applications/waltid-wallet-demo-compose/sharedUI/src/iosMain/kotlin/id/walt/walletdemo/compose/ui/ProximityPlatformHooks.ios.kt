@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package id.walt.walletdemo.compose.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import id.walt.wallet2.mobile.MobileWalletProximityHostActionResult
import id.walt.wallet2.mobile.MobileWalletProximityRemediationAction
import id.walt.walletdemo.compose.logic.WalletDemoProximityHostActionExecutor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBCentralManagerDelegateProtocol
import platform.CoreBluetooth.CBManagerAuthorizationAllowedAlways
import platform.CoreBluetooth.CBManagerAuthorizationNotDetermined
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UIKit.UIScreen
import platform.darwin.NSObject

@Composable
internal actual fun rememberProximityHostActions(): WalletDemoProximityHostActions =
    remember {
        WalletDemoProximityHostActions(
            executor = WalletDemoProximityHostActionExecutor { action ->
                withContext(Dispatchers.Main) {
                    when (action) {
                        MobileWalletProximityRemediationAction.RequestBluetoothPermission ->
                            requestBluetoothAuthorization()
                        MobileWalletProximityRemediationAction.OpenApplicationSettings,
                        MobileWalletProximityRemediationAction.EnableBluetooth -> openApplicationSettings()
                        MobileWalletProximityRemediationAction.EnableNfc ->
                            MobileWalletProximityHostActionResult.Cancelled
                        MobileWalletProximityRemediationAction.Retry ->
                            MobileWalletProximityHostActionResult.Completed
                        MobileWalletProximityRemediationAction.UseSupportedDevice ->
                            MobileWalletProximityHostActionResult.Cancelled
                    }
                }
            },
        )
    }

@Composable
internal actual fun ProximityPlatformSessionEffect(
    active: Boolean,
    qrVisible: Boolean,
    nfcReviewVisible: Boolean,
    onInterrupted: () -> Unit,
) {
    val currentOnInterrupted = rememberUpdatedState(onInterrupted)
    DisposableEffect(active) {
        if (!active) return@DisposableEffect onDispose {}
        val application = UIApplication.sharedApplication
        val previousIdleTimerDisabled = application.idleTimerDisabled
        application.idleTimerDisabled = true
        val token = NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationDidEnterBackgroundNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) {
            application.idleTimerDisabled = previousIdleTimerDisabled
            currentOnInterrupted.value()
        }
        onDispose {
            NSNotificationCenter.defaultCenter.removeObserver(token)
            application.idleTimerDisabled = previousIdleTimerDisabled
        }
    }
    DisposableEffect(qrVisible) {
        if (!qrVisible) return@DisposableEffect onDispose {}
        val screen = UIScreen.mainScreen
        val previousBrightness = screen.brightness
        screen.brightness = 1.0
        val token = NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationDidEnterBackgroundNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { screen.brightness = previousBrightness }
        onDispose {
            NSNotificationCenter.defaultCenter.removeObserver(token)
            screen.brightness = previousBrightness
        }
    }
}

private suspend fun requestBluetoothAuthorization(): MobileWalletProximityHostActionResult {
    if (CBCentralManager.authorization != CBManagerAuthorizationNotDetermined) {
        return if (CBCentralManager.authorization == CBManagerAuthorizationAllowedAlways) {
            MobileWalletProximityHostActionResult.Completed
        } else {
            MobileWalletProximityHostActionResult.Cancelled
        }
    }
    val result = CompletableDeferred<MobileWalletProximityHostActionResult>()
    val requester = BluetoothAuthorizationRequester(result)
    return try {
        requester.start()
        result.await()
    } finally {
        requester.close()
    }
}

private class BluetoothAuthorizationRequester(
    private val result: CompletableDeferred<MobileWalletProximityHostActionResult>,
) {
    private val delegate = object : NSObject(), CBCentralManagerDelegateProtocol {
        override fun centralManagerDidUpdateState(central: CBCentralManager) {
            if (CBCentralManager.authorization != CBManagerAuthorizationNotDetermined) {
                result.complete(
                    if (CBCentralManager.authorization == CBManagerAuthorizationAllowedAlways) {
                        MobileWalletProximityHostActionResult.Completed
                    } else {
                        MobileWalletProximityHostActionResult.Cancelled
                    }
                )
            }
        }
    }
    private var manager: CBCentralManager? = null

    fun start() {
        manager = CBCentralManager(delegate = delegate, queue = null, options = null)
    }

    fun close() {
        manager?.delegate = null
        manager = null
    }
}

private fun openApplicationSettings(): MobileWalletProximityHostActionResult {
    val url = NSURL(string = UIApplicationOpenSettingsURLString)
    UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any?>(), completionHandler = null)
    return MobileWalletProximityHostActionResult.Completed
}
