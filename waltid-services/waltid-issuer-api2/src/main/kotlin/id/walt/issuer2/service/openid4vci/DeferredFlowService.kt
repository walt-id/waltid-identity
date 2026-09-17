package id.walt.issuer2.service.openid4vci

import id.walt.openid4vci.responses.credential.CredentialResponseHttp
import kotlinx.serialization.json.JsonObject
import kotlin.time.Clock

class DeferredFlowService(
    private val store: DeferredCredentialTransactionStore = InMemoryDeferredCredentialTransactionStore(),
) {
    suspend fun register(
        sessionId: String,
        transactionId: String = java.util.UUID.randomUUID().toString(),
        intervalSeconds: Long = 30L,
        response: CredentialResponseHttp? = null,
        requestParameters: JsonObject = JsonObject(emptyMap()),
        dpopProofHeaderValues: List<String> = emptyList(),
        requestId: String = "",
        createdAtEpochSeconds: Long = Clock.System.now().epochSeconds,
    ): DeferredCredentialTransaction = store.save(
        DeferredCredentialTransaction(
            transactionId = transactionId,
            sessionId = sessionId,
            intervalSeconds = intervalSeconds,
            status = DeferredCredentialTransactionStatus.PENDING,
            response = response,
            requestParameters = requestParameters,
            dpopProofHeaderValues = dpopProofHeaderValues,
            requestId = requestId,
            createdAtEpochSeconds = createdAtEpochSeconds,
        ),
    )

    suspend fun get(transactionId: String): DeferredCredentialTransaction? = store.get(transactionId)

    suspend fun isReady(
        transaction: DeferredCredentialTransaction,
        nowEpochSeconds: Long = Clock.System.now().epochSeconds,
    ): Boolean = transaction.response != null || nowEpochSeconds - transaction.createdAtEpochSeconds >= transaction.intervalSeconds

    suspend fun markReady(
        transactionId: String,
        nowEpochSeconds: Long = Clock.System.now().epochSeconds,
    ): DeferredCredentialTransaction? {
        val transaction = store.get(transactionId) ?: return null
        if (transaction.status == DeferredCredentialTransactionStatus.CONSUMED ||
            transaction.status == DeferredCredentialTransactionStatus.EXPIRED
        ) {
            return null
        }
        if (!isReady(transaction, nowEpochSeconds)) {
            return transaction
        }
        return store.update(transactionId) { it.copy(status = DeferredCredentialTransactionStatus.READY) }
    }

    suspend fun consume(transactionId: String): DeferredCredentialTransaction? = store.consume(transactionId)

    suspend fun remove(transactionId: String): DeferredCredentialTransaction? = store.remove(transactionId)
}
