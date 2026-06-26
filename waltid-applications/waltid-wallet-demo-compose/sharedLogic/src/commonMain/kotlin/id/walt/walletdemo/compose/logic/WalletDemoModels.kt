package id.walt.walletdemo.compose.logic

data class DemoWalletConfig(
    val walletId: String = "default",
    val attestationBaseUrl: String = "",
    val attestationAttesterPath: String = "",
    val attestationBearerToken: String = "",
    val attestationHostHeader: String = "",
)

data class WalletDemoBootstrapResult(
    val keyId: String,
    val did: String,
)

data class WalletDemoCredential(
    val id: String,
    val format: String,
    val issuer: String,
    val label: String,
    val addedAt: String,
)

data class WalletDemoOperationResult(
    val success: Boolean,
    val message: String,
)

enum class WalletDemoPinMode {
    Setup,
    Login,
}

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

interface DemoWallet {
    suspend fun bootstrap(): WalletDemoBootstrapResult
    suspend fun listCredentials(): List<WalletDemoCredential>
    suspend fun receive(offerUrl: String): List<String>
    suspend fun present(requestUrl: String, did: String? = null): WalletDemoOperationResult
}
