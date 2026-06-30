package id.walt.walletdemo.compose.logic

sealed interface WalletSessionState {
    data object NotBootstrapped : WalletSessionState
    data object Bootstrapping : WalletSessionState

    data class Ready(
        val did: String,
        val credentials: List<WalletDemoCredential>,
    ) : WalletSessionState

    data class Failed(
        val message: String,
    ) : WalletSessionState
}
