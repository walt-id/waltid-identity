package id.walt.walletdemo.compose.logic

interface DemoWallet {
    suspend fun bootstrap(): WalletDemoBootstrapResult
    suspend fun listCredentials(): List<WalletDemoCredential>
    suspend fun startIssuance(offerUrl: String, redirectUri: String, did: String?): WalletDemoIssuanceSession
    suspend fun continuePreAuthorizedIssuance(
        sessionId: String,
        transactionCode: String?,
    ): WalletDemoIssuanceOutcome
    suspend fun continueAuthorizationIssuance(
        sessionId: String,
        callbackUri: String,
    ): WalletDemoIssuanceOutcome
    suspend fun cancelIssuance(sessionId: String): WalletDemoIssuanceOutcome
    suspend fun resumeDeferredIssuance(deferredCredentialId: String): WalletDemoIssuanceOutcome
    suspend fun present(requestUrl: String, did: String? = null): WalletDemoOperationResult
    suspend fun previewPresentation(requestUrl: String): WalletDemoPresentationPreviewResult
    suspend fun submitPresentation(
        previewHandle: WalletDemoPresentationPreviewHandle,
        selectedCredentialOptions: List<WalletDemoPresentationCredentialSelection>,
        selectedDisclosureOptions: List<WalletDemoPresentationDisclosureSelection>,
        did: String? = null,
    ): WalletDemoOperationResult
    suspend fun rejectPresentation(previewHandle: WalletDemoPresentationPreviewHandle): WalletDemoOperationResult
    suspend fun discardPresentationPreview(previewHandle: WalletDemoPresentationPreviewHandle)
}
