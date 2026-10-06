@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package id.walt.walletdemo.compose.ui

import androidx.compose.runtime.*
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationState
import platform.UIKit.UIApplicationWillResignActiveNotification

@Composable
internal actual fun rememberScannerHostActive(): Boolean {
    var active by remember { mutableStateOf(UIApplication.sharedApplication.applicationState == UIApplicationState.UIApplicationStateActive) }
    DisposableEffect(Unit) {
        val notifications = NSNotificationCenter.defaultCenter
        val activation = notifications.addObserverForName(
            UIApplicationDidBecomeActiveNotification, null, NSOperationQueue.mainQueue,
        ) { active = true }
        val deactivation = notifications.addObserverForName(
            UIApplicationWillResignActiveNotification, null, NSOperationQueue.mainQueue,
        ) { active = false }
        onDispose {
            notifications.removeObserver(activation)
            notifications.removeObserver(deactivation)
        }
    }
    return active
}
