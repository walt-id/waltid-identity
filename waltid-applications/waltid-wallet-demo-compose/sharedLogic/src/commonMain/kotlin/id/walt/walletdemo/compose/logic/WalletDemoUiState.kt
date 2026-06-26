package id.walt.walletdemo.compose.logic

data class WalletDemoUiState(
    val pinMode: WalletDemoPinMode = WalletDemoPinMode.Setup,
    val isUnlocked: Boolean = false,
    val pin: String = "",
    val pinConfirmation: String = "",
    val pinError: String? = null,
    val isReady: Boolean = false,
    val isBusy: Boolean = false,
    val isError: Boolean = false,
    val status: String = "Set up a PIN to unlock the wallet",
    val did: String = "",
    val offerUrl: String = "",
    val presentationRequestUrl: String = "",
    val credentials: List<WalletDemoCredential> = emptyList(),
)
