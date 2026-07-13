package id.walt.wallet2.mobile.swiftinterop

import id.walt.wallet2.mobile.MobileWallet
import id.walt.wallet2.mobile.MobileWalletBootstrapResult
import id.walt.wallet2.mobile.MobileWalletCredential
import id.walt.wallet2.mobile.MobileWalletEvent
import id.walt.wallet2.mobile.MobileWalletKeyType
import id.walt.wallet2.mobile.MobileWalletPresentationResult
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
}
