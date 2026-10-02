package id.walt.wallet2.stores.inmemory

import id.walt.wallet2.handlers.WalletIssuanceSessionRecord
import id.walt.wallet2.handlers.AtomicWalletIssuanceSessionStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Process-local continuations. Create a separate instance for each wallet. */
class InMemoryIssuanceSessionStore : AtomicWalletIssuanceSessionStore {
    private val records = linkedMapOf<String, WalletIssuanceSessionRecord>()
    private val mutex = Mutex()

    override suspend fun get(id: String) = mutex.withLock { records[id] }
    override suspend fun list() = mutex.withLock { records.values.toList() }
    override suspend fun put(record: WalletIssuanceSessionRecord) { mutex.withLock { records[record.id] = record } }
    override suspend fun compareAndSet(expected: WalletIssuanceSessionRecord, replacement: WalletIssuanceSessionRecord?): Boolean {
        require(replacement == null || expected.id == replacement.id) { "Cannot change a continuation record ID" }
        return mutex.withLock {
            if (records[expected.id] != expected) false else {
                if (replacement == null) records.remove(expected.id) else records[expected.id] = replacement
                true
            }
        }
    }
    override suspend fun remove(id: String) = mutex.withLock { records.remove(id) != null }
}
