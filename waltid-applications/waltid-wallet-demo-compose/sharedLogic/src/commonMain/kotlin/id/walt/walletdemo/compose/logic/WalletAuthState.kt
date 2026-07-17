package id.walt.walletdemo.compose.logic

sealed interface WalletAuthState {
    sealed interface PinEntry : WalletAuthState

    data class Setup(
        val pin: String = "",
        val confirmation: String = "",
        val error: String? = null,
    ) : PinEntry

    data class Login(
        val pin: String = "",
        val error: String? = null,
    ) : PinEntry

    data class StorageUnavailable(
        val message: String = "PIN storage is unavailable",
    ) : WalletAuthState

    data object Unlocked : WalletAuthState
}
