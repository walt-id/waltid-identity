package id.walt.walletdemo.compose.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import id.walt.walletdemo.compose.logic.WalletDemoCredentialStatuses
import id.walt.walletdemo.compose.logic.WalletDemoPresentationPreviewHandle
import id.walt.walletdemo.compose.logic.WalletDemoStatusReadState
import id.walt.walletdemo.compose.logic.WalletDemoStatusRefreshSummary

internal data class CredentialStatusUiContext(
    val snapshot: WalletDemoCredentialStatuses = WalletDemoCredentialStatuses(readState = WalletDemoStatusReadState.Loading),
    val checking: Boolean = false,
    val check: ((String) -> Unit)? = null,
    val automatic: Boolean = true,
    val setAutomatic: ((Boolean) -> Unit)? = null,
    val checkAll: (() -> Unit)? = null,
    val summary: WalletDemoStatusRefreshSummary? = null,
    val error: String? = null,
    val contextKey: Any? = null,
    val reviewRefresh: PresentationStatusRefreshActions? = null,
)

/** All operations belong to one online host; offline/provider contexts omit this capability. */
internal class PresentationStatusRefreshActions(
    val check: (String, WalletDemoPresentationPreviewHandle, Int) -> Unit,
    val rendered: (Long) -> Unit,
    val release: (WalletDemoPresentationPreviewHandle, Int) -> Unit,
)
internal val LocalCredentialStatusUi = staticCompositionLocalOf { CredentialStatusUiContext() }
