package id.walt.wallet2.handlers

import id.waltid.openid4vci.wallet.attestation.ClientAttestationChallengeContext
import id.waltid.openid4vci.wallet.attestation.WalletAttestationChallengeResult
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import org.kotlincrypto.hash.sha2.SHA256
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Private runtime state for one wallet's issuance transitions, shared across request-scoped engines.
 * Keep one instance per wallet in a service process. Each engine still resolves keys and writes
 * credentials through its current Wallet, so sharing state does not share caller permissions.
 * [store] provides restart recovery; this state coordinates callers within the current process.
 */
class WalletIssuanceSessionState(
    val walletId: String,
    internal val store: WalletIssuanceSessionStore? = null,
) {
    internal val mutex = Mutex()
    internal var closed = false
    internal val sessions = LinkedHashMap<String, WalletIssuanceSessionService.ActiveSession>()
    internal val deferred = LinkedHashMap<String, WalletIssuanceSessionService.DeferredRecord>()
    internal val pollingDeferred = mutableSetOf<String>()

    // Owned by this wallet generation; never shared through a process-global issuer cache.
    private val challenges = LinkedHashMap<ChallengeScope, ClientAttestationChallengeContext>()
    private val challengeMutex = Mutex()
    private val challengeAccess = mutableMapOf<ChallengeScope, Long>()
    @Serializable
    private data class ChallengeScope(val issuer: String, val clientId: String, val keyThumbprint: String, val flowId: String)
    @Serializable
    private data class PersistedFreshness(val scope: ChallengeScope, val challenge: String?, val dpopNonce: String?, val prepared: Boolean = false)

    internal suspend fun challengeContext(
        issuer: String, clientId: String, keyThumbprint: String,
        initialChallenge: String? = null, initialDpopNonce: String? = null,
        flowId: String = "legacy",
    ): ClientAttestationChallengeContext = challengeMutex.withLock {
        check(!closed) { "Wallet issuance is closed" }
        val now = Clock.System.now().toEpochMilliseconds()
        // Split authorization calls have no retained session lifecycle. Bound their correlation
        // lifetime; retained session records are removed by their own completion/expiry path.
        val cutoff = now - 10 * 60 * 1000
        challengeAccess.filter { (scope, touched) -> scope.flowId.startsWith("pkce:") && touched < cutoff }
            .keys.toList().forEach { challenges.remove(it); challengeAccess.remove(it) }
        store?.list()?.filter { it.kind == WalletIssuanceSessionRecordKind.CLIENT_ATTESTATION_FRESHNESS &&
            it.id.startsWith("attestation-flow:") && it.updatedAtEpochMilliseconds < cutoff }?.forEach { record ->
            val expired = Json.decodeFromString<PersistedFreshness>(record.payload).scope
            if (expired.flowId.startsWith("pkce:") && compareAndSet(record, null)) {
                challenges.remove(expired)
                challengeAccess.remove(expired)
            }
        }
        val scope = ChallengeScope(issuer, clientId, keyThumbprint, flowId)
        challengeAccess[scope] = now
        challenges.getOrPut(scope) {
            // Prior wallet-wide records must never mark a new issuance flow as prepared.
            val id = "attestation-flow:" + SHA256().digest(Json.encodeToString(scope).encodeToByteArray()).encodeToBase64Url()
            var snapshot: WalletIssuanceSessionRecord? = null
            fun decode(record: WalletIssuanceSessionRecord?): PersistedFreshness {
                if (record == null) return PersistedFreshness(scope, null, null)
                require(record.kind == WalletIssuanceSessionRecordKind.CLIENT_ATTESTATION_FRESHNESS && record.sessionId == id)
                return Json.decodeFromString<PersistedFreshness>(record.payload).also {
                    require(it.scope == scope) { "Stored attestation freshness scope is invalid" }
                }
            }
            fun PersistedFreshness.result() = WalletAttestationChallengeResult(challenge, dpopNonce, prepared)
            suspend fun persist(challenge: String?, nonce: String?, onlyIfAbsent: Boolean, prepared: Boolean): WalletAttestationChallengeResult {
                val backingStore = requireNotNull(store)
                var expected = snapshot
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val current = decode(expected)
                    val next = PersistedFreshness(scope,
                        if (onlyIfAbsent) current.challenge ?: challenge else challenge ?: current.challenge,
                        if (onlyIfAbsent) current.dpopNonce ?: nonce else nonce ?: current.dpopNonce,
                        current.prepared || prepared)
                    if (next == current) {
                        snapshot = expected
                        return current.result()
                    }
                    val replacement = WalletIssuanceSessionRecord(id, id,
                        WalletIssuanceSessionRecordKind.CLIENT_ATTESTATION_FRESHNESS,
                        Json.encodeToString(next),
                        maxOf(Clock.System.now().toEpochMilliseconds(), (expected?.updatedAtEpochMilliseconds ?: 0) + 1))
                    val written = if (expected == null) putIfAbsent(replacement) else compareAndSet(expected, replacement)
                    if (written) {
                        snapshot = replacement
                        return next.result()
                    }
                    // Serialize overlapping observations by successful writes. Rebase only supplied
                    // fields, preserving the other response's independent freshness value. Restored
                    // snapshots remain fill-only even if a live response won the race.
                    expected = backingStore.get(id)
                    yield()
                }
            }
            ClientAttestationChallengeContext(
                load = { snapshot = store?.get(id); snapshot?.let { decode(it).result() } },
                persist = if (store == null) null else ::persist,
            )
        }.also { it.restore(initialChallenge, initialDpopNonce) }
    }

    internal suspend fun removeChallengeFlow(flowId: String) = challengeMutex.withLock {
        challenges.keys.removeAll { it.flowId == flowId }
        challengeAccess.keys.removeAll { it.flowId == flowId }
        store?.list()?.filter { it.kind == WalletIssuanceSessionRecordKind.CLIENT_ATTESTATION_FRESHNESS &&
            it.id.startsWith("attestation-flow:") }?.forEach { record ->
            if (Json.decodeFromString<PersistedFreshness>(record.payload).scope.flowId == flowId) {
                compareAndSet(record, null)
            }
        }
    }

    internal suspend fun clearChallenges() = challengeMutex.withLock { challenges.clear(); challengeAccess.clear() }

    private val storeUpdates = Mutex()

    private suspend fun putIfAbsent(record: WalletIssuanceSessionRecord): Boolean {
        val backingStore = requireNotNull(store)
        if (backingStore is AtomicWalletIssuanceSessionCreationStore) return backingStore.putIfAbsent(record)
        // Released adapters retain their existing single-runtime coordination contract.
        return storeUpdates.withLock {
            if (backingStore.get(record.id) != null) false else { backingStore.put(record); true }
        }
    }

    internal suspend fun compareAndSet(
        expected: WalletIssuanceSessionRecord,
        replacement: WalletIssuanceSessionRecord?,
    ): Boolean {
        val backingStore = store ?: return false
        if (backingStore is AtomicWalletIssuanceSessionStore) return backingStore.compareAndSet(expected, replacement)
        require(replacement == null || replacement.id == expected.id) { "Cannot change a continuation record ID" }
        // Released stores have no cross-runtime atomicity contract. Serialize their existing
        // read/write operations only within this shared wallet runtime.
        return storeUpdates.withLock {
            if (backingStore.get(expected.id) != expected) false
            else if (replacement == null) backingStore.remove(expected.id)
            else { backingStore.put(replacement); true }
        }
    }
}
