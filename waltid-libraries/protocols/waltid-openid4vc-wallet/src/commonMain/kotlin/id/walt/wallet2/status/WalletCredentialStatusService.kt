@file:OptIn(ExperimentalCoroutinesApi::class)

package id.walt.wallet2.status

import id.walt.credentials.status.CheckFailure
import id.walt.credentials.status.CredentialStatus
import id.walt.credentials.status.CredentialStatusEntry
import id.walt.credentials.status.CredentialStatusLimits
import id.walt.credentials.status.CredentialStatusRefreshSession
import id.walt.credentials.status.CredentialStatusResolver
import id.walt.credentials.status.DiscoveryProblem
import id.walt.credentials.status.eligibleAt
import id.walt.credentials.status.entries
import id.walt.credentials.status.CredentialStatusReferenceAuthorizer
import id.walt.credentials.status.hasCheckError
import id.walt.credentials.status.historical
import id.walt.credentials.status.isCheckable
import id.walt.credentials.status.lastAttemptAt
import id.walt.credentials.status.lastObservation
import id.walt.credentials.status.matches
import id.walt.credentials.status.mergePrevious
import id.walt.credentials.status.withFailure
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.data.WalletCredentialStore
import id.walt.webdatafetching.WebDataFetcher
import id.walt.webdatafetching.WebDataFetchingConfiguration
import id.walt.webdatafetching.config.TimeoutConfiguration
import id.walt.webdatafetching.config.LoggingConfiguration
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import id.walt.policies2.vc.policies.status.signature.StatusListSignerAuthorizer
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * Wallet-owned observations and explicit work. Hosts own foreground/background scheduling.
 * Construction, reads and observation do not perform issuer networking.
 */
