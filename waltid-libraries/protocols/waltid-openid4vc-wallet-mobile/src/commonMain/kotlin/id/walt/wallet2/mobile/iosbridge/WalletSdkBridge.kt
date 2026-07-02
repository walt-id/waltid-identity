package id.walt.wallet2.mobile.iosbridge

import id.walt.crypto.keys.KeyType
import id.walt.wallet2.mobile.MobileWallet
import id.walt.wallet2.mobile.MobileWalletBootstrapResult
import id.walt.wallet2.mobile.MobileWalletCredential
import id.walt.wallet2.mobile.MobileWalletPresentationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

suspend fun <T> walletBridgeCall(block: suspend () -> T): WalletBridgeResult<T> =
    try {
        WalletBridgeResult.Success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (throwable: Throwable) {
        WalletBridgeResult.Failure(WalletBridgeError.fromThrowable(throwable))
    }

class WalletSdkBridge private constructor(
    private val operations: WalletSdkBridgeOperations,
    private val eventFlow: Flow<WalletBridgeEvent>,
) {
    constructor(wallet: MobileWallet) : this(
        operations = MobileWalletSdkBridgeOperations(wallet),
        eventFlow = emptyFlow(),
    )

    fun events(): Flow<WalletBridgeEvent> = eventFlow

    suspend fun bootstrap(
        keyType: WalletBridgeKeyType? = null,
        didMethod: String = "key",
    ): WalletBridgeResult<WalletBridgeBootstrapResult> =
        walletBridgeCall {
            operations.bootstrap(
                keyType = keyType?.toKeyType(),
                didMethod = didMethod,
            ).toWalletBridgeBootstrapResult()
        }

    suspend fun receive(
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

    suspend fun credentials(): WalletBridgeResult<List<WalletBridgeCredential>> =
        walletBridgeCall {
            operations.credentials().map { it.toWalletBridgeCredential() }
        }

    suspend fun present(
        requestUrl: String,
        did: String? = null,
        runPolicies: Boolean? = null,
    ): WalletBridgeResult<WalletBridgePresentationResult> =
        walletBridgeCall {
            operations.present(
                requestUrl = requestUrl,
                did = did,
                runPolicies = runPolicies,
            ).toWalletBridgePresentationResult()
        }

    companion object {
        internal fun forOperations(
            operations: WalletSdkBridgeOperations,
            eventFlow: Flow<WalletBridgeEvent> = emptyFlow(),
        ) = WalletSdkBridge(
            operations = operations,
            eventFlow = eventFlow,
        )
    }
}

internal interface WalletSdkBridgeOperations {
    suspend fun bootstrap(
        keyType: KeyType?,
        didMethod: String,
    ): MobileWalletBootstrapResult

    suspend fun receive(
        offerUrl: String,
        txCode: String?,
        clientId: String,
    ): List<String>

    suspend fun credentials(): List<MobileWalletCredential>

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
        keyType: KeyType?,
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
