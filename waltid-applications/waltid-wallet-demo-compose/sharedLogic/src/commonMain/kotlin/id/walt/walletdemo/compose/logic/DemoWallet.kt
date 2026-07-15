package id.walt.walletdemo.compose.logic

data class WalletDemoOfferResolution(
    val transactionCodeRequired: Boolean,
)

interface DemoWallet {
    suspend fun bootstrap(): WalletDemoBootstrapResult
    suspend fun listCredentials(): List<WalletDemoCredential>
    suspend fun resolveOffer(offerUrl: String): WalletDemoOfferResolution
    suspend fun receive(offerUrl: String, txCode: String? = null): List<String>
    suspend fun present(requestUrl: String, did: String? = null): WalletDemoOperationResult
    suspend fun previewPresentation(requestUrl: String): WalletDemoPresentationPreview
    suspend fun submitPresentation(
        requestUrl: String,
        selectedCredentialOptions: List<WalletDemoPresentationCredentialSelection>,
        selectedDisclosureOptions: List<WalletDemoPresentationDisclosureSelection>,
        did: String? = null,
    ): WalletDemoOperationResult
}