class WalletCredentialStatusService(
    val wallet: Wallet,
    client: HttpClient? = null,
    private val now: () -> Instant = { Clock.System.now() },
    private val trustPolicyId: String? = null,
    limits: CredentialStatusLimits = CredentialStatusLimits(),
    referenceAuthorizer: CredentialStatusReferenceAuthorizer? = null,
    signerAuthorizer: StatusListSignerAuthorizer? = null,
    parentScope: CoroutineScope? = null,
) : AutoCloseable {
    private data class Record(val reference: WalletCredentialReference, val store: WalletCredentialStore, val credential: StoredCredential) {
        val fingerprint = credential.statusFingerprint()
    }
    private data class State(val store: WalletCredentialStore, val fingerprint: String, val generation: Long, val metadata: CredentialStatus)
    private class Flight(val fingerprint: String, val result: Deferred<CredentialStatusRefreshResult>, var owners: Int = 0)

    private val job = SupervisorJob(parentScope?.coroutineContext?.get(Job))
    private val scope = CoroutineScope((parentScope?.coroutineContext ?: Dispatchers.Default) + job)
    private val ownedFetcher = if (client == null) WebDataFetcher("wallet-credential-status", WebDataFetchingConfiguration(
        maxResponseBytes = limits.maxResponseBytes.toLong(),
        logging = LoggingConfiguration(enable = false),
        timeouts = TimeoutConfiguration(requestTimeout = limits.requestTimeout),
    )) else null
    private val resolver = CredentialStatusResolver(client ?: requireNotNull(ownedFetcher).httpClient, scope, now,
        limits, signerAuthorizer, referenceAuthorizer)
    private val mutex = Mutex()
    private val states = mutableMapOf<WalletCredentialReference, State>()
    private val flights = mutableMapOf<WalletCredentialReference, Flight>()
    private val retryAfter = mutableMapOf<WalletCredentialReference, Instant>()
    private val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private var generation = 0L

    /** Host/store integration hook after successful credential writes; emits local state only. */
    fun notifyCredentialInventoryChanged() { changes.tryEmit(Unit) }

    /** Returns every credential's local observation; stale state retains its original check time. */
    suspend fun snapshot(): WalletCredentialStatusSnapshot {
        check(job.isActive) { "Credential status service is closed" }
        val records = records()
        val metadata = records.map { record -> record.reference to local(record) }
        val at = now()
        val entries = metadata.map { (reference, status) -> WalletCredentialStatus(reference, status, at) }
        mutex.withLock {
            val retained = records.mapTo(mutableSetOf(), Record::reference)
            states.keys.filter { it !in retained }.forEach {
                states.remove(it); retryAfter.remove(it); flights.remove(it)?.result?.cancel()
            }
            val next = metadata.flatMap { (reference, status) ->
                status.entries.mapNotNull { entry ->
                    val eligible = maxOf(at, entry.eligibleAt ?: at)
                    if (!entry.isCheckable(eligible)) return@mapNotNull null
                    val freshUntil = entry.lastObservation()?.takeIf { it.isFresh(at) }?.freshUntil ?: at
                    val failedAttempt = entry.lastAttemptAt?.plus(30.seconds) ?: at
                    maxOf(eligible, freshUntil, failedAttempt, retryAfter[reference] ?: at)
                }
            }.minOrNull()
            return WalletCredentialStatusSnapshot(at, entries, flights.keys.toSet(), next)
        }
    }

    suspend fun readCredentials(): List<WalletCredentialRead> = records().map { record ->
        WalletCredentialRead(record.credential, record.reference, local(record), now())
    }

    suspend fun readCredential(id: String): WalletCredentialRead? {
        val record = records().firstOrNull { it.credential.id == id } ?: return null
        return WalletCredentialRead(record.credential, record.reference, local(record), now())
    }

    /** Freshness deadlines emit local state changes without requesting a status list. */
    fun observe(): Flow<WalletCredentialStatusSnapshot> = channelFlow {
        if (!job.isActive) { close(); return@channelFlow }
        val completion = job.invokeOnCompletion { close() }
        val collector = launch {
            changes.onStart { emit(Unit) }.transformLatest {
                while (currentCoroutineContext().isActive) {
                    val snapshot = snapshot()
                    emit(snapshot)
                    val next = snapshot.credentials.flatMap { it.status.entries }
                        .flatMap { listOfNotNull(it.lastObservation()?.freshUntil, it.eligibleAt) }
                        .filter { it > snapshot.evaluatedAt }.minOrNull()
                    if (next == null) awaitCancellation()
                    delay((next - now()).coerceAtLeast(Duration.ZERO) + 1.milliseconds)
                }
            }.collect { send(it) }
        }
        awaitClose { collector.cancel(); completion.dispose() }
    }.buffer(Channel.CONFLATED)

    /** Bounded batch. Omitted selection means all stored records; IDs are never URLs. */
    suspend fun refresh(
        selection: Set<WalletCredentialReference>? = null,
        mode: CredentialStatusRefreshMode = CredentialStatusRefreshMode.IF_DUE,
        maxItems: Int = 100,
        executionBudget: Duration = 30.seconds,
    ): List<CredentialStatusRefreshResult> = coroutineScope {
        check(job.isActive) { "Credential status service is closed" }
        require(selection == null || selection.isNotEmpty())
        require(maxItems in 1..1000 && executionBudget.isPositive())
        val all = records()
        if (selection != null) require(selection.all { key -> all.any { it.reference == key } }) {
            "Credential selection does not belong to this wallet"
        }
        // Keep bounded retries useful for large inventories, including lists with immediate expiry.
        // Untouched records go first; then prefer the oldest attempt rather than fixed store order.
        val selected = all.filter { selection == null || it.reference in selection }.map { it to local(it) }
            .sortedBy { (_, metadata) -> metadata.entries.flatMap { entry ->
                listOfNotNull(entry.lastObservation()?.checkedAt, entry.lastAttemptAt)
            }.maxOrNull() }
        val refreshSession = resolver.newRefreshSession()
        try {
            val started = now()
            var budget = maxItems
            val planned = selected.map { (record, cached) ->
                if (mode == CredentialStatusRefreshMode.IF_DUE && !due(record.reference, cached)) {
                    return@map async { CredentialStatusRefreshResult(record.reference, CredentialStatusRefreshOutcome.UNCHANGED, cached) }
                }
                if (cached.entries.none(::checkable)) {
                    return@map async { CredentialStatusRefreshResult(record.reference, CredentialStatusRefreshOutcome.UNCHANGED, cached) }
                }
                if (budget-- <= 0 || now() - started >= executionBudget) {
                    return@map async { CredentialStatusRefreshResult(record.reference, CredentialStatusRefreshOutcome.DEFERRED, cached) }
                }
                async {
                    withTimeoutOrNull((executionBudget - (now() - started)).coerceAtLeast(1.milliseconds)) {
                        refreshRecord(record, mode, refreshSession)
                    } ?: CredentialStatusRefreshResult(record.reference, CredentialStatusRefreshOutcome.DEFERRED, local(record))
                }
            }
            planned.awaitAll()
        } finally { withContext(NonCancellable) { refreshSession.close() } }
    }

    private suspend fun records(): List<Record> = wallet.credentialStores.flatMapIndexed { index, store ->
        store.listCredentials().toList().map { Record(WalletCredentialReference(index, it.id), store, it) }
    }

    private suspend fun local(record: Record): CredentialStatus {
        check(job.isActive) { "Credential status service is closed" }
        mutex.withLock { states[record.reference]?.takeIf { it.store === record.store && it.fingerprint == record.fingerprint }?.let { return it.metadata } }
        val discovered = resolver.discover(record.credential.credential)
        val stored = (record.store as? CredentialStatusSnapshotStore)?.readCredentialStatus(record.credential.id)
        val restored = stored?.takeIf { it.schemaVersion == 2 && it.credentialFingerprint == record.fingerprint }?.let { saved ->
            if (discovered !is CredentialStatus.References) return@let discovered
            val samePolicy = trustPolicyId != null && saved.trustPolicyId == trustPolicyId
            val known = saved.metadata.entries.associateBy(CredentialStatusEntry::referenceId)
            val at = now()
            CredentialStatus.references(discovered.entries.map { entry ->
                val previous = known[entry.referenceId]?.takeIf { entry.matches(it) } ?: return@map entry
                val observation = previous.lastObservation()
                val validUntil = observation?.validUntil
                val applicable = observation == null || observation.checkedAt <= at && (validUntil == null || at < validUntil)
                if (samePolicy && applicable) previous else previous.historical(at)
            })
        } ?: discovered
        return mutex.withLock {
            states[record.reference]?.takeIf { it.store === record.store && it.fingerprint == record.fingerprint }?.metadata ?: run {
                flights.remove(record.reference)?.result?.cancel()
                states[record.reference] = State(record.store, record.fingerprint, ++generation, restored)
                restored
            }
        }
    }

    private suspend fun due(reference: WalletCredentialReference, metadata: CredentialStatus): Boolean {
        val at = now()
        if (mutex.withLock { retryAfter[reference]?.let { it > at } == true }) return false
        return metadata.entries.any { checkable(it) && it.lastObservation()?.isFresh(at) != true }
    }

    private fun checkable(entry: CredentialStatusEntry): Boolean = entry.isCheckable(now())

    private suspend fun refreshRecord(record: Record, mode: CredentialStatusRefreshMode, refreshSession: CredentialStatusRefreshSession): CredentialStatusRefreshResult {
        val flight = mutex.withLock {
            val state = requireNotNull(states[record.reference])
            flights.getOrPut(record.reference) {
                Flight(record.fingerprint, scope.async(start = CoroutineStart.LAZY) { evaluateAndCommit(record, state.generation, mode, refreshSession) })
            }.also { it.owners++ }
        }
        changes.tryEmit(Unit)
        flight.result.start()
        return try { flight.result.await() } finally {
            withContext(NonCancellable) { mutex.withLock {
                flight.owners--
                if (flight.owners == 0 && flights[record.reference] === flight) {
                    flights.remove(record.reference)
                    if (!flight.result.isCompleted) flight.result.cancel()
                }
            } }
            changes.tryEmit(Unit)
        }
    }

    private suspend fun evaluateAndCommit(record: Record, version: Long, mode: CredentialStatusRefreshMode, refreshSession: CredentialStatusRefreshSession): CredentialStatusRefreshResult {
        val resolved = resolver.resolve(record.credential.credential, mode == CredentialStatusRefreshMode.FORCE, refreshSession)
        currentCoroutineContext().ensureActive()
        val current = record.store.getCredential(record.credential.id)
        if (wallet.credentialStores.getOrNull(record.reference.storeIndex) !== record.store || current?.statusFingerprint() != record.fingerprint) {
            return CredentialStatusRefreshResult(record.reference, CredentialStatusRefreshOutcome.REMOVED, CredentialStatus.references())
        }
        val merged = mutex.withLock {
            val previous = states[record.reference]?.takeIf { it.store === record.store && it.fingerprint == record.fingerprint && it.generation == version }
                ?: return CredentialStatusRefreshResult(record.reference, CredentialStatusRefreshOutcome.REMOVED, CredentialStatus.references())
            val known = previous.metadata.entries.associateBy(CredentialStatusEntry::referenceId)
            when (val metadata = resolved.metadata) {
                is CredentialStatus.DiscoveryFailed -> {
                    // The fingerprint/generation fence above still binds these real earlier references.
                    // Preserve their dated warning when a later container cannot be enumerated.
                    if (previous.metadata.entries.any { it.lastObservation() != null }) {
                        val cause = if (metadata.problem == DiscoveryProblem.RESOURCE_LIMIT) CheckFailure.Reason.RESOURCE_LIMIT else CheckFailure.Reason.INVALID_LIST
                        CredentialStatus.references(previous.metadata.entries.map { it.withFailure(cause, now()) })
                    } else metadata
                }
                is CredentialStatus.References -> CredentialStatus.references(metadata.entries.map { entry ->
                    val prior = known[entry.referenceId]?.takeIf { entry.matches(it) }
                    if (prior == null) entry else entry.mergePrevious(prior, now())
                })
            }
        }

        val persistent = CredentialStatus.references(merged.entries.mapNotNull { entry ->
            entry.takeUnless { it.referenceId in resolved.nonPersistentReferenceIds }
        })
        val snapshotStore = record.store as? CredentialStatusSnapshotStore
        if (snapshotStore != null) {
            val stored = if (persistent.entries.isEmpty()) snapshotStore.removeCredentialStatusIfCurrent(current)
            else snapshotStore.saveCredentialStatusIfCurrent(current, PersistedCredentialStatus(
                    credentialFingerprint = record.fingerprint, trustPolicyId = trustPolicyId, metadata = persistent,
                ))
            if (!stored) {
                return CredentialStatusRefreshResult(record.reference, CredentialStatusRefreshOutcome.REMOVED, CredentialStatus.references())
            }
        }
        currentCoroutineContext().ensureActive()
        mutex.withLock {
            check(job.isActive) { "Credential status service is closed" }
            val state = states[record.reference]
            if (wallet.credentialStores.getOrNull(record.reference.storeIndex) !== record.store ||
                state == null || state.store !== record.store || state.fingerprint != record.fingerprint || state.generation != version) {
                return CredentialStatusRefreshResult(record.reference, CredentialStatusRefreshOutcome.REMOVED, CredentialStatus.references())
            }
            states[record.reference] = state.copy(metadata = merged)
            if (merged.hasCheckError) retryAfter[record.reference] = now() + 30.seconds
            else retryAfter.remove(record.reference)
        }
        changes.tryEmit(Unit)
        return CredentialStatusRefreshResult(record.reference, CredentialStatusRefreshOutcome.COMPLETED, merged)
    }

    override fun close() {
        job.cancel()
        resolver.close()
        ownedFetcher?.close()
    }
}
