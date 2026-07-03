package id.walt.walletdemo.compose.logic

sealed interface WalletOperationState {
    data object Idle : WalletOperationState
    data object Receiving : WalletOperationState
    data object Presenting : WalletOperationState

    data class Succeeded(
        val message: String,
    ) : WalletOperationState

    data class Failed(
        val message: String,
    ) : WalletOperationState
}
