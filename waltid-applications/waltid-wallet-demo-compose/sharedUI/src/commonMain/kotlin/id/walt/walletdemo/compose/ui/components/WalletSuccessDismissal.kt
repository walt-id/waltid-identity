package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalAccessibilityManager
import id.walt.walletdemo.compose.ui.LocalWalletVisualPreferences
import id.walt.walletdemo.compose.ui.rememberScannerHostActive
import kotlinx.coroutines.delay

/** Only idle, foreground, completed success may leave automatically. Touching it keeps it open. */
@Composable
internal fun rememberSuccessDismissal(
    key: Any, enabled: Boolean, onDone: () -> Unit, active: Boolean = rememberScannerHostActive(),
): Modifier {
    var interacted by remember(key) { mutableStateOf(false) }
    val screenReader = LocalWalletVisualPreferences.current.screenReaderEnabled
    val latestDone by rememberUpdatedState(onDone)
    val timeout = LocalAccessibilityManager.current?.calculateRecommendedTimeoutMillis(
        5_000, containsIcons = true, containsText = true, containsControls = true,
    )?.coerceAtLeast(5_000) ?: 5_000
    LaunchedEffect(key, enabled, active, interacted, screenReader, timeout) {
        if (enabled && active && !interacted && !screenReader) {
            delay(timeout)
            latestDone()
        }
    }
    return Modifier.pointerInput(key) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            interacted = true
        }
    }
}
