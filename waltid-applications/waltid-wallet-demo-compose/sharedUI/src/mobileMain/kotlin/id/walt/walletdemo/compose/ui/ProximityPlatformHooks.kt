package id.walt.walletdemo.compose.ui

import androidx.compose.runtime.Composable
import id.walt.wallet2.mobile.ProximityRemediationAction
import id.walt.walletdemo.compose.logic.WalletDemoProximityHostActionExecutor

internal class WalletDemoProximityHostActions(
    val executor: WalletDemoProximityHostActionExecutor,
    private val actionForDisplay: (ProximityRemediationAction) ->
        ProximityRemediationAction = { it },
    private val automaticallyPerform: (ProximityRemediationAction) -> Boolean = { true },
) {
    fun displayedAction(action: ProximityRemediationAction): ProximityRemediationAction =
        actionForDisplay(action)

    fun mayPerformAutomatically(action: ProximityRemediationAction): Boolean =
        automaticallyPerform(action)
}

/** Bridges the shared journey to OS-owned permission and settings surfaces. */
@Composable
internal expect fun rememberProximityHostActions(): WalletDemoProximityHostActions

/** Owns only app lifecycle, screen-awake, QR brightness, and platform NFC dispatch integration. */
@Composable
internal expect fun ProximityPlatformSessionEffect(
    active: Boolean,
    qrVisible: Boolean,
    nfcReviewVisible: Boolean,
    onInterrupted: () -> Unit,
)
