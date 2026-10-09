@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package id.walt.walletdemo.compose.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIAccessibilityDarkerSystemColorsEnabled
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled
import platform.UIKit.UIAccessibilityIsReduceTransparencyEnabled
import platform.UIKit.UIAccessibilityDarkerSystemColorsStatusDidChangeNotification
import platform.UIKit.UIAccessibilityReduceMotionStatusDidChangeNotification
import platform.UIKit.UIAccessibilityReduceTransparencyStatusDidChangeNotification
import platform.UIKit.UIAccessibilityIsVoiceOverRunning
import platform.UIKit.UIAccessibilityVoiceOverStatusDidChangeNotification

@Composable
internal actual fun rememberWalletVisualPreferences(): WalletVisualPreferences {
    fun current() = WalletVisualPreferences(
        reduceMotion = UIAccessibilityIsReduceMotionEnabled(),
        opaqueControls = UIAccessibilityIsReduceTransparencyEnabled() || UIAccessibilityDarkerSystemColorsEnabled(),
        screenReaderEnabled = UIAccessibilityIsVoiceOverRunning(),
    )
    var preferences by remember { mutableStateOf(current()) }
    DisposableEffect(Unit) {
        val center = NSNotificationCenter.defaultCenter
        val observers = listOf(UIAccessibilityReduceMotionStatusDidChangeNotification,
            UIAccessibilityReduceTransparencyStatusDidChangeNotification, UIAccessibilityDarkerSystemColorsStatusDidChangeNotification,
            UIAccessibilityVoiceOverStatusDidChangeNotification)
            .map { name -> center.addObserverForName(name, null, NSOperationQueue.mainQueue) { preferences = current() } }
        onDispose { observers.forEach(center::removeObserver) }
    }
    return preferences
}
