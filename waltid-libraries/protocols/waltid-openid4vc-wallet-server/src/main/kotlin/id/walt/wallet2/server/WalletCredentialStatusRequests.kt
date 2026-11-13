package id.walt.wallet2.server

import id.walt.credentials.status.entries
import id.walt.credentials.status.hasCheckError
import id.walt.credentials.status.isCheckable
import id.walt.credentials.status.lastObservation
import id.walt.errors.StatusException
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.status.CredentialStatusContext
import id.walt.wallet2.status.CredentialStatusRefreshMode
import id.walt.wallet2.status.CredentialStatusRefreshOutcome
import id.walt.wallet2.status.CredentialStatusRefreshResult
import id.walt.wallet2.status.CredentialStatusReviewChangedException
import id.walt.wallet2.status.CredentialStatusReviewState
import id.walt.wallet2.status.RefreshCredentialStatusesRequest
import id.walt.wallet2.status.WalletCredentialRead
import id.walt.wallet2.status.WalletCredentialStatus
import id.walt.wallet2.status.WalletCredentialStatusRefreshResponse
import id.walt.wallet2.status.WalletCredentialStatusService
import id.walt.wallet2.status.WalletCredentialStatusSnapshotResponse
import id.walt.wallet2.status.reviewGuard
import id.walt.wallet2.status.toResponse
import id.waltid.openid4vp.wallet.presentation.PresentationReviewGuard
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext

/**
 * Every operation creates a newly authorized service. Explicit active reviews retain only detached
 * evidence unavailable from managed snapshots, never a request-authorized service, credential
 * store, authorization object or list body. Contexts are
 * scoped to a backend/wallet lifecycle and expire after five idle minutes. The pool admits at most
 * 128 contexts and 4096 reserved records, with at most 1000 records in an individual context.
 */
