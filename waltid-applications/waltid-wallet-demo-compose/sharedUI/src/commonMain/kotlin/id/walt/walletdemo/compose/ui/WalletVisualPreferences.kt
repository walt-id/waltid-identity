package id.walt.walletdemo.compose.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/** Accessibility choices and rendering capabilities affect presentation, never consent or flow state. */
internal data class WalletVisualPreferences(
    val reduceMotion: Boolean = false,
    val opaqueControls: Boolean = false,
    val screenReaderEnabled: Boolean = false,
    val blurSupported: Boolean = true,
)

internal val LocalWalletVisualPreferences = staticCompositionLocalOf { WalletVisualPreferences() }

@Composable
internal expect fun rememberWalletVisualPreferences(): WalletVisualPreferences
