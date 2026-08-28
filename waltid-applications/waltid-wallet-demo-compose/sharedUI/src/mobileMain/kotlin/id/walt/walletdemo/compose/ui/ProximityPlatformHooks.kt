package id.walt.walletdemo.compose.ui

import androidx.compose.runtime.Composable
import id.walt.walletdemo.compose.logic.WalletDemoProximityHostActionExecutor

/** Bridges the shared journey to OS-owned permission and settings surfaces. */
@Composable
internal expect fun rememberProximityHostActionExecutor(): WalletDemoProximityHostActionExecutor

/** Owns only app lifecycle, screen-awake, and QR brightness integration. */
@Composable
internal expect fun ProximityPlatformSessionEffect(
    active: Boolean,
    qrVisible: Boolean,
    onInterrupted: () -> Unit,
)
