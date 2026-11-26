package id.walt.wallet2.mobile.swiftinterop

import id.walt.crypto2.keys.KeyUseAuthorizationPolicy
import id.walt.crypto2.keys.KeyUseAuthorizationSupport
import id.walt.wallet2.handlers.WalletIssuanceAuthorization
import id.walt.wallet2.handlers.WalletIssuanceContinuation
import id.walt.wallet2.handlers.WalletIssuanceOfferReview
import id.walt.wallet2.handlers.WalletIssuanceOutcome
import id.walt.wallet2.mobile.Wallet
import id.walt.wallet2.mobile.credentials.CredentialStatusMaintenancePreference
import id.walt.wallet2.mobile.credentials.CredentialWithStatus
import id.walt.wallet2.mobile.credentials.WalletCredential
import id.walt.wallet2.mobile.digitalcredentials.AnnexCPreview
import id.walt.wallet2.mobile.digitalcredentials.AnnexCRequest
import id.walt.wallet2.mobile.digitalcredentials.AnnexCSubmission
import id.walt.wallet2.mobile.digitalcredentials.DigitalCredentialCapabilities
import id.walt.wallet2.mobile.digitalcredentials.DigitalCredentialResponse
import id.walt.wallet2.mobile.identity.*
import id.walt.wallet2.mobile.identity.WalletKeyType
import id.walt.wallet2.mobile.issuance.DeferredIssuanceId
import id.walt.wallet2.mobile.issuance.IssuanceAuthorizationId
import id.walt.wallet2.mobile.issuance.IssuanceClientRegistration
import id.walt.wallet2.mobile.issuance.IssuanceCredentialSelection
import id.walt.wallet2.mobile.issuance.IssuanceHolderBinding
import id.walt.wallet2.mobile.issuance.IssuanceRequest
import id.walt.wallet2.mobile.issuance.IssuanceSessionId
import id.walt.wallet2.mobile.presentation.PresentationErrorCode
import id.walt.wallet2.mobile.presentation.PresentationPreviewHandle
import id.walt.wallet2.mobile.presentation.PresentationPreviewResult
import id.walt.wallet2.mobile.presentation.PresentationResult
import id.walt.wallet2.mobile.presentation.PresentationReview
import id.walt.wallet2.mobile.presentation.PresentationSelection
import id.walt.wallet2.mobile.proximity.ProximityCapabilities
import id.walt.wallet2.mobile.proximity.ProximityConfiguration
import id.walt.wallet2.mobile.proximity.ProximitySession
import id.walt.wallet2.status.CredentialStatusRefreshMode
import id.walt.wallet2.status.CredentialStatusRefreshResult
import id.walt.wallet2.status.WalletCredentialStatusSnapshot
import kotlinx.coroutines.flow.Flow

