package id.walt.walletdemo.compose.logic

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class LazyDemoWallet(
    private val createWallet: suspend () -> DemoWallet,
) : DemoWallet {
    private val mutex = Mutex()
    private var wallet: DemoWallet? = null

    private suspend fun wallet(): DemoWallet =
        wallet ?: mutex.withLock {
            wallet ?: createWallet().also { wallet = it }
        }

    override suspend fun bootstrap(): WalletDemoBootstrapResult =
        wallet().bootstrap()

    override suspend fun listCredentials(): List<WalletDemoCredential> =
        wallet().listCredentials()

    override suspend fun resolveOffer(offerUrl: String): WalletDemoOfferPreview =
        wallet().resolveOffer(offerUrl)

    override suspend fun receive(
        previewHandle: WalletDemoIssuancePreviewHandle,
        txCode: String?,
    ): List<String> = wallet().receive(previewHandle, txCode)

    override suspend fun discardIssuancePreview(previewHandle: WalletDemoIssuancePreviewHandle) =
        wallet().discardIssuancePreview(previewHandle)

    override suspend fun startIssuance(offerUrl: String, redirectUri: String, did: String?) =
        wallet().startIssuance(offerUrl, redirectUri, did)

    override suspend fun continuePreAuthorizedIssuance(sessionId: String, transactionCode: String?) =
        wallet().continuePreAuthorizedIssuance(sessionId, transactionCode)

    override suspend fun continueAuthorizationIssuance(sessionId: String, callbackUri: String) =
        wallet().continueAuthorizationIssuance(sessionId, callbackUri)

    override suspend fun cancelIssuance(sessionId: String) = wallet().cancelIssuance(sessionId)

    override suspend fun present(requestUrl: String, did: String?): WalletDemoOperationResult =
        wallet().present(requestUrl, did)

    override suspend fun previewPresentation(requestUrl: String): WalletDemoPresentationPreviewResult =
        wallet().previewPresentation(requestUrl)

    override suspend fun submitPresentation(
        previewHandle: WalletDemoPresentationPreviewHandle,
        selectedCredentialOptions: List<WalletDemoPresentationCredentialSelection>,
        selectedDisclosureOptions: List<WalletDemoPresentationDisclosureSelection>,
        did: String?,
    ): WalletDemoOperationResult =
        wallet().submitPresentation(previewHandle, selectedCredentialOptions, selectedDisclosureOptions, did)

    override suspend fun rejectPresentation(previewHandle: WalletDemoPresentationPreviewHandle) =
        wallet().rejectPresentation(previewHandle)

    override suspend fun discardPresentationPreview(previewHandle: WalletDemoPresentationPreviewHandle) =
        wallet().discardPresentationPreview(previewHandle)
}
