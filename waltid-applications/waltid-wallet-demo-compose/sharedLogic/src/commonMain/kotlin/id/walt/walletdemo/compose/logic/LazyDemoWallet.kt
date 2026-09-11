package id.walt.walletdemo.compose.logic

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal open class LazyDemoWallet<Wallet : DemoWallet>(
    private val createWallet: suspend () -> Wallet,
) : DemoWallet {
    private val mutex = Mutex()
    private var wallet: Wallet? = null

    protected suspend fun wallet(): Wallet =
        wallet ?: mutex.withLock {
            wallet ?: createWallet().also { wallet = it }
        }

    override suspend fun bootstrap(signingProtection: WalletDemoSigningProtection): WalletDemoBootstrapResult =
        wallet().bootstrap(signingProtection)

    override suspend fun signingProtectionAvailability(
        signingProtection: WalletDemoSigningProtection,
    ): WalletDemoSigningProtectionAvailability =
        wallet().signingProtectionAvailability(signingProtection)

    override suspend fun listCredentials(): List<WalletDemoCredential> =
        wallet().listCredentials()

    override suspend fun startIssuance(offerUrl: String, redirectUri: String, did: String?) =
        wallet().startIssuance(offerUrl, redirectUri, did)

    override suspend fun beginAuthorizationIssuance(sessionId: String) =
        wallet().beginAuthorizationIssuance(sessionId)

    override suspend fun continuePreAuthorizedIssuance(sessionId: String, transactionCode: String?) =
        wallet().continuePreAuthorizedIssuance(sessionId, transactionCode)

    override suspend fun continueAuthorizationIssuance(sessionId: String, callbackUri: String) =
        wallet().continueAuthorizationIssuance(sessionId, callbackUri)

    override suspend fun cancelIssuance(sessionId: String) = wallet().cancelIssuance(sessionId)

    override suspend fun resumeDeferredIssuance(deferredCredentialId: String) =
        wallet().resumeDeferredIssuance(deferredCredentialId)

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

    override suspend fun deleteCredential(credentialId: String): Boolean =
        wallet().deleteCredential(credentialId)

    override suspend fun deleteWallet() {
        mutex.withLock {
            wallet?.deleteWallet()
            wallet = null
        }
    }
}
