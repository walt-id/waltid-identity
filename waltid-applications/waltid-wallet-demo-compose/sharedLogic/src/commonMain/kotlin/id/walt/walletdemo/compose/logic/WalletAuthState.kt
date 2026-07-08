package id.walt.walletdemo.compose.logic

sealed interface WalletAuthState {
    data class Setup(
        val pin: String = "",
        val confirmation: String = "",
        val error: String? = null,
    ) : WalletAuthState

    data class Login(
        val pin: String = "",
        val error: String? = null,
    ) : WalletAuthState

    data object Unlocked : WalletAuthState
}
