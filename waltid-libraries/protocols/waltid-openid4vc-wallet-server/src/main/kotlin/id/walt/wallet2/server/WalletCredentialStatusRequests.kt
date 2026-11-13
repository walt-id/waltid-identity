package id.walt.wallet2.server

import id.walt.credentials.status.entries
import id.walt.credentials.status.isCheckable
import id.walt.credentials.status.lastObservation
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.status.CredentialStatusRefreshMode
import id.walt.wallet2.status.CredentialStatusRefreshOutcome
import id.walt.wallet2.status.CredentialStatusRefreshResult
import id.walt.wallet2.status.RefreshCredentialStatusesRequest
import id.walt.wallet2.status.WalletCredentialRead
import id.walt.wallet2.status.WalletCredentialStatusService
import id.walt.wallet2.status.WalletCredentialStatusSnapshotResponse
import id.walt.wallet2.status.reviewGuard
import id.walt.wallet2.status.toResponse
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext

/**
 * Request-scoped services never retain authentication or Enterprise service instances.
 * Only a bounded, wallet-scoped cooldown survives requests; observations live in the managed store.
 * Instantiate once per backend/route registration and pass a tenant + wallet lifecycle key.
 */
class WalletCredentialStatusRequests(
    private val serviceFactory: suspend (Wallet) -> WalletCredentialStatusService = {
        WalletCredentialStatusService(it, trustPolicyId = "wallet-status/default-v1",
            parentScope = CoroutineScope(currentCoroutineContext()))
    },
) {
    /** Backend identity and wallet generation keep equal paths in separate lifecycles isolated. */
    class Scope(private val backend: Any, private val walletId: String, private val generation: String? = null) {
        override fun equals(other: Any?): Boolean = other is Scope && backend === other.backend &&
            walletId == other.walletId && generation == other.generation
        override fun hashCode(): Int = 31 * (31 * System.identityHashCode(backend) + walletId.hashCode()) + (generation?.hashCode() ?: 0)
    }

    private val admitted = ConcurrentHashMap<Scope, Long>()

    suspend fun reads(wallet: Wallet): List<WalletCredentialRead> = serviceFactory(wallet).use { it.readCredentials() }
    suspend fun read(wallet: Wallet, id: String): WalletCredentialRead? = serviceFactory(wallet).use { it.readCredential(id) }
    suspend fun snapshot(wallet: Wallet): WalletCredentialStatusSnapshotResponse = serviceFactory(wallet).use { it.snapshot().toResponse() }

    suspend fun <T> withReviewedStatus(wallet: Wallet, ids: Set<String>, reviewed: Map<String, String>, operation: suspend () -> T): T =
        serviceFactory(wallet).use { service ->
            require(ids.isNotEmpty() && ids.size <= 100)
            val guard = service.reviewGuard(ids, reviewed)
            guard.check()
            withContext(guard) { operation() }
        }

    suspend fun refresh(wallet: Wallet, scope: Scope, request: RefreshCredentialStatusesRequest): List<CredentialStatusRefreshResult> =
        serviceFactory(wallet).use { service ->
            val snapshot = service.snapshot()
            val selection = request.credentials
            require(selection == null || selection.all { ref -> snapshot.credentials.any { it.reference == ref } }) {
                "Credential selection does not belong to this wallet"
            }
            val selected = snapshot.credentials.filter { selection == null || it.reference in selection }
            // Local no-op checks must not consume admission for a later explicit revalidation.
            val localOnly = selected.all { record -> record.status.entries.all { entry ->
                !entry.isCheckable(snapshot.evaluatedAt) ||
                    request.mode == CredentialStatusRefreshMode.IF_DUE && entry.lastObservation()?.isFresh(snapshot.evaluatedAt) == true
            } }
            if (localOnly) return@use selected.map {
                CredentialStatusRefreshResult(it.reference, CredentialStatusRefreshOutcome.UNCHANGED, it.status)
            }
            val now = System.nanoTime()
            val allowed = synchronized(admitted) {
                admitted.entries.removeIf { now - it.value >= 30.seconds.inWholeNanoseconds }
                if (admitted.containsKey(scope) || admitted.size >= 4096) false
                else { admitted[scope] = now; true }
            }
            if (allowed) service.refresh(selection, request.mode)
            else selected.map {
                CredentialStatusRefreshResult(it.reference, CredentialStatusRefreshOutcome.DEFERRED, it.status)
            }
        }

    fun forget(scope: Scope) { admitted.remove(scope) }
}
