package id.walt.wallet2.stores.inmemory

import id.walt.wallet2.handlers.WalletIssuanceSessionRecord
import id.walt.wallet2.handlers.WalletIssuanceSessionRecordKind
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class InMemoryIssuanceSessionStoreTest {
    @Test
    fun conditionalUpdateRejectsMissingStaleAndCompetingClaims() = runTest {
        val store = InMemoryIssuanceSessionStore()
        val original = WalletIssuanceSessionRecord("target", "session",
            WalletIssuanceSessionRecordKind.DEFERRED_CREDENTIAL, "pending", 123)
        val claimed = original.copy(payload = "claimed", updatedAtEpochMilliseconds = 456)
        assertFalse(store.compareAndSet(original, claimed))
        store.put(original)
        val claims = (1..2).map { async { store.compareAndSet(original, claimed) } }.awaitAll()
        assertEquals(1, claims.count { it })
        assertEquals(claimed, store.get(original.id))
        assertFalse(store.compareAndSet(original, claimed))
        assertFailsWith<IllegalArgumentException> { store.compareAndSet(claimed, claimed.copy(id = "other")) }
        assertFalse(store.compareAndSet(original, null))
        assertTrue(store.compareAndSet(claimed, null))
        assertFalse(store.compareAndSet(claimed, original))
    }
}
