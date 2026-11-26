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
import id.walt.credentials.status.meaning
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
import kotlin.uuid.Uuid
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
class WalletCredentialStatusService private constructor(
    private val owner: Owner,
    client: HttpClient?,
    private val now: () -> Instant,
    private val statusTrustPolicy: suspend () -> String?,
    limits: CredentialStatusLimits,
    referenceAuthorizer: CredentialStatusReferenceAuthorizer?,
    signerAuthorizer: StatusListSignerAuthorizer?,
    parentScope: CoroutineScope?,
    private val onNoticeCommitted: () -> Unit = {},
) : AutoCloseable {
    constructor(
        wallet: Wallet,
        client: HttpClient? = null,
        now: () -> Instant = { Clock.System.now() },
        trustPolicyId: String? = null,
        limits: CredentialStatusLimits = CredentialStatusLimits(),
        referenceAuthorizer: CredentialStatusReferenceAuthorizer? = null,
        signerAuthorizer: StatusListSignerAuthorizer? = null,
        parentScope: CoroutineScope? = null,
    ) : this(Owner.Foreground(wallet), client, now, { trustPolicyId }, limits, referenceAuthorizer, signerAuthorizer, parentScope)

    val wallet: Wallet get() = (owner as Owner.Foreground).wallet

    private sealed interface Owner {
        class Foreground(val wallet: Wallet) : Owner
        class Maintenance(val inventory: CredentialStatusInventory) : Owner
    }

    companion object {
        /** Uses the same built-in status resolver as restricted maintenance. */
        fun managed(wallet: Wallet, trustPolicy: suspend () -> String?): WalletCredentialStatusService =
            WalletCredentialStatusService(Owner.Foreground(wallet), null, { Clock.System.now() }, trustPolicy,
                CredentialStatusLimits(), null, null, null)

        /** One bounded, due-only pass over a restricted store, with progress recorded before each attempt. */
        suspend fun refreshMaintenancePage(
            inventory: CredentialStatusInventory,
            ids: List<String>,
            maxItems: Int,
            client: HttpClient?,
            trustPolicy: suspend () -> String?,
            advance: (String) -> Boolean,
            onItem: (CredentialStatusRefreshOutcome) -> Unit,
            onNoticeCommitted: () -> Unit,
        ): Unit = coroutineScope {
            val owner = Owner.Maintenance(inventory)
            WalletCredentialStatusService(owner, client, { Clock.System.now() }, trustPolicy,
                CredentialStatusLimits(), null, null, this, onNoticeCommitted).use { service ->
                service.maintenancePage(owner, ids, maxItems, advance, onItem)
            }
        }
    }

    private class Source(
        val identity: Any,
        val snapshots: CredentialStatusSnapshotStore?,
        val read: suspend (String) -> CredentialStatusInput?,
    ) {
        val notices = snapshots as? CredentialStatusNoticeStore

        constructor(store: WalletCredentialStore) : this(store, store as? CredentialStatusSnapshotStore, { id ->
            if (store is CredentialStatusNoticeStore) store.readStatusInput(id)
            else store.getCredential(id)?.let(CredentialStatusInput::Signed)
        })
    }

    private data class Candidate(val reference: WalletCredentialReference, val source: Source, val input: CredentialStatusInput) {
        fun record(): Record? = (input as? CredentialStatusInput.Signed)?.let { Record(reference, source, it.credential) }
    }

    private data class Record(val reference: WalletCredentialReference, val source: Source, val credential: StoredCredential) {
        val store: Any get() = source.identity
        val fingerprint = credential.statusFingerprint()
        suspend fun current(): StoredCredential? = (source.read(credential.id) as? CredentialStatusInput.Signed)?.credential
    }
    private data class SessionNotice(val value: CredentialStatusNoticeState, val evidenceRevision: String?)
    private data class State(
        val store: Any,
        val fingerprint: String,
        val generation: Long,
        val metadata: CredentialStatus,
        val nonPersistentReferenceIds: Set<String> = emptySet(),
        val persisted: CredentialStatusStoredRecord? = null,
        val policy: String? = null,
        val sessionNotice: SessionNotice? = null,
    )
    private class Flight(val result: Deferred<CredentialStatusRefreshResult>, var owners: Int = 0)

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

    /** Detaches evidence unavailable from managed snapshots for a bounded host review. Requires a stable trust policy ID. */
    suspend fun captureReviewState(): CredentialStatusReviewState {
        val policy = checkNotNull(statusTrustPolicy()) { "An active review requires a stable trust policy ID" }
        snapshot()
        return mutex.withLock {
            check(flights.isEmpty()) { "Cannot capture a review while status checks are running" }
            val retained = states.mapNotNull { (reference, state) ->
                val customStoreEvidence = state.store !is CredentialStatusSnapshotStore && state.metadata.entries.any {
                    it.lastObservation() != null || it.lastAttemptAt != null
                }
                if (state.nonPersistentReferenceIds.isEmpty() && !customStoreEvidence) null
                else CredentialStatusReviewEntry(reference, state.fingerprint, state.metadata,
                    state.nonPersistentReferenceIds.toSet(), state.persisted)
            }
            CredentialStatusReviewState(wallet.id, policy, retained)
        }
    }

    /**
     * Restores an active review into a newly authorized service, without networking or persistence.
     * Signed bytes and policy must still match; current managed evidence is reconciled locally.
     * Call before starting refresh work, and retain no service or store from the earlier request.
     */
    suspend fun restoreReviewState(review: CredentialStatusReviewState) {
        if (review.walletId != wallet.id || review.trustPolicyId != statusTrustPolicy()) throw CredentialStatusReviewChangedException()
        val records = records().associateBy(Record::reference)
        val matching = review.entries.mapNotNull { saved ->
            records[saved.reference]?.takeIf { it.fingerprint == saved.fingerprint }?.let { it to saved }
        }
        matching.forEach { (record, _) -> local(record) }
        mutex.withLock {
            check(job.isActive && flights.isEmpty()) { "Cannot restore a review while status checks are running" }
            for ((record, saved) in matching) {
                val state = states[record.reference]?.takeIf { it.store === record.store && it.fingerprint == record.fingerprint } ?: continue
                val metadata = if (state.persisted == saved.persisted) saved.metadata else reconcile(state.metadata, saved.metadata)
                val restricted = retainedRestrictions(metadata, saved.metadata, saved.nonPersistentReferenceIds) +
                    retainedRestrictions(metadata, state.metadata, state.nonPersistentReferenceIds)
                states[record.reference] = state.copy(metadata = metadata, nonPersistentReferenceIds = restricted)
            }
        }
        changes.tryEmit(Unit)
    }

    /** Returns local observations and pending notices in oldest check time order. */
    suspend fun snapshot(): WalletCredentialStatusSnapshot {
        check(job.isActive) { "Credential status service is closed" }
        val candidates = candidates()
        val local = candidates.map { it to it.readLocalState() }
        val at = now()
        val entries = local.map { (candidate, state) ->
            WalletCredentialStatus(candidate.reference, state?.metadata ?: unreadable, at)
        }
        val notices = local.mapNotNull { (candidate, state) ->
            state?.let { pendingNotice(candidate, it, at) }
        }.sortedWith(compareBy({ it.checkedAt }, { it.reference.storeIndex }, { it.reference.credentialId }))
        mutex.withLock {
            val retained = candidates.mapTo(mutableSetOf(), Candidate::reference)
            states.keys.filter { it !in retained }.forEach {
                states.remove(it); retryAfter.remove(it); flights.remove(it)?.result?.cancel()
            }
            val next = entries.flatMap { (reference, status) ->
                status.entries.mapNotNull { entry ->
                    val eligible = maxOf(at, entry.eligibleAt ?: at)
                    if (!entry.isCheckable(eligible)) return@mapNotNull null
                    val freshUntil = entry.lastObservation()?.takeIf { it.isFresh(at) }?.freshUntil ?: at
                    val failedAttempt = entry.lastAttemptAt?.plus(30.seconds) ?: at
                    maxOf(eligible, freshUntil, failedAttempt, retryAfter[reference] ?: at)
                }
            }.minOrNull()
            return WalletCredentialStatusSnapshot(at, entries, flights.keys.toSet(), next, notices)
        }
    }

    suspend fun readCredentials(): List<WalletCredentialRead> = wallet.credentialStores.flatMapIndexed { index, store ->
        val source = Source(store)
        store.listCredentials().toList().map { credential ->
            val reference = WalletCredentialReference(index, credential.id)
            WalletCredentialRead(credential, reference, local(Record(reference, source, credential)), now())
        }
    }

    suspend fun readCredential(id: String): WalletCredentialRead? {
        val record = wallet.credentialStores.withIndex().firstNotNullOfOrNull { (index, store) ->
            store.getCredential(id)?.let { Record(WalletCredentialReference(index, it.id), Source(store), it) }
        } ?: return null
        return WalletCredentialRead(record.credential, record.reference, local(record), now())
    }

    /** Local advisory evidence for the selected IDs, retaining the identity of every matching store. */
    suspend fun selectedStatuses(ids: Set<String>): List<WalletCredentialStatus> {
        val at = now()
        return wallet.credentialStores.flatMapIndexed { index, store ->
            val source = Source(store)
            ids.mapNotNull { id ->
                val input = source.read(id) ?: return@mapNotNull null
                val reference = WalletCredentialReference(index, id)
                WalletCredentialStatus(reference, Candidate(reference, source, input).readLocalState()?.metadata ?: unreadable, at)
            }
        }
    }

    private suspend fun pendingNotice(candidate: Candidate, state: State, at: Instant): PendingCredentialStatusNotice? {
        val input = candidate.input as CredentialStatusInput.Signed
        val saved = input.storedStatus?.snapshot
        val retained = input.notice?.takeIf {
            saved?.schemaVersion == 2 && saved.credentialFingerprint == state.fingerprint
        }
        if (state.sessionNotice == null && (retained == null || retained.acknowledged)) return null
        val entries = state.metadata.entries
        val live = state.sessionNotice?.value?.takeIf { notice ->
            entries.any { it.referenceId == notice.referenceId && it.meaning == notice.kind }
        }
        // Only carry a dismissal over the write that directly followed the live evidence.
        // An intervening recovery can create a new notice with the same meaning.
        if (retained != null && live?.acknowledged == true && live.kind == retained.kind &&
            live.referenceId == retained.referenceId && state.policy != null && state.policy == saved?.trustPolicyId &&
            retained.previousEvidenceRevision != null && retained.previousEvidenceRevision == state.sessionNotice.evidenceRevision
        ) {
            if (!retained.acknowledged) {
                candidate.source.notices?.acknowledgeStatusNotice(candidate.reference.credentialId, retained.version)
            }
            return null
        }
        val notice = listOfNotNull(retained, live).minByOrNull { adverseNoticeKinds.indexOf(it.kind) }
            ?.takeUnless { it.acknowledged } ?: return null
        val historical = entries.none {
            it.referenceId == notice.referenceId && it.meaning == notice.kind && it.lastObservation()?.isFresh(at) == true
        }
        return PendingCredentialStatusNotice(candidate.reference, notice.version, notice.kind, notice.checkedAt, historical)
    }

    suspend fun acknowledgeNotice(reference: WalletCredentialReference, version: String) {
        val store = wallet.credentialStores.getOrNull(reference.storeIndex) ?: return
        val acknowledged = mutex.withLock {
            val committed = (store as? CredentialStatusNoticeStore)?.acknowledgeStatusNotice(reference.credentialId, version) == true
            val state = states[reference] ?: return@withLock committed
            val session = state.sessionNotice?.takeIf {
                it.value.version == version && !it.value.acknowledged && state.store === store
            }
                ?: return@withLock committed
            states[reference] = state.copy(sessionNotice = session.copy(value = session.value.copy(acknowledged = true)))
            true
        }
        if (acknowledged) changes.tryEmit(Unit)
    }

    /** Freshness deadlines emit local state changes without requesting a status list. */
    fun observe(): Flow<WalletCredentialStatusSnapshot> = channelFlow {
        if (!job.isActive) { close(); return@channelFlow }
        val collector = scope.async {
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
        val completion = collector.invokeOnCompletion { cause ->
            if (job.isCancelled) close() else close(cause)
        }
        awaitClose { collector.cancel(); completion.dispose() }
    }.buffer(Channel.CONFLATED)

    /** Bounded batch. Omitted selection means all stored records; IDs are never URLs. */
    suspend fun refresh(
        selection: Set<WalletCredentialReference>? = null,
        mode: CredentialStatusRefreshMode = CredentialStatusRefreshMode.IF_DUE,
        maxItems: Int = 100,
        executionBudget: Duration = 30.seconds,
    ): List<CredentialStatusRefreshResult> {
        val work = scope.async { refreshOwned(selection, mode, maxItems, executionBudget) }
        return try {
            work.await()
        } finally {
            work.cancel()
        }
    }

    private suspend fun refreshOwned(
        selection: Set<WalletCredentialReference>?,
        mode: CredentialStatusRefreshMode,
        maxItems: Int,
        executionBudget: Duration,
    ): List<CredentialStatusRefreshResult> = coroutineScope {
        check(job.isActive) { "Credential status service is closed" }
        require(selection == null || selection.isNotEmpty())
        require(maxItems in 1..1000 && executionBudget.isPositive())
        val started = kotlin.time.TimeSource.Monotonic.markNow()
        val all = if (selection == null) candidates() else selection.map { reference ->
            val store = wallet.credentialStores.getOrNull(reference.storeIndex)
            requireNotNull(store?.let { candidate(reference, Source(it)) }) { "Credential selection does not belong to this wallet" }
        }
        // Keep bounded retries useful for large inventories, including lists with immediate expiry.
        // Untouched records go first; then prefer the oldest attempt rather than fixed store order.
        val selected = all.mapNotNull { candidate ->
            candidate.record()?.let { it to candidate.readLocalState(it).metadata }
        }
            .sortedBy { (_, metadata) -> metadata.entries.flatMap { entry ->
                listOfNotNull(entry.lastObservation()?.checkedAt, entry.lastAttemptAt)
            }.maxOrNull() }
        val malformed = all.filter { it.input is CredentialStatusInput.Unreadable }.map {
            CredentialStatusRefreshResult(it.reference, CredentialStatusRefreshOutcome.COMPLETED, unreadable)
        }
        val refreshSession = resolver.newRefreshSession()
        try {
            var budget = maxItems
            val planned = selected.map { (record, cached) ->
                if (mode == CredentialStatusRefreshMode.IF_DUE && !due(record.reference, cached)) {
                    return@map async { CredentialStatusRefreshResult(record.reference, CredentialStatusRefreshOutcome.UNCHANGED, cached) }
                }
                if (cached.entries.none(::checkable)) {
                    return@map async { CredentialStatusRefreshResult(record.reference, CredentialStatusRefreshOutcome.UNCHANGED, cached) }
                }
                if (budget-- <= 0 || started.elapsedNow() >= executionBudget) {
                    return@map async { CredentialStatusRefreshResult(record.reference, CredentialStatusRefreshOutcome.DEFERRED, cached) }
                }
                async {
                    withTimeoutOrNull((executionBudget - started.elapsedNow()).coerceAtLeast(1.milliseconds)) {
                        refreshRecord(record, mode, refreshSession)
                    } ?: CredentialStatusRefreshResult(record.reference, CredentialStatusRefreshOutcome.DEFERRED, local(record))
                }
            }
            planned.awaitAll() + malformed
        } finally { withContext(NonCancellable) { refreshSession.close() } }
    }

    private val unreadable = CredentialStatus.DiscoveryFailed(DiscoveryProblem.MALFORMED_CONTAINER)

    private suspend fun candidate(reference: WalletCredentialReference, source: Source): Candidate? =
        source.read(reference.credentialId)?.let { Candidate(reference, source, it) }

    private suspend fun candidates(): List<Candidate> = wallet.credentialStores.flatMapIndexed { index, store ->
        val source = Source(store)
        val inputs = if (store is CredentialStatusInventory) buildList<CredentialStatusInput> {
            var cursor: String? = null
            do {
                currentCoroutineContext().ensureActive()
                val page = store.statusPage(cursor, 64)
                addAll(page)
                cursor = page.lastOrNull()?.id
            } while (page.size == 64)
        } else store.listCredentials().toList().mapNotNull {
            if (store is CredentialStatusNoticeStore) source.read(it.id) else CredentialStatusInput.Signed(it)
        }
        inputs.map { Candidate(WalletCredentialReference(index, it.id), source, it) }
    }

    private suspend fun records(): List<Record> = candidates().mapNotNull(Candidate::record)

    private fun currentStore(reference: WalletCredentialReference): Any? = when (owner) {
        is Owner.Foreground -> owner.wallet.credentialStores.getOrNull(reference.storeIndex)
        is Owner.Maintenance -> owner.inventory.takeIf { reference.storeIndex == 0 }
    }

    private suspend fun maintenancePage(
        owner: Owner.Maintenance,
        ids: List<String>,
        maxItems: Int,
        advance: (String) -> Boolean,
        onItem: (CredentialStatusRefreshOutcome) -> Unit,
    ) {
        val inventory = owner.inventory
        val source = Source(inventory, inventory, inventory::readStatusInput)
        val session = resolver.newRefreshSession()
        try {
            var remaining = maxItems
            for (id in ids) {
                if (remaining == 0) break
                currentCoroutineContext().ensureActive()
                // Claim the ID before decoding: an interrupted row gets another turn after wraparound.
                if (!advance(id)) break
                val input = inventory.readStatusInput(id)
                if (input == null) {
                    onItem(CredentialStatusRefreshOutcome.UNCHANGED)
                    continue
                }
                val reference = WalletCredentialReference(0, id)
                val candidate = Candidate(reference, source, input)
                val record = candidate.record()
                val cached = record?.let { candidate.readLocalState(it).metadata } ?: unreadable
                currentCoroutineContext().ensureActive()
                val outcome = when {
                    record == null -> {
                        remaining--
                        CredentialStatusRefreshOutcome.COMPLETED
                    }
                    due(reference, cached) -> {
                        remaining--
                        refreshRecord(record, CredentialStatusRefreshMode.IF_DUE, session).outcome
                    }
                    else -> CredentialStatusRefreshOutcome.UNCHANGED
                }
                onItem(outcome)
            }
        } finally {
            withContext(NonCancellable) { session.close() }
        }
    }

    private suspend fun Candidate.readLocalState(): State? = record()?.let { readLocalState(it) }

    private suspend fun Candidate.readLocalState(record: Record): State {
        val stored = if (source.notices != null) (input as CredentialStatusInput.Signed).storedStatus
            else source.snapshots?.readCredentialStatusRecord(input.id)
        return localState(record, stored)
    }

    private suspend fun local(record: Record): CredentialStatus =
        localState(record, record.source.snapshots?.readCredentialStatusRecord(record.credential.id)).metadata

    private suspend fun localState(record: Record, stored: CredentialStatusStoredRecord?): State {
        check(job.isActive) { "Credential status service is closed" }
        val policy = statusTrustPolicy()
        mutex.withLock {
            states[record.reference]?.takeIf { it.store === record.store && it.fingerprint == record.fingerprint &&
                it.persisted == stored && it.policy == policy }
                ?.let { return it }
        }
        val discovered = resolver.discover(record.credential.credential)
        val restored = stored?.snapshot?.takeIf { it.schemaVersion == 2 && it.credentialFingerprint == record.fingerprint }?.let { saved ->
            if (discovered !is CredentialStatus.References) return@let discovered
            val samePolicy = policy != null && saved.trustPolicyId == policy
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
            val cached = states[record.reference]?.takeIf { it.store === record.store && it.fingerprint == record.fingerprint && it.policy == policy }
            if (cached != null) {
                if (cached.persisted == stored) return@withLock cached
                val reconciled = reconcile(restored, cached.metadata)
                val restricted = retainedRestrictions(reconciled, cached.metadata, cached.nonPersistentReferenceIds)
                cached.copy(metadata = reconciled, nonPersistentReferenceIds = restricted, persisted = stored)
                    .also { states[record.reference] = it }
            } else {
                flights.remove(record.reference)?.result?.cancel()
                State(record.store, record.fingerprint, ++generation, restored, persisted = stored, policy = policy)
                    .also { states[record.reference] = it }
            }
        }
    }

    private fun reconcile(current: CredentialStatus, previous: CredentialStatus): CredentialStatus {
        if (current !is CredentialStatus.References) return previous
        val known = previous.entries.associateBy(CredentialStatusEntry::referenceId)
        return CredentialStatus.references(current.entries.map { entry ->
            val prior = known[entry.referenceId]?.takeIf { entry.matches(it) } ?: return@map entry
            val lastUpdate = listOfNotNull(entry.lastObservation()?.checkedAt, entry.lastAttemptAt,
                prior.lastObservation()?.checkedAt, prior.lastAttemptAt).maxOrNull() ?: now()
            entry.mergePrevious(prior, lastUpdate)
        })
    }

    private fun retainedRestrictions(current: CredentialStatus, previous: CredentialStatus, restricted: Set<String>): Set<String> {
        val known = previous.entries.associateBy(CredentialStatusEntry::referenceId)
        return current.entries.mapNotNullTo(mutableSetOf()) { entry ->
            val prior = known[entry.referenceId]
            val observation = entry.lastObservation()
            entry.referenceId.takeIf { it in restricted &&
                (observation != null && observation === prior?.lastObservation() || observation == null && entry == prior) }
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
                Flight(scope.async(start = CoroutineStart.LAZY) { evaluateAndCommit(record, state.generation, mode, refreshSession) })
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
        val policy = statusTrustPolicy()
        val resolved = resolver.resolve(record.credential.credential, mode == CredentialStatusRefreshMode.FORCE, refreshSession)
        currentCoroutineContext().ensureActive()
        val current = record.current()
        if (currentStore(record.reference) !== record.store || current?.statusFingerprint() != record.fingerprint) {
            return removed(record)
        }
        if (statusTrustPolicy() != policy) {
            return CredentialStatusRefreshResult(record.reference, CredentialStatusRefreshOutcome.DEFERRED, local(record))
        }
        var merged = mutex.withLock {
            val previous = states[record.reference]?.takeIf { it.store === record.store && it.fingerprint == record.fingerprint && it.generation == version }
                ?: return removed(record)
            val known = previous.metadata.entries.associateBy(CredentialStatusEntry::referenceId)
            val metadata = when (val metadata = resolved.metadata) {
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
            previous.copy(metadata = metadata, nonPersistentReferenceIds = resolved.nonPersistentReferenceIds +
                retainedRestrictions(metadata, previous.metadata, previous.nonPersistentReferenceIds))
        }

        val snapshotStore = record.source.snapshots
        var expected = merged.persisted
        var committed: CredentialStatusStoredRecord? = null
        if (snapshotStore != null) {
            // Retry only the local atomic commit, never issuer work. A cleared record is a fence,
            // not permission to resurrect an observation from before that clear.
            for (attempt in 0 until 8) {
                val persistent = CredentialStatus.references(merged.metadata.entries.filterNot {
                    it.referenceId in merged.nonPersistentReferenceIds
                })
                val updated = CredentialStatusStoredRecord(Uuid.random().toString(),
                    persistent.takeIf { it.entries.isNotEmpty() }?.let {
                        PersistedCredentialStatus(credentialFingerprint = record.fingerprint, trustPolicyId = policy, metadata = it)
                    })
                if (snapshotStore is CredentialStatusNoticeStore) {
                    // A dismissal can reach either storage location as the same notice moves to memory.
                    mutex.withLock {
                        val state = currentRefreshState(record, version) ?: return removed(record)
                        val at = now()
                        var transferred: CredentialStatusNoticeState? = null
                        val commit = snapshotStore.compareAndSetCredentialStatusAndNotice(current, expected, updated) { previous ->
                            val next = persistent.noticeTransition(previous, resolved.metadata, at)
                            // Reuse the acknowledgement, with the timestamp from the newly retainable evidence.
                            val retained = if (next != null && next.version != previous?.version) {
                                val live = state.sessionNotice?.takeIf { it.evidenceRevision == expected?.revision }
                                    ?.value?.takeIf { it.kind == next.kind }
                                next.copy(
                                    version = live?.version ?: next.version,
                                    acknowledged = live?.acknowledged ?: next.acknowledged,
                                    previousEvidenceRevision = expected?.revision,
                                )
                            } else next
                            transferred = previous?.takeIf {
                                it.referenceId in merged.nonPersistentReferenceIds && it.version != retained?.version
                            }
                            retained
                        }
                        if (commit != CredentialStatusCommit.LOST) {
                            if (commit == CredentialStatusCommit.NOTICE_CREATED) onNoticeCommitted()
                            val sessionEvidence = CredentialStatus.references(merged.metadata.entries.filter {
                                it.referenceId in merged.nonPersistentReferenceIds
                            })
                            val sessionNotice = sessionEvidence.noticeTransition(state.sessionNotice?.value, resolved.metadata, at, transferred)
                            return finishRefresh(record.reference, merged, updated, sessionNotice)
                        }
                    }
                } else if (snapshotStore.compareAndSetCredentialStatus(current, expected, updated)) {
                    committed = updated
                    break
                }
                if (currentStore(record.reference) !== record.store || record.current()?.statusFingerprint() != record.fingerprint) {
                    return removed(record)
                }
                val latest = snapshotStore.readCredentialStatusRecord(record.credential.id)
                if (latest == expected || latest?.snapshot == null) {
                    return CredentialStatusRefreshResult(record.reference, CredentialStatusRefreshOutcome.DEFERRED, local(record))
                }
                val saved = latest.snapshot
                if (saved.schemaVersion != 2 || saved.credentialFingerprint != record.fingerprint ||
                    policy == null || saved.trustPolicyId != policy) {
                    return CredentialStatusRefreshResult(record.reference, CredentialStatusRefreshOutcome.DEFERRED, local(record))
                }
                val metadata = reconcile(merged.metadata, saved.metadata)
                merged = merged.copy(metadata = metadata, nonPersistentReferenceIds =
                    retainedRestrictions(metadata, merged.metadata, merged.nonPersistentReferenceIds))
                expected = latest
            }
            if (committed == null) return CredentialStatusRefreshResult(record.reference, CredentialStatusRefreshOutcome.DEFERRED, local(record))
        }
        currentCoroutineContext().ensureActive()
        return mutex.withLock {
            val state = currentRefreshState(record, version) ?: return@withLock removed(record)
            val sessionNotice = merged.metadata.noticeTransition(state.sessionNotice?.value, resolved.metadata, now())
            finishRefresh(record.reference, merged, committed, sessionNotice)
        }
    }

    private fun currentRefreshState(record: Record, version: Long): State? {
        check(job.isActive) { "Credential status service is closed" }
        return states[record.reference]?.takeIf {
            currentStore(record.reference) === record.store && it.store === record.store &&
                it.fingerprint == record.fingerprint && it.generation == version
        }
    }

    private fun removed(record: Record) =
        CredentialStatusRefreshResult(record.reference, CredentialStatusRefreshOutcome.REMOVED, CredentialStatus.references())

    private fun finishRefresh(
        reference: WalletCredentialReference,
        state: State,
        stored: CredentialStatusStoredRecord?,
        sessionNotice: CredentialStatusNoticeState?,
    ): CredentialStatusRefreshResult {
        states[reference] = state.copy(
            persisted = stored,
            sessionNotice = sessionNotice?.let { SessionNotice(it, stored?.revision) },
        )
        if (state.metadata.hasCheckError) retryAfter[reference] = now() + 30.seconds
        else retryAfter.remove(reference)
        changes.tryEmit(Unit)
        return CredentialStatusRefreshResult(reference, CredentialStatusRefreshOutcome.COMPLETED, state.metadata)
    }

    internal suspend fun closeAndJoin() {
        close()
        job.join()
    }

    override fun close() {
        job.cancel()
        resolver.close()
        ownedFetcher?.close()
    }
}