internal class DefaultWalletSdkBridgeOperations(
    private val wallet: Wallet,
) : WalletSdkBridgeOperations {
    override suspend fun attachKeyAttestationProviderResolver(resolver: WalletBridgeKeyAttestationProviderResolver) {
        wallet.attachKeyAttestationProviderResolver(resolver.toKeyAttestationProviderResolver())
    }

    override suspend fun attachKeyAttestationProvider(provider: WalletBridgeKeyAttestationProvider) {
        wallet.attachKeyAttestationProvider(provider.toKeyAttestationProvider())
    }

    override suspend fun proximityPresentationCapabilities(
        configuration: ProximityConfiguration,
    ): ProximityCapabilities = wallet.proximityPresentationCapabilities(configuration)

    override suspend fun startProximityPresentation(
        configuration: ProximityConfiguration,
    ): ProximitySession = wallet.startProximityPresentation(configuration)
    override val signingIdentity: SigningIdentityManager get() = wallet.signingIdentity

    override fun digitalCredentialCapabilities(): DigitalCredentialCapabilities =
        wallet.digitalCredentialCapabilities()

    override suspend fun previewAnnexCPresentation(request: AnnexCRequest): AnnexCPreview =
        wallet.previewAnnexCPresentation(request)

    override suspend fun submitStatusReviewedAnnexCPresentation(submission: AnnexCSubmission, reviewedStatus: Map<String, String>): DigitalCredentialResponse = wallet.submitStatusReviewedAnnexCPresentation(submission, reviewedStatus)

    override suspend fun submitAnnexCPresentation(
        submission: AnnexCSubmission,
    ): DigitalCredentialResponse = wallet.submitAnnexCPresentation(submission)

    override suspend fun keyUseAuthorizationPreflight(
        keyType: WalletKeyType,
        policy: KeyUseAuthorizationPolicy,
    ): KeyUseAuthorizationSupport = wallet.keyUseAuthorizationPreflight(keyType, policy)

    override suspend fun createIssuanceHolderKeys(
        count: Int, keyType: WalletKeyType?, didMethod: String, policy: KeyUseAuthorizationPolicy?,
    ): List<IssuanceHolderBinding> = wallet.createIssuanceHolderKeys(count, keyType, didMethod, policy)

    override suspend fun startIssuance(request: IssuanceRequest): WalletIssuanceOfferReview =
        wallet.startIssuance(request).snapshot

    override suspend fun resumeIssuance(sessionId: String): WalletIssuanceOfferReview? =
        wallet.resumeIssuance(IssuanceSessionId(sessionId))?.snapshot

    override suspend fun beginAuthorizationIssuance(sessionId: String, registration: IssuanceClientRegistration, credentials: List<IssuanceCredentialSelection>?): WalletIssuanceAuthorization =
        wallet.beginAuthorizationIssuance(sessionId, registration, credentials)

    override suspend fun continuePreAuthorizedIssuance(
        sessionId: String,
        transactionCode: String?,
        credentials: List<IssuanceCredentialSelection>?,
        clientId: String?,
    ): WalletIssuanceOutcome =
        wallet.continuePreAuthorizedIssuance(sessionId, transactionCode, credentials, clientId)

    override suspend fun completeAuthorization(
        authorizationId: String,
        callbackUri: String,
    ): WalletIssuanceOutcome =
        wallet.completeAuthorization(IssuanceAuthorizationId(authorizationId), callbackUri)

    override suspend fun cancelIssuance(sessionId: String): WalletIssuanceOutcome =
        wallet.cancelIssuance(IssuanceSessionId(sessionId))

    override suspend fun listDeferredIssuance(): List<WalletIssuanceContinuation> = wallet.listDeferredIssuance()

    override suspend fun resumeDeferredIssuance(deferredCredentialId: String): WalletIssuanceOutcome =
        wallet.resumeDeferredIssuance(DeferredIssuanceId(deferredCredentialId))

    override suspend fun credentials(): List<WalletCredential> = wallet.credentials()
    override suspend fun credentialReads(): List<CredentialWithStatus> = wallet.credentialReads()
    override val credentialStatuses: Flow<WalletCredentialStatusSnapshot> get() = wallet.credentialStatuses
    override suspend fun credentialStatusSnapshot(): WalletCredentialStatusSnapshot = wallet.credentialStatusSnapshot()
    override suspend fun credentialStatusMaintenanceEnrollment() = wallet.credentialStatusMaintenanceEnrollment()
    override suspend fun setCredentialStatusMaintenance(preference: CredentialStatusMaintenancePreference) =
        wallet.setCredentialStatusMaintenance(preference)
    override suspend fun reconcileCredentialStatusMaintenance(automaticChecks: Boolean, resumePaused: Boolean) =
        wallet.reconcileCredentialStatusMaintenance(automaticChecks, resumePaused)
    override suspend fun invalidateCredentialStatusSnapshot() = wallet.invalidateCredentialStatusSnapshot()
    override suspend fun acknowledgeCredentialStatusNotice(credentialId: String, version: String) =
        wallet.acknowledgeCredentialStatusNotice(credentialId, version)

    override suspend fun refreshCredentialStatuses(credentialIds: List<String>?, mode: CredentialStatusRefreshMode): List<CredentialStatusRefreshResult> =
        wallet.refreshCredentialStatuses(credentialIds, mode)
    override suspend fun close(): Unit = wallet.close()

    override suspend fun deleteCredential(credentialId: String): Boolean =
        wallet.deleteCredential(credentialId)

    override suspend fun deleteWallet() =
        wallet.deleteLocalData()

    override suspend fun previewPresentation(
        requestUrl: String,
    ): PresentationPreviewResult =
        wallet.previewPresentation(requestUrl = requestUrl)

    override suspend fun retainPresentationReview(
        previewHandle: PresentationPreviewHandle, selection: PresentationSelection, did: String?,
    ): PresentationReview = wallet.reviewPresentation(previewHandle, selection, did)

    override suspend fun submitReviewedPresentation(review: PresentationReview): PresentationResult =
        wallet.submitPresentation(review)

    override suspend fun rejectPresentation(
        previewHandle: PresentationPreviewHandle,
        errorCode: PresentationErrorCode?,
        errorDescription: String?,
    ): PresentationResult =
        wallet.rejectPresentation(
            previewHandle = previewHandle,
            errorCode = errorCode,
            errorDescription = errorDescription,
        )

    override suspend fun discardPresentationPreview(previewHandle: PresentationPreviewHandle) =
        wallet.discardPresentationPreview(previewHandle)
}
