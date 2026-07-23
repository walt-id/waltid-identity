package id.walt.walletdemo.compose.logic

interface DemoWallet {
    suspend fun bootstrap(): WalletDemoBootstrapResult
    suspend fun listCredentials(): List<WalletDemoCredential>
    suspend fun resolveOffer(offerUrl: String): WalletDemoOfferPreview
    suspend fun receive(previewHandle: WalletDemoIssuancePreviewHandle, txCode: String? = null): List<String>
    suspend fun discardIssuancePreview(previewHandle: WalletDemoIssuancePreviewHandle)
    suspend fun startIssuance(offerUrl: String, redirectUri: String, did: String?): WalletDemoIssuanceSession =
        error("Issuance sessions are unavailable")
    suspend fun continuePreAuthorizedIssuance(
        sessionId: String,
        transactionCode: String?,
    ): WalletDemoIssuanceOutcome = WalletDemoIssuanceOutcome.Failed("Issuance sessions are unavailable")
    suspend fun continueAuthorizationIssuance(
        sessionId: String,
        callbackUri: String,
    ): WalletDemoIssuanceOutcome = WalletDemoIssuanceOutcome.Failed("Issuance sessions are unavailable")
    suspend fun cancelIssuance(sessionId: String): WalletDemoIssuanceOutcome = WalletDemoIssuanceOutcome.Cancelled
    suspend fun resumeDeferredIssuance(deferredCredentialId: String): WalletDemoIssuanceOutcome =
        WalletDemoIssuanceOutcome.Failed("Deferred issuance is unavailable")
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
