package id.walt.walletdemo.compose.logic

interface DemoWallet {
    suspend fun bootstrap(): WalletDemoBootstrapResult
    suspend fun listCredentials(): List<WalletDemoCredential>
    suspend fun resolveOffer(offerUrl: String): DemoOfferResolution
    suspend fun receive(offerUrl: String, txCode: String? = null): List<String>
    suspend fun present(requestUrl: String, did: String? = null): WalletDemoOperationResult
}

data class DemoOfferResolution(
    val txCodeRequired: Boolean,
)
