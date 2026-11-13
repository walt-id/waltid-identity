package id.walt.walletdemo.compose.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import id.walt.walletdemo.compose.logic.WalletDemoCredentialStatuses
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
)
internal val LocalCredentialStatusUi = staticCompositionLocalOf { CredentialStatusUiContext() }
