package id.walt.walletdemo.compose.ui

import androidx.compose.runtime.Composable
import id.walt.wallet2.mobile.MobileWalletProximityRemediationAction
import id.walt.walletdemo.compose.logic.WalletDemoProximityHostActionExecutor

internal class WalletDemoProximityHostActions(
    val executor: WalletDemoProximityHostActionExecutor,
    private val actionForDisplay: (MobileWalletProximityRemediationAction) ->
        MobileWalletProximityRemediationAction = { it },
    private val automaticallyPerform: (MobileWalletProximityRemediationAction) -> Boolean = { true },
) {
    fun displayedAction(action: MobileWalletProximityRemediationAction): MobileWalletProximityRemediationAction =
        actionForDisplay(action)

    fun mayPerformAutomatically(action: MobileWalletProximityRemediationAction): Boolean =
        automaticallyPerform(action)
}

/** Bridges the shared journey to OS-owned permission and settings surfaces. */
@Composable
internal expect fun rememberProximityHostActions(): WalletDemoProximityHostActions

/** Owns only app lifecycle, screen-awake, and QR brightness integration. */
@Composable
internal expect fun ProximityPlatformSessionEffect(
    active: Boolean,
    qrVisible: Boolean,
    onInterrupted: () -> Unit,
)
