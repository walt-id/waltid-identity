package id.walt.walletdemo.compose.ui

import android.app.UiModeManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun rememberWalletVisualPreferences(): WalletVisualPreferences {
    val motion = rememberCoroutineScope().coroutineContext[MotionDurationScale]
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(UiModeManager::class.java) }
    var contrast by remember(manager) { mutableStateOf(if (Build.VERSION.SDK_INT >= 34) manager?.contrast ?: 0f else 0f) }
    DisposableEffect(manager, context) {
        if (Build.VERSION.SDK_INT >= 34 && manager != null) {
            val listener = UiModeManager.ContrastChangeListener { contrast = it }
            manager.addContrastChangeListener(context.mainExecutor, listener)
            onDispose { manager.removeContrastChangeListener(listener) }
        } else onDispose {}
    }
    return WalletVisualPreferences(reduceMotion = motion?.scaleFactor == 0f, opaqueControls = contrast >= .5f)
}
