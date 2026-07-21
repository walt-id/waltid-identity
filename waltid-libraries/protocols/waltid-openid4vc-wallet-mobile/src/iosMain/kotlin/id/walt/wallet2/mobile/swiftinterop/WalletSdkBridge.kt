package id.walt.wallet2.mobile.swiftinterop

import id.walt.wallet2.mobile.MobileWallet
import id.walt.wallet2.mobile.MobileWalletBootstrapResult
import id.walt.wallet2.mobile.MobileWalletAnnexCPreview
import id.walt.wallet2.mobile.MobileWalletAnnexCRequest
import id.walt.wallet2.mobile.MobileWalletAnnexCSubmission
import id.walt.wallet2.mobile.MobileWalletCredential
import id.walt.wallet2.mobile.MobileWalletEvent
import id.walt.wallet2.mobile.MobileWalletKeyType
import id.walt.wallet2.mobile.MobileWalletOfferResolution
import id.walt.wallet2.mobile.MobileWalletPresentationCredentialSelection
import id.walt.wallet2.mobile.MobileWalletPresentationDisclosureSelection
import id.walt.wallet2.mobile.MobileWalletPresentationErrorCode
import id.walt.wallet2.mobile.MobileWalletPresentationPreviewResult
import id.walt.wallet2.mobile.MobileWalletPresentationResult
import id.walt.wallet2.mobile.MobileWalletDigitalCredentialCapabilities
import id.walt.wallet2.mobile.MobileWalletDigitalCredentialResponse
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
        requestUrl: String,
        selectedCredentialOptions: List<MobileWalletPresentationCredentialSelection>,
        selectedDisclosureOptions: List<MobileWalletPresentationDisclosureSelection>? = null,
        did: String? = null,
        runPolicies: Boolean? = null,
    ): WalletBridgeResult<MobileWalletPresentationResult> =
        walletBridgeCall {
            operations.submitPresentation(
                requestUrl = requestUrl,
                selectedCredentialOptions = selectedCredentialOptions,
                selectedDisclosureOptions = selectedDisclosureOptions,
                did = did,
                runPolicies = runPolicies,
            )
        }

    /** Sends an OpenID4VP error response for a previously previewed request. */
    public suspend fun rejectPresentation(
        requestUrl: String,
        errorCode: MobileWalletPresentationErrorCode? = null,
        errorDescription: String? = null,
    ): WalletBridgeResult<MobileWalletPresentationResult> =
        walletBridgeCall {
            operations.rejectPresentation(
                requestUrl = requestUrl,
                errorCode = errorCode,
                errorDescription = errorDescription,
            )
        }

    /** Returns the current iOS IdentityDocumentServices capability snapshot. */
    public fun digitalCredentialCapabilities(): MobileWalletDigitalCredentialCapabilities =
        operations.digitalCredentialCapabilities()

    /** Retains a parsed Annex C request for explicit native consent. */
    public suspend fun previewAnnexCPresentation(
        request: MobileWalletAnnexCRequest,
    ): WalletBridgeResult<MobileWalletAnnexCPreview> =
        walletBridgeCall { operations.previewAnnexCPresentation(request) }

    /** Verifies the post-consent raw request and builds the encrypted Annex C response. */
    public suspend fun submitAnnexCPresentation(
        submission: MobileWalletAnnexCSubmission,
    ): WalletBridgeResult<MobileWalletDigitalCredentialResponse> =
        walletBridgeCall { operations.submitAnnexCPresentation(submission) }

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
    fun digitalCredentialCapabilities(): MobileWalletDigitalCredentialCapabilities =
        error("Digital Credentials are not implemented by this test bridge")

    suspend fun previewAnnexCPresentation(request: MobileWalletAnnexCRequest): MobileWalletAnnexCPreview =
        error("Annex C is not implemented by this test bridge")

    suspend fun submitAnnexCPresentation(
        submission: MobileWalletAnnexCSubmission,
    ): MobileWalletDigitalCredentialResponse = error("Annex C is not implemented by this test bridge")

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
        requestUrl: String,
        selectedCredentialOptions: List<MobileWalletPresentationCredentialSelection>,
        selectedDisclosureOptions: List<MobileWalletPresentationDisclosureSelection>?,
        did: String?,
        runPolicies: Boolean?,
    ): MobileWalletPresentationResult

    suspend fun rejectPresentation(
        requestUrl: String,
        errorCode: MobileWalletPresentationErrorCode?,
        errorDescription: String?,
    ): MobileWalletPresentationResult
}

internal class MobileWalletSdkBridgeOperations(
    private val wallet: MobileWallet,
) : WalletSdkBridgeOperations {
    override fun digitalCredentialCapabilities(): MobileWalletDigitalCredentialCapabilities =
        wallet.digitalCredentialCapabilities()

    override suspend fun previewAnnexCPresentation(request: MobileWalletAnnexCRequest): MobileWalletAnnexCPreview =
        wallet.previewAnnexCPresentation(request)

    override suspend fun submitAnnexCPresentation(
        submission: MobileWalletAnnexCSubmission,
    ): MobileWalletDigitalCredentialResponse = wallet.submitAnnexCPresentation(submission)

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
        requestUrl: String,
        selectedCredentialOptions: List<MobileWalletPresentationCredentialSelection>,
        selectedDisclosureOptions: List<MobileWalletPresentationDisclosureSelection>?,
        did: String?,
        runPolicies: Boolean?,
    ): MobileWalletPresentationResult =
        wallet.submitPresentation(
            requestUrl = requestUrl,
            selectedCredentialOptions = selectedCredentialOptions,
            selectedDisclosureOptions = selectedDisclosureOptions,
            did = did,
            runPolicies = runPolicies,
        )

    override suspend fun rejectPresentation(
        requestUrl: String,
        errorCode: MobileWalletPresentationErrorCode?,
        errorDescription: String?,
    ): MobileWalletPresentationResult =
        wallet.rejectPresentation(
            requestUrl = requestUrl,
            errorCode = errorCode,
            errorDescription = errorDescription,
        )
}
