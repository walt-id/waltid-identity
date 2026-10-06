package id.walt.walletdemo.compose.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/** Platform accessibility choices affect presentation, never the wallet's consent or flow state. */
internal data class WalletVisualPreferences(val reduceMotion: Boolean = false, val opaqueControls: Boolean = false)

internal val LocalWalletVisualPreferences = staticCompositionLocalOf { WalletVisualPreferences() }

@Composable
internal expect fun rememberWalletVisualPreferences(): WalletVisualPreferences
