package id.walt.wallet2.handlers

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

    private val storeUpdates = Mutex()

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
