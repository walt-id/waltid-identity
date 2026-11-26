package id.walt.wallet2.mobile.swiftinterop

import id.walt.wallet2.handlers.WalletIssuanceAuthorization
import id.walt.wallet2.handlers.WalletIssuanceContinuation
import id.walt.wallet2.handlers.WalletIssuanceOfferReview
import id.walt.wallet2.handlers.WalletIssuanceOutcome
import id.walt.wallet2.mobile.Wallet
import id.walt.wallet2.mobile.WalletEvent
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
import id.walt.wallet2.mobile.presentation.PresentationSelection
import id.walt.wallet2.mobile.proximity.ProximityCapabilities
import id.walt.wallet2.mobile.proximity.ProximityConfiguration
import id.walt.wallet2.mobile.proximity.ProximitySession
import id.walt.wallet2.status.CredentialStatusRefreshMode
import id.walt.wallet2.status.CredentialStatusRefreshResult
import id.walt.wallet2.status.WalletCredentialStatusSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer

/**
 * iOS-facing bridge around [Wallet] operations.
 *
 * The bridge keeps errors and results explicit for Swift callers while preserving the small
 * cross-platform wallet API shape.
 */
public class WalletSdkBridge private constructor(
    private val operations: WalletSdkBridgeOperations,
    private val eventFlow: Flow<WalletEvent>,
) {
    /**
     * Creates a bridge backed by the supplied [Wallet].
     */
    public constructor(wallet: Wallet) : this(
        operations = MobileWalletSdkBridgeOperations(wallet),
        eventFlow = wallet.events,
    )

    /**
     * Buffered stream of recent issuance and presentation events emitted by the bridged wallet.
     */
    public val events: Flow<WalletEvent> = eventFlow

    /** Initial/current status values, including freshness changes, with bounded native delivery. */
    public val credentialStatuses: Flow<WalletBridgeCredentialStatusUpdate>
        get() = operations.credentialStatuses.map<WalletCredentialStatusSnapshot, WalletBridgeCredentialStatusUpdate> {
            WalletBridgeCredentialStatusUpdate.Snapshot(it.toStatusBridgeSnapshot())
        }.catch { cause ->
            if (cause is CancellationException) throw cause
            emit(WalletBridgeCredentialStatusUpdate.Failure(WalletBridgeError.fromThrowable(
                IllegalStateException("Credential status observation failed"))))
        }

    /** Local composed reads; no status endpoint is requested. */
    public suspend fun credentialReads(): WalletBridgeResult<List<WalletBridgeCredentialRead>> = walletBridgeCall {
        operations.credentialReads().map { it.toStatusBridgeRead() }
    }

    /** Reads the current opt-in on the foreground wallet. */
    public suspend fun credentialStatusMaintenanceEnrollment(): WalletBridgeResult<WalletBridgeStatusMaintenanceEnrollment> =
        walletBridgeCall { operations.credentialStatusMaintenanceEnrollment().toMaintenanceBridgeEnrollment() }

    /** Changes the managed foreground wallet's maintenance authorization. */
    public suspend fun setCredentialStatusMaintenance(preference: String): WalletBridgeResult<WalletBridgeStatusMaintenanceEnrollment> =
        walletBridgeCall {
            operations.setCredentialStatusMaintenance(CredentialStatusMaintenancePreference.valueOf(preference)).toMaintenanceBridgeEnrollment()
        }

    /** Announces another handle's committed local status. */
    public suspend fun invalidateCredentialStatusSnapshot(): WalletBridgeResult<Unit> =
        walletBridgeCall { operations.invalidateCredentialStatusSnapshot() }

    /** Reconciles the automatic-check preference with this wallet's current background grant. */
    public suspend fun reconcileCredentialStatusMaintenance(
        automaticChecks: Boolean, resumePaused: Boolean,
    ): WalletBridgeResult<WalletBridgeStatusMaintenanceEnrollment> = walletBridgeCall {
        operations.reconcileCredentialStatusMaintenance(automaticChecks, resumePaused).toMaintenanceBridgeEnrollment()
    }

    /** Acknowledges the exact notice that was displayed. */
    public suspend fun acknowledgeCredentialStatusNotice(credentialId: String, version: String): WalletBridgeResult<Unit> =
        walletBridgeCall { operations.acknowledgeCredentialStatusNotice(credentialId, version) }

    /** Explicit wallet-scoped status check, preserving typed partial outcomes. */
    public suspend fun refreshCredentialStatuses(credentialIds: List<String>? = null, force: Boolean = false): WalletBridgeResult<String> = walletBridgeCall {
        credentialStatusJson.encodeToString(ListSerializer(CredentialStatusRefreshResult.serializer()),
            operations.refreshCredentialStatuses(credentialIds, if (force) CredentialStatusRefreshMode.FORCE else CredentialStatusRefreshMode.IF_DUE))
    }

    /** Local status snapshot, independent of artwork/credential JSON normalization. */
    public suspend fun credentialStatusSnapshot(): WalletBridgeResult<String> = walletBridgeCall {
        operations.credentialStatusSnapshot().toStatusBridgeSnapshot()
    }

    /** Closes this instance and status observers without deleting persisted data. */
    public suspend fun close(): WalletBridgeResult<Unit> = walletBridgeCall { operations.close() }

    /** Attaches runtime key attestation support to this wallet instance. */
    public suspend fun attachKeyAttestationProvider(
        provider: WalletBridgeKeyAttestationProvider,
    ): WalletBridgeResult<Unit> = walletBridgeCall { operations.attachKeyAttestationProvider(provider) }

    /** Attaches issuer-specific runtime key attestation support to this wallet instance. */
    public suspend fun attachKeyAttestationProviderResolver(
        resolver: WalletBridgeKeyAttestationProviderResolver,
    ): WalletBridgeResult<Unit> = walletBridgeCall { operations.attachKeyAttestationProviderResolver(resolver) }

    /** Reopens the selected identity or creates the configured default through the shared lifecycle. */
    public suspend fun initializeSigningIdentity(): WalletBridgeResult<SigningIdentityOperationResult> =
        walletBridgeCall { operations.signingIdentity.initialize() }

    /** Reports current provider prerequisites, including unavailable integrations. */
    public suspend fun signingIdentityRecoveryProviderStatuses(): WalletBridgeResult<List<IdentityRecoveryProviderStatus>> =
        walletBridgeCall { operations.signingIdentity.recoveryProviderStatuses() }

    /** Enumerates complete SDK-owned identity choices. */
    public suspend fun signingIdentityCreationOptions(intent: SigningIdentityIntent = SigningIdentityIntent.WithoutRecovery,
        attestation: SigningIdentityAttestationRequest = SigningIdentityAttestationRequest.None): WalletBridgeResult<SigningIdentityCreationOptions> =
        walletBridgeCall { operations.signingIdentity.creationOptions(intent, attestation) }

    /** Creates the identity described by an SDK-issued option. */
    public suspend fun createSigningIdentity(option: SigningIdentityCreationOption): WalletBridgeResult<SigningIdentityOperationResult> =
        walletBridgeCall { operations.signingIdentity.create(option) }

    /** Reads the active, pending or unavailable identity state. */
    public suspend fun signingIdentityState(): WalletBridgeResult<SigningIdentityState> =
        walletBridgeCall { operations.signingIdentity.state() }

    /** Requests deletion without claiming that every synchronized copy has disappeared. */
    public suspend fun deleteSigningIdentityRecovery(candidate: SigningIdentityRecoveryCandidate): WalletBridgeResult<RecoveryReceipt> =
        walletBridgeCall { operations.signingIdentity.deleteRecovery(candidate) }

    /** Resumes an interrupted identity operation. */
    public suspend fun resumeSigningIdentity(identityId: String): WalletBridgeResult<SigningIdentityOperationResult> =
        walletBridgeCall { operations.signingIdentity.resumePending(identityId) }

    /** Cancels pending local setup without deleting an already submitted provider record. */
    public suspend fun cancelPendingSigningIdentity(identityId: String): WalletBridgeResult<Unit> =
        walletBridgeCall { operations.signingIdentity.cancelPending(identityId) }

    /** Offers backup only for an existing exportable key or retained recovery secret. */
    public suspend fun signingIdentityBackupOptions(identityId: String): WalletBridgeResult<List<SigningIdentityBackupOption>> =
        walletBridgeCall { operations.signingIdentity.backupOptions(identityId) }

    /** Submits a selected identity backup. */
    public suspend fun backupSigningIdentity(option: SigningIdentityBackupOption): WalletBridgeResult<SigningIdentityOperationResult> =
        walletBridgeCall { operations.signingIdentity.backup(option) }

    /** Lists explicitly configured private-key custodians under the current export policy. */
    public suspend fun signingIdentityCustodyOptions(identityId: String): WalletBridgeResult<List<SigningIdentityCustodyOption>> =
        walletBridgeCall { operations.signingIdentity.custodyOptions(identityId) }

    /** Imports a copy into an explicit custodian without claiming portable recovery. */
    public suspend fun copySigningIdentityToCustody(option: SigningIdentityCustodyOption): WalletBridgeResult<SigningIdentityCustodyResult> =
        walletBridgeCall { operations.signingIdentity.copyToCustody(option) }

    /** Discovers safe backup references from registered providers. */
    public suspend fun signingIdentityRecoveryDiscovery(): WalletBridgeResult<SigningIdentityRecoveryDiscovery> =
        walletBridgeCall { operations.signingIdentity.discoverRecovery() }

    /** Validates a backup and offers supported restoration destinations. */
    public suspend fun signingIdentityRestorationOptions(candidate: SigningIdentityRecoveryCandidate): WalletBridgeResult<List<SigningIdentityRestorationOption>> =
        walletBridgeCall { operations.signingIdentity.restorationOptions(candidate) }

    /** Restores the exact original signing identity. */
    public suspend fun restoreSigningIdentity(option: SigningIdentityRestorationOption): WalletBridgeResult<SigningIdentityOperationResult> =
        walletBridgeCall { operations.signingIdentity.restore(option) }

    /** Explicitly creates platform holder keys for a batch. Never called implicitly by issuance. */
    public suspend fun createIssuanceHolderKeys(
        count: Int,
        keyType: WalletKeyType? = null,
        didMethod: String = "key",
        keyUseAuthorizationPolicy: WalletBridgeKeyUseAuthorizationPolicy? = null,
    ): WalletBridgeResult<List<IssuanceHolderBinding>> = walletBridgeCall {
        operations.createIssuanceHolderKeys(count, keyType, didMethod, keyUseAuthorizationPolicy?.toCorePolicy())
    }

    /** Checks whether a key-use authorization request is supported without creating a key. */
    public suspend fun keyUseAuthorizationPreflight(
        keyType: WalletKeyType = WalletKeyType.secp256r1,
        policy: WalletBridgeKeyUseAuthorizationPolicy = WalletBridgeKeyUseAuthorizationPolicy.BiometricCurrentSet,
    ): WalletBridgeResult<WalletBridgeKeyPreflight> =
        walletBridgeCall { operations.keyUseAuthorizationPreflight(keyType, policy.toCorePolicy()).toBridgeModel() }

    /** Resolves an offer and starts its bound OpenID4VCI issuance session. */
    public suspend fun startIssuance(
        request: IssuanceRequest,
    ): WalletBridgeResult<WalletIssuanceOfferReview> =
        walletBridgeCall { operations.startIssuance(request) }

    /** Resolves a saved offer reference in this wallet's session store. */
    public suspend fun resumeIssuance(sessionId: String): WalletBridgeResult<WalletBridgeIssuanceLookup> =
        walletBridgeCall { WalletBridgeIssuanceLookup(operations.resumeIssuance(sessionId)) }

    /** Starts the authorization-code browser request for an accepted issuance session. */
    public suspend fun beginAuthorizationIssuance(
        sessionId: String,
        registration: IssuanceClientRegistration,
        credentials: List<IssuanceCredentialSelection>? = null,
    ): WalletBridgeResult<WalletIssuanceAuthorization> =
        walletBridgeCall { operations.beginAuthorizationIssuance(sessionId, registration, credentials) }

    /** Continues one reviewed pre-authorized issuance session. */
    public suspend fun continuePreAuthorizedIssuance(
        sessionId: String,
        transactionCode: String? = null,
        credentials: List<IssuanceCredentialSelection>? = null,
        clientId: String?,
    ): WalletBridgeResult<WalletIssuanceOutcome> =
        walletBridgeCall { operations.continuePreAuthorizedIssuance(sessionId, transactionCode, credentials, clientId) }

    /** Continues one authorization-code issuance session after its browser callback. */
    public suspend fun continueAuthorizationIssuance(
        sessionId: String,
        callbackUri: String,
    ): WalletBridgeResult<WalletIssuanceOutcome> =
        walletBridgeCall { operations.continueAuthorizationIssuance(sessionId, callbackUri) }

    /** Cancels one active issuance session and discards its continuation material. */
    public suspend fun cancelIssuance(
        sessionId: String,
    ): WalletBridgeResult<WalletIssuanceOutcome> =
        walletBridgeCall { operations.cancelIssuance(sessionId) }

    /** Lists retained deferred handles without polling or exposing protocol secrets. */
    public suspend fun listDeferredIssuance(): WalletBridgeResult<List<WalletIssuanceContinuation>> =
        walletBridgeCall { operations.listDeferredIssuance() }

    /** Resumes one deferred credential issuance result. */
    public suspend fun resumeDeferredIssuance(
        deferredCredentialId: String,
    ): WalletBridgeResult<WalletIssuanceOutcome> =
        walletBridgeCall { operations.resumeDeferredIssuance(deferredCredentialId) }

    /**
     * Lists credential summaries stored in the bridged wallet.
     */
    public suspend fun credentials(): WalletBridgeResult<List<WalletCredential>> =
        walletBridgeCall {
            operations.credentials()
        }

    /**
     * Removes one stored credential by wallet-local identifier.
     */
    public suspend fun deleteCredential(credentialId: String): WalletBridgeResult<Boolean> =
        walletBridgeCall {
            operations.deleteCredential(credentialId)
        }

    /**
     * Deletes wallet-local state and managed persistence material.
     */
    public suspend fun deleteWallet(): WalletBridgeResult<Unit> =
        walletBridgeCall {
            operations.deleteWallet()
        }

    /**
     * Resolves and previews an OpenID4VP presentation request without submitting credentials.
     */
    public suspend fun previewPresentation(
        requestUrl: String,
    ): WalletBridgeResult<PresentationPreviewResult> =
        walletBridgeCall {
            operations.previewPresentation(requestUrl = requestUrl)
        }

    /** Prepares exact selection data through the same typed error/cancellation bridge as submission. */
    public suspend fun reviewPresentation(
        previewHandle: PresentationPreviewHandle,
        selection: PresentationSelection,
        did: String? = null,
    ): WalletBridgeResult<WalletBridgePresentationReview> = walletBridgeCall {
        WalletBridgePresentationReview(operations.retainPresentationReview(previewHandle, selection, did))
    }

    /** Releases the exact native review supplied by this wallet. */
    public suspend fun submitReviewedPresentation(review: WalletBridgePresentationReview): WalletBridgeResult<PresentationResult> =
        walletBridgeCall { operations.submitReviewedPresentation(review.review) }

    /** Rejects one reviewed presentation and consumes its preview. */
    public suspend fun rejectPresentation(
        previewHandle: PresentationPreviewHandle,
        errorCode: PresentationErrorCode? = null,
        errorDescription: String? = null,
    ): WalletBridgeResult<PresentationResult> =
        walletBridgeCall {
            operations.rejectPresentation(
                previewHandle = previewHandle,
                errorCode = errorCode,
                errorDescription = errorDescription,
            )
        }

    /** Discards one reviewed presentation locally. */
    public suspend fun discardPresentationPreview(
        previewHandle: PresentationPreviewHandle,
    ): WalletBridgeResult<Unit> =
        walletBridgeCall { operations.discardPresentationPreview(previewHandle) }

    /** Checks proximity prerequisites without creating session material or preparing a radio. */
    public suspend fun proximityPresentationCapabilities(
        configuration: ProximityConfiguration,
    ): WalletBridgeResult<ProximityCapabilities> =
        walletBridgeCall { operations.proximityPresentationCapabilities(configuration) }

    /** Starts one single-use proximity presentation session. */
    public suspend fun startProximityPresentation(
        configuration: ProximityConfiguration,
    ): WalletBridgeResult<ProximitySession> =
        walletBridgeCall { operations.startProximityPresentation(configuration) }

    /** Returns the current iOS IdentityDocumentServices capability snapshot. */
    public fun digitalCredentialCapabilities(): DigitalCredentialCapabilities =
        operations.digitalCredentialCapabilities()

    /** Retains a parsed Annex C request for explicit native consent. */
    public suspend fun previewAnnexCPresentation(
        request: AnnexCRequest,
    ): WalletBridgeResult<AnnexCPreview> =
        walletBridgeCall { operations.previewAnnexCPresentation(request) }

    /** Verifies the post-consent raw request and builds the encrypted Annex C response. */
    public suspend fun submitStatusReviewedAnnexCPresentation(
        submission: AnnexCSubmission, reviewedStatus: Map<String, String>,
    ): WalletBridgeResult<DigitalCredentialResponse> = walletBridgeCall { operations.submitStatusReviewedAnnexCPresentation(submission, reviewedStatus) }

    /** Submits an approved Annex C response using the existing platform trust checks. */
    public suspend fun submitAnnexCPresentation(
        submission: AnnexCSubmission,
    ): WalletBridgeResult<DigitalCredentialResponse> =
        walletBridgeCall { operations.submitAnnexCPresentation(submission) }

    internal companion object {
        internal fun forOperations(
            operations: WalletSdkBridgeOperations,
            eventFlow: Flow<WalletEvent> = emptyFlow(),
        ) = WalletSdkBridge(
            operations = operations,
            eventFlow = eventFlow,
        )
    }
}

internal suspend fun <T> walletBridgeCall(block: suspend () -> T): WalletBridgeResult<T> =
    try {
        WalletBridgeResult.Success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (throwable: Throwable) {
        WalletBridgeResult.Failure(WalletBridgeError.fromThrowable(throwable))
    }

/** A saved offer that is still available for review, or null after it has been consumed. */
public class WalletBridgeIssuanceLookup internal constructor(
    /** The retained offer and its selected grant. */
    public val session: WalletIssuanceOfferReview?,
)
