package id.walt.wallet2.mobile.swiftinterop

import id.walt.wallet2.mobile.MobileWallet
import id.walt.wallet2.mobile.MobileWalletBootstrapResult
import id.walt.wallet2.mobile.MobileWalletCredential
import id.walt.wallet2.mobile.MobileWalletEvent
import id.walt.wallet2.mobile.MobileWalletKeyType
import id.walt.wallet2.mobile.MobileWalletIssuancePreviewHandle
import id.walt.wallet2.mobile.MobileWalletIssuanceRequest
import id.walt.wallet2.mobile.MobileWalletOfferResolution
import id.walt.wallet2.mobile.MobileWalletPresentationCredentialSelection
import id.walt.wallet2.mobile.MobileWalletPresentationDisclosureSelection
import id.walt.wallet2.mobile.MobileWalletPresentationErrorCode
import id.walt.wallet2.mobile.MobileWalletPresentationPreviewResult
import id.walt.wallet2.mobile.MobileWalletPresentationPreviewHandle
import id.walt.wallet2.mobile.MobileWalletPresentationResult
import id.walt.wallet2.handlers.WalletIssuanceOutcome
import id.walt.wallet2.handlers.WalletIssuanceSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

internal suspend fun <T> walletBridgeCall(block: suspend () -> T): WalletBridgeResult<T> =
    try {
        WalletBridgeResult.Success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (throwable: Throwable) {
        WalletBridgeResult.Failure(WalletBridgeError.fromThrowable(throwable))
    }

/**
 * iOS-facing bridge around [MobileWallet] operations.
 *
 * The bridge keeps errors and results explicit for Swift callers while preserving the small
 * cross-platform wallet API shape.
 */
public class WalletSdkBridge private constructor(
    private val operations: WalletSdkBridgeOperations,
    private val eventFlow: Flow<MobileWalletEvent>,
) {
    /**
     * Creates a bridge backed by the supplied [MobileWallet].
     */
    public constructor(wallet: MobileWallet) : this(
        operations = MobileWalletSdkBridgeOperations(wallet),
        eventFlow = wallet.events,
    )

    /**
     * Buffered stream of recent issuance and presentation events emitted by the bridged wallet.
     */
    public val events: Flow<MobileWalletEvent> = eventFlow

    /**
     * Initializes the bridged wallet and returns the persisted key and DID information.
     */
    public suspend fun bootstrap(
        keyType: MobileWalletKeyType? = null,
        didMethod: String = "key",
    ): WalletBridgeResult<MobileWalletBootstrapResult> =
        walletBridgeCall {
            operations.bootstrap(
                keyType = keyType,
                didMethod = didMethod,
            )
        }

    /** Resolves a credential offer before issuance. */
    public suspend fun resolveOffer(
        offerUrl: String,
    ): WalletBridgeResult<MobileWalletOfferResolution> =
        walletBridgeCall { operations.resolveOffer(offerUrl = offerUrl) }

    /**
     * Receives credentials from an OpenID4VCI credential offer.
     */
    public suspend fun receive(
        offerUrl: String,
        txCode: String? = null,
        clientId: String = "wallet-client",
    ): WalletBridgeResult<List<String>> =
        walletBridgeCall {
            operations.receive(
                offerUrl = offerUrl,
                txCode = txCode,
                clientId = clientId,
            )
        }

    /** Receives credentials from one reviewed issuance preview. */
    public suspend fun receivePreviewed(
        previewHandle: MobileWalletIssuancePreviewHandle,
        txCode: String? = null,
        clientId: String = "wallet-client",
    ): WalletBridgeResult<List<String>> =
        walletBridgeCall {
            operations.receivePreviewed(
                previewHandle = previewHandle,
                txCode = txCode,
                clientId = clientId,
            )
        }

    /** Discards one reviewed issuance preview locally. */
    public suspend fun discardIssuancePreview(
        previewHandle: MobileWalletIssuancePreviewHandle,
    ): WalletBridgeResult<Unit> =
        walletBridgeCall { operations.discardIssuancePreview(previewHandle) }

    /** Resolves an offer and starts its bound OpenID4VCI issuance session. */
    public suspend fun startIssuance(
        request: MobileWalletIssuanceRequest,
    ): WalletBridgeResult<WalletIssuanceSession> =
        walletBridgeCall { operations.startIssuance(request) }

    /** Continues one reviewed pre-authorized issuance session. */
    public suspend fun continuePreAuthorizedIssuance(
        sessionId: String,
        transactionCode: String? = null,
    ): WalletBridgeResult<WalletIssuanceOutcome> =
        walletBridgeCall { operations.continuePreAuthorizedIssuance(sessionId, transactionCode) }

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

    /** Resumes one deferred credential issuance result. */
    public suspend fun resumeDeferredIssuance(
        deferredCredentialId: String,
    ): WalletBridgeResult<WalletIssuanceOutcome> =
        walletBridgeCall { operations.resumeDeferredIssuance(deferredCredentialId) }

    /**
     * Lists credential summaries stored in the bridged wallet.
     */
    public suspend fun credentials(): WalletBridgeResult<List<MobileWalletCredential>> =
        walletBridgeCall {
            operations.credentials()
        }

    /**
     * Deletes wallet-local state and managed persistence material.
     */
    public suspend fun deleteWallet(): WalletBridgeResult<Unit> =
        walletBridgeCall {
            operations.deleteWallet()
        }

    /**
     * Presents matching wallet credentials to an OpenID4VP verifier request.
     */
    public suspend fun present(
        requestUrl: String,
        did: String? = null,
        runPolicies: Boolean? = null,
    ): WalletBridgeResult<MobileWalletPresentationResult> =
        walletBridgeCall {
            operations.present(
                requestUrl = requestUrl,
                did = did,
                runPolicies = runPolicies,
            )
        }

    /**
     * Resolves and previews an OpenID4VP presentation request without submitting credentials.
     */
    public suspend fun previewPresentation(
        requestUrl: String,
    ): WalletBridgeResult<MobileWalletPresentationPreviewResult> =
        walletBridgeCall {
            operations.previewPresentation(requestUrl = requestUrl)
        }

    /**
     * Submits a presentation using user-selected wallet credential options.
     */
    public suspend fun submitPresentation(
        previewHandle: MobileWalletPresentationPreviewHandle,
        selectedCredentialOptions: List<MobileWalletPresentationCredentialSelection>,
        selectedDisclosureOptions: List<MobileWalletPresentationDisclosureSelection>? = null,
        did: String? = null,
        runPolicies: Boolean? = null,
    ): WalletBridgeResult<MobileWalletPresentationResult> =
        walletBridgeCall {
            operations.submitPresentation(
                previewHandle = previewHandle,
                selectedCredentialOptions = selectedCredentialOptions,
                selectedDisclosureOptions = selectedDisclosureOptions,
                did = did,
                runPolicies = runPolicies,
            )
        }

    /** Rejects one reviewed presentation and consumes its preview. */
    public suspend fun rejectPresentation(
        previewHandle: MobileWalletPresentationPreviewHandle,
        errorCode: MobileWalletPresentationErrorCode? = null,
        errorDescription: String? = null,
    ): WalletBridgeResult<MobileWalletPresentationResult> =
        walletBridgeCall {
            operations.rejectPresentation(
                previewHandle = previewHandle,
                errorCode = errorCode,
                errorDescription = errorDescription,
            )
        }

    /** Discards one reviewed presentation locally. */
    public suspend fun discardPresentationPreview(
        previewHandle: MobileWalletPresentationPreviewHandle,
    ): WalletBridgeResult<Unit> =
        walletBridgeCall { operations.discardPresentationPreview(previewHandle) }

    internal companion object {
        internal fun forOperations(
            operations: WalletSdkBridgeOperations,
            eventFlow: Flow<MobileWalletEvent> = emptyFlow(),
        ) = WalletSdkBridge(
            operations = operations,
            eventFlow = eventFlow,
        )
    }
}

internal interface WalletSdkBridgeOperations {
    suspend fun bootstrap(
        keyType: MobileWalletKeyType?,
        didMethod: String,
    ): MobileWalletBootstrapResult

    suspend fun resolveOffer(offerUrl: String): MobileWalletOfferResolution

    suspend fun receive(
        offerUrl: String,
        txCode: String?,
        clientId: String,
    ): List<String>

    suspend fun receivePreviewed(
        previewHandle: MobileWalletIssuancePreviewHandle,
        txCode: String?,
        clientId: String,
    ): List<String>

    suspend fun discardIssuancePreview(previewHandle: MobileWalletIssuancePreviewHandle)

    suspend fun startIssuance(request: MobileWalletIssuanceRequest): WalletIssuanceSession

    suspend fun continuePreAuthorizedIssuance(
        sessionId: String,
        transactionCode: String?,
    ): WalletIssuanceOutcome

    suspend fun continueAuthorizationIssuance(
        sessionId: String,
        callbackUri: String,
    ): WalletIssuanceOutcome

    suspend fun cancelIssuance(sessionId: String): WalletIssuanceOutcome

    suspend fun resumeDeferredIssuance(deferredCredentialId: String): WalletIssuanceOutcome

    suspend fun credentials(): List<MobileWalletCredential>

    suspend fun deleteWallet()

    suspend fun present(
        requestUrl: String,
        did: String?,
        runPolicies: Boolean?,
    ): MobileWalletPresentationResult

    suspend fun previewPresentation(
        requestUrl: String,
    ): MobileWalletPresentationPreviewResult

    suspend fun submitPresentation(
        previewHandle: MobileWalletPresentationPreviewHandle,
        selectedCredentialOptions: List<MobileWalletPresentationCredentialSelection>,
        selectedDisclosureOptions: List<MobileWalletPresentationDisclosureSelection>?,
        did: String?,
        runPolicies: Boolean?,
    ): MobileWalletPresentationResult

    suspend fun rejectPresentation(
        previewHandle: MobileWalletPresentationPreviewHandle,
        errorCode: MobileWalletPresentationErrorCode?,
        errorDescription: String?,
    ): MobileWalletPresentationResult

    suspend fun discardPresentationPreview(previewHandle: MobileWalletPresentationPreviewHandle)
}

internal class MobileWalletSdkBridgeOperations(
    private val wallet: MobileWallet,
) : WalletSdkBridgeOperations {
    override suspend fun bootstrap(
        keyType: MobileWalletKeyType?,
        didMethod: String,
    ): MobileWalletBootstrapResult =
        wallet.bootstrap(
            keyType = keyType,
            didMethod = didMethod,
        )

    override suspend fun resolveOffer(offerUrl: String): MobileWalletOfferResolution =
        wallet.resolveOffer(offerUrl = offerUrl)

    override suspend fun receive(
        offerUrl: String,
        txCode: String?,
        clientId: String,
    ): List<String> =
        wallet.receive(
            offerUrl = offerUrl,
            txCode = txCode,
            clientId = clientId,
        )

    override suspend fun receivePreviewed(
        previewHandle: MobileWalletIssuancePreviewHandle,
        txCode: String?,
        clientId: String,
    ): List<String> = wallet.receive(
        previewHandle = previewHandle,
        txCode = txCode,
        clientId = clientId,
    )

    override suspend fun discardIssuancePreview(previewHandle: MobileWalletIssuancePreviewHandle) =
        wallet.discardIssuancePreview(previewHandle)

    override suspend fun startIssuance(request: MobileWalletIssuanceRequest): WalletIssuanceSession =
        wallet.startIssuance(request)

    override suspend fun continuePreAuthorizedIssuance(
        sessionId: String,
        transactionCode: String?,
    ): WalletIssuanceOutcome =
        wallet.continuePreAuthorizedIssuance(sessionId, transactionCode)

    override suspend fun continueAuthorizationIssuance(
        sessionId: String,
        callbackUri: String,
    ): WalletIssuanceOutcome =
        wallet.continueAuthorizationIssuance(sessionId, callbackUri)

    override suspend fun cancelIssuance(sessionId: String): WalletIssuanceOutcome =
        wallet.cancelIssuance(sessionId)

    override suspend fun resumeDeferredIssuance(deferredCredentialId: String): WalletIssuanceOutcome =
        wallet.resumeDeferredIssuance(deferredCredentialId)

    override suspend fun credentials(): List<MobileWalletCredential> =
        wallet.credentials()

    override suspend fun deleteWallet() =
        wallet.deleteWallet()

    override suspend fun present(
        requestUrl: String,
        did: String?,
        runPolicies: Boolean?,
    ): MobileWalletPresentationResult =
        wallet.present(
            requestUrl = requestUrl,
            did = did,
            runPolicies = runPolicies,
        )

    override suspend fun previewPresentation(
        requestUrl: String,
    ): MobileWalletPresentationPreviewResult =
        wallet.previewPresentation(requestUrl = requestUrl)

    override suspend fun submitPresentation(
        previewHandle: MobileWalletPresentationPreviewHandle,
        selectedCredentialOptions: List<MobileWalletPresentationCredentialSelection>,
        selectedDisclosureOptions: List<MobileWalletPresentationDisclosureSelection>?,
        did: String?,
        runPolicies: Boolean?,
    ): MobileWalletPresentationResult =
        wallet.submitPresentation(
            previewHandle = previewHandle,
            selectedCredentialOptions = selectedCredentialOptions,
            selectedDisclosureOptions = selectedDisclosureOptions,
            did = did,
            runPolicies = runPolicies,
        )

    override suspend fun rejectPresentation(
        previewHandle: MobileWalletPresentationPreviewHandle,
        errorCode: MobileWalletPresentationErrorCode?,
        errorDescription: String?,
    ): MobileWalletPresentationResult =
        wallet.rejectPresentation(
            previewHandle = previewHandle,
            errorCode = errorCode,
            errorDescription = errorDescription,
        )

    override suspend fun discardPresentationPreview(previewHandle: MobileWalletPresentationPreviewHandle) =
        wallet.discardPresentationPreview(previewHandle)
}
