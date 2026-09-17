package id.walt.issuer2.service.openid4vci

import id.walt.openid4vci.responses.credential.CredentialResponseHttp
import kotlinx.serialization.json.JsonObject
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock

enum class DeferredCredentialTransactionStatus {
    PENDING,
    READY,
    CONSUMED,
    EXPIRED,
}

data class DeferredCredentialTransaction(
    val transactionId: String,
    val sessionId: String,
    val intervalSeconds: Long = 30L,
    val status: DeferredCredentialTransactionStatus = DeferredCredentialTransactionStatus.PENDING,
    val response: CredentialResponseHttp? = null,
    val requestParameters: JsonObject = JsonObject(emptyMap()),
    val dpopProofHeaderValues: List<String> = emptyList(),
    val requestId: String = "",
    val createdAtEpochSeconds: Long = Clock.System.now().epochSeconds,
)

interface DeferredCredentialTransactionStore {
    suspend fun save(transaction: DeferredCredentialTransaction): DeferredCredentialTransaction
    suspend fun get(transactionId: String): DeferredCredentialTransaction?
    suspend fun update(
        transactionId: String,
        updater: (DeferredCredentialTransaction) -> DeferredCredentialTransaction,
    ): DeferredCredentialTransaction?
    suspend fun remove(transactionId: String): DeferredCredentialTransaction?
    suspend fun consume(transactionId: String): DeferredCredentialTransaction?
}

class InMemoryDeferredCredentialTransactionStore : DeferredCredentialTransactionStore {
    private val transactions = ConcurrentHashMap<String, DeferredCredentialTransaction>()

    override suspend fun save(transaction: DeferredCredentialTransaction): DeferredCredentialTransaction {
        transactions[transaction.transactionId] = transaction
        return transaction
    }

    override suspend fun get(transactionId: String): DeferredCredentialTransaction? = transactions[transactionId]

    override suspend fun update(
        transactionId: String,
        updater: (DeferredCredentialTransaction) -> DeferredCredentialTransaction,
    ): DeferredCredentialTransaction? {
        val current = transactions[transactionId] ?: return null
        val updated = updater(current)
        transactions[transactionId] = updated
        return updated
    }

    override suspend fun remove(transactionId: String): DeferredCredentialTransaction? = transactions.remove(transactionId)

    override suspend fun consume(transactionId: String): DeferredCredentialTransaction? {
        val current = transactions[transactionId] ?: return null
        if (current.status == DeferredCredentialTransactionStatus.CONSUMED ||
            current.status == DeferredCredentialTransactionStatus.EXPIRED
        ) {
            return null
        }

        val updated = current.copy(status = DeferredCredentialTransactionStatus.CONSUMED)
        return if (transactions.replace(transactionId, current, updated)) updated else null
    }
}
