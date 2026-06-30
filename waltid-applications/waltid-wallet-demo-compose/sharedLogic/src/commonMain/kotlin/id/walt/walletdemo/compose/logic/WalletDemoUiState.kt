package id.walt.walletdemo.compose.logic

data class WalletDemoUiState(
    val auth: WalletAuthState = WalletAuthState.Setup(),
    val session: WalletSessionState = WalletSessionState.NotBootstrapped,
    val operation: WalletOperationState = WalletOperationState.Idle,
    val requestDrafts: WalletRequestDrafts = WalletRequestDrafts(),
)