class WalletCredentialStatusRequests(
    private val now: () -> Instant = { Clock.System.now() },
    private val serviceFactory: suspend (Wallet) -> WalletCredentialStatusService = {
        WalletCredentialStatusService(it, trustPolicyId = "wallet-status/default-v1",
            parentScope = CoroutineScope(currentCoroutineContext()))
    },
) {
    /** Use a stable backend identity, never an authorized service; include tenant, wallet generation and store configuration. */
    class Scope(
        private val backend: Any,
        private val walletId: String,
        private val generation: String? = null,
        storeConfiguration: List<String> = emptyList(),
    ) {
        private val storeConfiguration = storeConfiguration.toList()
        internal fun sameWallet(other: Scope): Boolean = backend === other.backend && walletId == other.walletId
        override fun equals(other: Any?): Boolean = other is Scope && backend === other.backend &&
            walletId == other.walletId && generation == other.generation && storeConfiguration == other.storeConfiguration
        override fun hashCode(): Int = 31 * (31 * (31 * System.identityHashCode(backend) + walletId.hashCode()) +
            (generation?.hashCode() ?: 0)) + storeConfiguration.hashCode()
    }

    private class Context(val scope: Scope, var state: CredentialStatusReviewState, var capacity: Int, var expiresAt: Instant) {
        var version = 0L
    }
    private data class LoadedContext(val id: String, val owner: Context, val state: CredentialStatusReviewState, val version: Long)
    private val admitted = ConcurrentHashMap<Scope, Instant>()
    private val contexts = mutableMapOf<String, Context>()

    suspend fun reads(wallet: Wallet): List<WalletCredentialRead> = serviceFactory(wallet).use { it.readCredentials() }
    suspend fun read(wallet: Wallet, id: String): WalletCredentialRead? = serviceFactory(wallet).use { it.readCredential(id) }

    /** Creates a context only when a host explicitly asks; stateless reads retain no extra state. */
    suspend fun createContext(wallet: Wallet, scope: Scope): WalletCredentialStatusSnapshotResponse = serviceFactory(wallet).use { service ->
        val snapshot = service.snapshot()
        val state = service.captureReviewState()
        val context = synchronized(contexts) {
            pruneContexts()
            requireCapacity(snapshot.credentials.size)
            if (contexts.size >= 128) throw StatusException(429, "Too many active credential status reviews")
            val id = UUID.randomUUID().toString()
            val owner = Context(scope, state, snapshot.credentials.size, now() + 5.minutes)
            contexts[id] = owner
            CredentialStatusContext(id, owner.expiresAt)
        }
        snapshot.toResponse().copy(statusContext = context)
    }

    suspend fun snapshot(wallet: Wallet, scope: Scope, statusContext: String? = null): WalletCredentialStatusSnapshotResponse =
        serviceFactory(wallet).use { service ->
            val loaded = statusContext?.let { loadContext(scope, it) }
            loaded?.let { service.restoreReviewState(it.state) }
            service.snapshot().toResponse().copy(statusContext = loaded?.let(::describe))
        }

    suspend fun <T> withReviewedStatus(
        wallet: Wallet, scope: Scope, ids: Set<String>, reviewed: Map<String, String>, statusContext: String? = null,
        operation: suspend () -> T,
    ): T = serviceFactory(wallet).use { service ->
        require(ids.isNotEmpty() && ids.size <= 100)
        val localGuard = service.reviewGuard(ids, reviewed)
        val guard = PresentationReviewGuard {
            // Another refresh can update this active review while signing/authorization is pending.
            statusContext?.let { service.restoreReviewState(loadContext(scope, it).state) }
            localGuard.check()
        }
        guard.check()
        withContext(guard) { operation() }
    }

    suspend fun refresh(wallet: Wallet, scope: Scope, request: RefreshCredentialStatusesRequest): List<WalletCredentialStatusRefreshResponse> =
        serviceFactory(wallet).use { service ->
            val loaded = request.statusContext?.let { loadContext(scope, it) }
            loaded?.let { service.restoreReviewState(it.state) }
            val snapshot = service.snapshot()
            val selection = request.credentials
            require(selection == null || selection.all { ref -> snapshot.credentials.any { it.reference == ref } }) {
                "Credential selection does not belong to this wallet"
            }
            val selected = snapshot.credentials.filter { selection == null || it.reference in selection }
            val localOnly = selected.all { record -> record.status.entries.all { entry ->
                !entry.isCheckable(snapshot.evaluatedAt) ||
                    request.mode == CredentialStatusRefreshMode.IF_DUE && entry.lastObservation()?.isFresh(snapshot.evaluatedAt) == true
            } }
            if (!localOnly && loaded != null) reserve(loaded, snapshot.credentials.size)
            val allowed = !localOnly && admit(scope)
            val results = when {
                localOnly -> selected.map { CredentialStatusRefreshResult(it.reference, CredentialStatusRefreshOutcome.UNCHANGED, it.status) }
                allowed -> service.refresh(selected.mapTo(mutableSetOf()) { it.reference }, request.mode)
                else -> selected.map { CredentialStatusRefreshResult(it.reference, CredentialStatusRefreshOutcome.DEFERRED, it.status) }
            }
            val context = if (allowed && loaded != null) save(loaded, service.captureReviewState()) else loaded?.let(::describe)
            val final = service.snapshot()
            val records = final.credentials.associateBy { it.reference }
            results.map { result ->
                val record = records[result.reference] ?: WalletCredentialStatus(result.reference, result.status, final.evaluatedAt)
                WalletCredentialStatusRefreshResponse(result.reference, result.outcome, record.status,
                    record.presentation.toData(), final.evaluatedAt, record.status.hasCheckError, context)
            }
        }

    fun closeContext(scope: Scope, id: String) = synchronized(contexts) {
        if (contexts[id]?.scope == scope) contexts.remove(id)
        Unit
    }

    fun forget(scope: Scope) {
        admitted.keys.removeIf { it.sameWallet(scope) }
        synchronized(contexts) { contexts.entries.removeIf { it.value.scope.sameWallet(scope) } }
    }

    private fun admit(scope: Scope): Boolean {
        val at = now()
        return synchronized(admitted) {
            admitted.entries.removeIf { at - it.value >= 30.seconds }
            if (admitted.containsKey(scope) || admitted.size >= 4096) false
            else { admitted[scope] = at; true }
        }
    }

    private fun pruneContexts() { val at = now(); contexts.entries.removeIf { at >= it.value.expiresAt } }

    private fun requireCapacity(capacity: Int, replacing: Context? = null) {
        if (capacity > 1000 || contexts.values.sumOf { it.capacity } - (replacing?.capacity ?: 0) + capacity > 4096)
            throw StatusException(429, "Credential status review capacity exceeded")
    }

    private fun loadContext(scope: Scope, id: String): LoadedContext = synchronized(contexts) {
        pruneContexts()
        if (id.length !in 1..128) throw CredentialStatusReviewChangedException()
        val context = contexts[id]?.takeIf { it.scope == scope } ?: throw CredentialStatusReviewChangedException()
        context.expiresAt = now() + 5.minutes
        LoadedContext(id, context, context.state, context.version)
    }

    private fun describe(loaded: LoadedContext): CredentialStatusContext = synchronized(contexts) {
        pruneContexts()
        val context = contexts[loaded.id]?.takeIf { it === loaded.owner } ?: throw CredentialStatusReviewChangedException()
        CredentialStatusContext(loaded.id, context.expiresAt)
    }

    private fun reserve(loaded: LoadedContext, records: Int) = synchronized(contexts) {
        pruneContexts()
        val context = contexts[loaded.id]?.takeIf { it === loaded.owner } ?: throw CredentialStatusReviewChangedException()
        val capacity = maxOf(records, context.state.recordCount)
        requireCapacity(capacity, context)
        context.capacity = capacity
    }

    private fun save(loaded: LoadedContext, state: CredentialStatusReviewState): CredentialStatusContext = synchronized(contexts) {
        pruneContexts()
        val context = contexts[loaded.id]?.takeIf { it === loaded.owner && it.version == loaded.version }
            ?: throw CredentialStatusReviewChangedException()
        check(state.recordCount <= context.capacity) { "Credential status review reservation was exceeded" }
        context.state = state
        context.version++
        context.expiresAt = now() + 5.minutes
        CredentialStatusContext(loaded.id, context.expiresAt)
    }
}
