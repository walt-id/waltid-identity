package id.walt.walletdemo.compose.logic

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

internal open class LazyDemoWallet<Wallet : DemoWallet>(
    private val createWallet: suspend () -> Wallet,
) : DemoWallet {
    private val mutex = Mutex()
    private var wallet: Wallet? = null

    protected suspend fun wallet(): Wallet =
        wallet ?: mutex.withLock {
            wallet ?: createWallet().also { wallet = it }
        }

    override suspend fun identityDetails(): WalletDemoIdentityDetails? = wallet().identityDetails()
    override suspend fun identitySetup(): WalletDemoIdentitySetup? = wallet().identitySetup()
    override suspend fun chooseIdentity(choiceId: String) = wallet().chooseIdentity(choiceId)
    override suspend fun cancelIdentity(identityId: String) = wallet().cancelIdentity(identityId)
    override suspend fun resumeSigningIdentity(identityId: String) = wallet().resumeSigningIdentity(identityId)

    override suspend fun bootstrap(signingProtection: WalletDemoSigningProtection): WalletDemoBootstrapResult =
        wallet().bootstrap(signingProtection)

    override suspend fun refreshDigitalCredentialRegistration() = wallet().refreshDigitalCredentialRegistration()

    override suspend fun signingProtectionAvailability(
        signingProtection: WalletDemoSigningProtection,
    ): WalletDemoSigningProtectionAvailability =
        wallet().signingProtectionAvailability(signingProtection)

    override fun credentialStatusSnapshots(): Flow<WalletDemoCredentialStatuses> = flow {
        wallet().credentialStatusSnapshots().collect { emit(it) }
    }
    override suspend fun credentialStatusSnapshot(): WalletDemoCredentialStatuses = wallet().credentialStatusSnapshot()
    override suspend fun refreshCredentialStatuses(ids: Set<String>?, force: Boolean): WalletDemoStatusRefreshSummary = wallet().refreshCredentialStatuses(ids, force)
    override suspend fun submitStatusReviewedPresentation(
        previewHandle: WalletDemoPresentationPreviewHandle,
        selectedCredentialOptions: List<WalletDemoPresentationCredentialSelection>,
        selectedDisclosureOptions: List<WalletDemoPresentationDisclosureSelection>,
        reviewedStatus: Map<String, String>, did: String?, paymentConsentRevision: String?,
        statusReviewEpoch: Long?,
    ): WalletDemoOperationResult = wallet().submitStatusReviewedPresentation(previewHandle, selectedCredentialOptions, selectedDisclosureOptions, reviewedStatus, did, paymentConsentRevision, statusReviewEpoch)

    override suspend fun listCredentials(): List<WalletDemoCredential> =
        wallet().listCredentials()

    override suspend fun startIssuance(offerUrl: String, redirectUri: String, did: String?) =
        wallet().startIssuance(offerUrl, redirectUri, did)

    override suspend fun beginAuthorizationIssuance(sessionId: String, credentials: List<WalletDemoCredentialSelection>) =
        wallet().beginAuthorizationIssuance(sessionId, credentials)


    override suspend fun listDeferredIssuance() = wallet().listDeferredIssuance()

    override suspend fun continuePreAuthorizedIssuance(sessionId: String, transactionCode: String?, credentials: List<WalletDemoCredentialSelection>) =
        wallet().continuePreAuthorizedIssuance(sessionId, transactionCode, credentials)

    override suspend fun continueAuthorizationIssuance(sessionId: String, callbackUri: String) =
        wallet().continueAuthorizationIssuance(sessionId, callbackUri)

    override suspend fun cancelIssuance(sessionId: String) = wallet().cancelIssuance(sessionId)

    override suspend fun resumeDeferredIssuance(deferredCredentialId: String) =
        wallet().resumeDeferredIssuance(deferredCredentialId)

    override suspend fun previewPresentation(requestUrl: String): WalletDemoPresentationPreviewResult =
        wallet().previewPresentation(requestUrl)

    override suspend fun preparePaymentConsent(
        previewHandle: WalletDemoPresentationPreviewHandle,
        selectedCredentialOptions: List<WalletDemoPresentationCredentialSelection>,
        selectedDisclosureOptions: List<WalletDemoPresentationDisclosureSelection>,
        did: String?,
    ): WalletDemoPaymentConsent? = wallet().preparePaymentConsent(previewHandle, selectedCredentialOptions, selectedDisclosureOptions, did)

    override suspend fun submitPresentation(
        previewHandle: WalletDemoPresentationPreviewHandle,
        selectedCredentialOptions: List<WalletDemoPresentationCredentialSelection>,
        selectedDisclosureOptions: List<WalletDemoPresentationDisclosureSelection>,
        did: String?,
        paymentConsentRevision: String?,
    ): WalletDemoOperationResult =
        wallet().submitPresentation(previewHandle, selectedCredentialOptions, selectedDisclosureOptions, did, paymentConsentRevision)

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
