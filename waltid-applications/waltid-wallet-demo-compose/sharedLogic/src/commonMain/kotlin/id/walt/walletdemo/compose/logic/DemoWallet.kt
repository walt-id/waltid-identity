package id.walt.walletdemo.compose.logic

interface DemoWallet {
    suspend fun bootstrap(): WalletDemoBootstrapResult
    suspend fun listCredentials(): List<WalletDemoCredential>
    suspend fun receive(offerUrl: String): List<String>
    suspend fun present(requestUrl: String, did: String? = null): WalletDemoOperationResult
    suspend fun previewPresentation(requestUrl: String): WalletDemoPresentationPreview
    suspend fun submitPresentation(
        requestUrl: String,
        selectedCredentialIds: List<String>,
        did: String? = null,
    ): WalletDemoOperationResult
}
