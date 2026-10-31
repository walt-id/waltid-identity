package id.walt.openid4vci.deferred

import id.walt.openid4vci.errors.CredentialError
import id.walt.openid4vci.errors.CredentialErrorCodes
import id.walt.openid4vci.requests.credential.AcceptedCredentialRequest
import id.walt.openid4vci.requests.generateRequestId
import id.walt.openid4vci.responses.credential.CredentialResponse
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Private continuation data. Never include this object in a management response. */
@Serializable
data class DeferredIssuanceState(
    val status: DeferredStatus = DeferredStatus.PENDING,
    val deadline: Instant? = null,
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    @kotlinx.serialization.EncodeDefault
    val version: Long = 0,
    val transactions: Map<String, DeferredTransaction> = emptyMap(),
)

@Serializable
data class DeferredTransaction(
    val id: String,
    val accepted: AcceptedCredentialRequest,
    val claimId: String? = null,
    val claimExpiresAt: Instant? = null,
    val response: CredentialResponse? = null,
    /** Service-owned issuance progress, e.g. allocated credential status entries. */
    val progress: JsonObject? = null,
    val consumed: Boolean = false,
)

data class DeferredSessionSnapshot(val id: String, val expiresAt: Instant, val state: DeferredIssuanceState?)

/** Implementations must atomically compare the version and update session lifecycle with the state. */
interface DeferredIssuanceStore {
    suspend fun get(id: String): DeferredSessionSnapshot?
    suspend fun compareAndSet(expected: DeferredSessionSnapshot, replacement: DeferredIssuanceState): Boolean
}

class DeferredDecisionException(val httpStatus: Int, message: String) : IllegalStateException(message)
class DeferredProtocolException(val error: CredentialError) : IllegalStateException(error.description)

