@file:OptIn(ExperimentalForeignApi::class)

package id.walt.walletdemo.compose.ui

import androidx.compose.runtime.*
import id.walt.walletdemo.compose.logic.WalletDateTime
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.*
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import kotlin.time.Instant

@Composable
internal actual fun rememberWalletTimeFormatter(): (Instant) -> String {
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        val center = NSNotificationCenter.defaultCenter
        val observers = listOf(NSSystemTimeZoneDidChangeNotification, NSCurrentLocaleDidChangeNotification, UIApplicationDidBecomeActiveNotification).map { name ->
            center.addObserverForName(name, null, NSOperationQueue.mainQueue) { revision++ }
        }
        onDispose { observers.forEach(center::removeObserver) }
    }
    return remember(revision) { { instant: Instant -> WalletDateTime.format(instant) } }
}
