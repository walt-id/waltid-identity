package id.walt.wallet2.mobile.swiftinterop

import id.walt.crypto2.keys.KeyUseAuthorizationPolicy
import id.walt.crypto2.keys.KeyUseAuthorizationSupport
import id.walt.wallet2.handlers.WalletIssuanceAuthorization
import id.walt.wallet2.handlers.WalletIssuanceContinuation
import id.walt.wallet2.handlers.WalletIssuanceOfferReview
import id.walt.wallet2.handlers.WalletIssuanceOutcome
import id.walt.wallet2.mobile.credentials.CredentialWithStatus
import id.walt.wallet2.mobile.credentials.WalletCredential
import id.walt.wallet2.mobile.digitalcredentials.AnnexCPreview
import id.walt.wallet2.mobile.digitalcredentials.AnnexCRequest
import id.walt.wallet2.mobile.digitalcredentials.AnnexCSubmission
import id.walt.wallet2.mobile.digitalcredentials.DigitalCredentialCapabilities
import id.walt.wallet2.mobile.digitalcredentials.DigitalCredentialResponse
import id.walt.wallet2.mobile.identity.*
import id.walt.wallet2.mobile.identity.WalletKeyType
import id.walt.wallet2.mobile.issuance.IssuanceClientRegistration
import id.walt.wallet2.mobile.issuance.IssuanceCredentialSelection
import id.walt.wallet2.mobile.issuance.IssuanceHolderBinding
import id.walt.wallet2.mobile.issuance.IssuanceRequest
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

internal interface WalletSdkBridgeOperations {
    suspend fun attachKeyAttestationProviderResolver(resolver: WalletBridgeKeyAttestationProviderResolver): Unit =
        error("Key attestation resolution is not implemented by this test bridge")

    suspend fun attachKeyAttestationProvider(provider: WalletBridgeKeyAttestationProvider): Unit =
        error("Key attestation is not implemented by this test bridge")

    suspend fun proximityPresentationCapabilities(
        configuration: ProximityConfiguration,
    ): ProximityCapabilities = error("Proximity presentation is not implemented by this test bridge")

    suspend fun startProximityPresentation(
        configuration: ProximityConfiguration,
    ): ProximitySession = error("Proximity presentation is not implemented by this test bridge")

    val signingIdentity: SigningIdentityManager get() = error("Identity lifecycle is not supplied by this test double")
    fun digitalCredentialCapabilities(): DigitalCredentialCapabilities =
        error("Digital Credentials are not implemented by this test bridge")

    suspend fun previewAnnexCPresentation(request: AnnexCRequest): AnnexCPreview =
        error("Annex C is not implemented by this test bridge")

    suspend fun submitStatusReviewedAnnexCPresentation(submission: AnnexCSubmission, reviewedStatus: Map<String, String>): DigitalCredentialResponse = error("Annex C status review is unavailable")

    suspend fun submitAnnexCPresentation(
        submission: AnnexCSubmission,
    ): DigitalCredentialResponse = error("Annex C is not implemented by this test bridge")

    suspend fun keyUseAuthorizationPreflight(
        keyType: WalletKeyType,
        policy: KeyUseAuthorizationPolicy,
    ): KeyUseAuthorizationSupport

    suspend fun createIssuanceHolderKeys(
        count: Int, keyType: WalletKeyType?, didMethod: String, policy: KeyUseAuthorizationPolicy?,
    ): List<IssuanceHolderBinding>

    suspend fun startIssuance(request: IssuanceRequest): WalletIssuanceOfferReview
    suspend fun resumeIssuance(sessionId: String): WalletIssuanceOfferReview?

    suspend fun beginAuthorizationIssuance(sessionId: String, registration: IssuanceClientRegistration, credentials: List<IssuanceCredentialSelection>?): WalletIssuanceAuthorization

    suspend fun continuePreAuthorizedIssuance(
        sessionId: String,
        transactionCode: String?,
        credentials: List<IssuanceCredentialSelection>?,
        clientId: String?,
    ): WalletIssuanceOutcome

    suspend fun completeAuthorization(
        authorizationId: String,
        callbackUri: String,
    ): WalletIssuanceOutcome

    suspend fun cancelIssuance(sessionId: String): WalletIssuanceOutcome

    suspend fun listDeferredIssuance(): List<WalletIssuanceContinuation>

    suspend fun resumeDeferredIssuance(deferredCredentialId: String): WalletIssuanceOutcome

    suspend fun credentials(): List<WalletCredential>
    suspend fun credentialReads(): List<CredentialWithStatus>
    val credentialStatuses: Flow<WalletCredentialStatusSnapshot>
    suspend fun credentialStatusSnapshot(): WalletCredentialStatusSnapshot
    suspend fun refreshCredentialStatuses(credentialIds: List<String>?, mode: CredentialStatusRefreshMode): List<CredentialStatusRefreshResult>
    suspend fun close()

    suspend fun deleteCredential(credentialId: String): Boolean

    suspend fun deleteWallet()

    suspend fun previewPresentation(
        requestUrl: String,
    ): PresentationPreviewResult

    suspend fun retainPresentationReview(
        previewHandle: PresentationPreviewHandle, selection: PresentationSelection, did: String?,
    ): PresentationReview

    suspend fun submitReviewedPresentation(review: PresentationReview): PresentationResult

    suspend fun rejectPresentation(
        previewHandle: PresentationPreviewHandle,
        errorCode: PresentationErrorCode?,
        errorDescription: String?,
    ): PresentationResult

    suspend fun discardPresentationPreview(previewHandle: PresentationPreviewHandle)
}