/** Operates only on an already resolved and authorized issuance session. Knows nothing about grants. */
class DeferredIssuance(
    private val store: DeferredIssuanceStore,
    val config: DeferredIssuanceConfig = DeferredIssuanceConfig(),
    private val now: () -> Instant = { Clock.System.now() },
) {
    suspend fun decide(sessionId: String, decision: DeferredStatus) {
        require(decision != DeferredStatus.PENDING)
        update(sessionId, management = true) { snapshot, state ->
            when (state.status) {
                decision -> state
                DeferredStatus.PENDING -> state.copy(status = decision)
                else -> throw DeferredDecisionException(409, "Deferred issuance has already been decided")
            }
        }
    }

    /** Set once after a successful token response has been prepared; activity never extends it. */
    suspend fun start(sessionId: String, deadline: Instant): Instant {
        val result = update(sessionId) { _, state ->
            if (state.deadline == null) state.copy(deadline = deadline) else state
        }
        return requireNotNull(result.deadline)
    }

    suspend fun accept(sessionId: String, accepted: AcceptedCredentialRequest): DeferredTransaction {
        val transaction = DeferredTransaction(generateRequestId(), accepted)
        val state = update(sessionId) { _, state ->
            if (state.status == DeferredStatus.DENIED) denied()
            val existing = state.transactions.values.find { it.accepted.credentialIdentifier == accepted.credentialIdentifier }
            if (existing != null) {
                if (existing.consumed) invalidTransaction()
                if (!existing.accepted.equivalentTo(accepted)) {
                    throw DeferredProtocolException(CredentialError(CredentialErrorCodes.INVALID_CREDENTIAL_REQUEST, "Credential request conflicts with the accepted transaction"))
                }
                state
            } else state.copy(transactions = state.transactions + (transaction.id to transaction))
        }
        return state.transactions.values.single { it.accepted.credentialIdentifier == accepted.credentialIdentifier }
    }

    sealed interface Collection {
        data class Pending(val response: CredentialResponse) : Collection
        data class Claimed(val transaction: DeferredTransaction) : Collection
    }

    /** Token authorization is resolved by the caller on every poll, including refreshed tokens. */
    suspend fun collect(
        sessionId: String,
        transactionId: String,
        authorizedCredentialIdentifiers: Set<String>,
        clientId: String?,
    ): Collection {
        var result: Collection? = null
        update(sessionId) { _, state ->
            val transaction = state.transactions[transactionId] ?: invalidTransaction()
            if (transaction.consumed) invalidTransaction()
            if (transaction.accepted.credentialIdentifier !in authorizedCredentialIdentifiers ||
                transaction.accepted.request.accessTokenClientId != clientId) {
                invalidTransaction()
            }
            if (state.status == DeferredStatus.DENIED) denied()
            if (state.status == DeferredStatus.PENDING || transaction.claimExpiresAt?.let { it > now() } == true) {
                result = Collection.Pending(pending(transactionId))
                state
            } else {
                val claimed = transaction.copy(claimId = generateRequestId(), claimExpiresAt = now() + 5.minutes)
                result = Collection.Claimed(claimed)
                state.copy(transactions = state.transactions + (transactionId to claimed))
            }
        }
        return requireNotNull(result)
    }

    /** Renew only the processing lease, never the offer's absolute deadline. */
    suspend fun <T> withClaim(sessionId: String, claim: DeferredTransaction, action: suspend () -> T): T = coroutineScope {
        val heartbeat = launch {
            while (true) {
                delay(60_000)
                mutateClaim(sessionId, claim) { it.copy(claimExpiresAt = now() + 5.minutes) }
            }
        }
        try { action() } finally { heartbeat.cancelAndJoin() }
    }

    /** A fenced checkpoint lets a new process resume finalization without signing again. */
    suspend fun checkpoint(sessionId: String, claim: DeferredTransaction, response: CredentialResponse, progress: JsonObject? = null) {
        mutateClaim(sessionId, claim) { it.copy(response = response, progress = progress) }
    }

    suspend fun checkpointProgress(sessionId: String, claim: DeferredTransaction, progress: JsonObject) {
        mutateClaim(sessionId, claim) { it.copy(progress = progress) }
    }

    suspend fun consume(sessionId: String, claim: DeferredTransaction) {
        mutateClaim(sessionId, claim) { it.copy(consumed = true, claimId = null, claimExpiresAt = null) }
    }

    suspend fun release(sessionId: String, claim: DeferredTransaction) {
        mutateClaim(sessionId, claim) { it.copy(claimId = null, claimExpiresAt = null) }
    }

    fun pending(transactionId: String) = CredentialResponse(transactionId = transactionId, interval = config.pollingIntervalSeconds)

    private suspend fun mutateClaim(sessionId: String, claim: DeferredTransaction, transform: (DeferredTransaction) -> DeferredTransaction) {
        update(sessionId) { _, state ->
            val current = state.transactions[claim.id] ?: invalidTransaction()
            check(!current.consumed && current.claimId == claim.claimId && claim.claimId != null) { "Deferred transaction claim was lost" }
            state.copy(transactions = state.transactions + (claim.id to transform(current)))
        }
    }

    private suspend fun update(
        sessionId: String,
        management: Boolean = false,
        transform: suspend (DeferredSessionSnapshot, DeferredIssuanceState) -> DeferredIssuanceState,
    ): DeferredIssuanceState {
        while (true) {
            val snapshot = store.get(sessionId)
            if (snapshot == null || (snapshot.state?.deadline ?: snapshot.expiresAt) <= now()) {
                if (management) throw DeferredDecisionException(404, "Offer is unknown or expired") else invalidTransaction()
            }
            val state = snapshot.state ?: if (management) {
                throw DeferredDecisionException(409, "Offer does not use deferred issuance")
            } else invalidTransaction()
            val changed = transform(snapshot, state)
            if (changed == state) return state
            val replacement = changed.copy(version = state.version + 1)
            if (store.compareAndSet(snapshot, replacement)) return replacement
        }
    }

    private fun invalidTransaction(): Nothing = throw DeferredProtocolException(
        CredentialError(CredentialErrorCodes.INVALID_TRANSACTION_ID, "Transaction is unknown, expired, consumed, or unauthorized")
    )
    private fun denied(): Nothing = throw DeferredProtocolException(
        CredentialError(CredentialErrorCodes.CREDENTIAL_REQUEST_DENIED, "Credential issuance was denied")
    )
}
