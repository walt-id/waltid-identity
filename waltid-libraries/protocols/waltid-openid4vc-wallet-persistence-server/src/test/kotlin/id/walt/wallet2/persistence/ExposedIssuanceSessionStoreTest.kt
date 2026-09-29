package id.walt.wallet2.persistence

import id.walt.wallet2.data.WalletDescriptor
import id.walt.wallet2.handlers.WalletIssuanceSessionRecord
import id.walt.wallet2.handlers.WalletIssuanceSessionRecordKind
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExposedIssuanceSessionStoreTest {
    @Test
    fun recordsRemainWalletScopedAcrossRecreationReplacementRemovalAndWalletDeletion() = runTest {
        val db = initWallet2Database(Wallet2PersistenceConfig(
            jdbcUrl = "jdbc:sqlite::memory:", maximumPoolSize = 1, minimumIdle = 1))
        val wallets = ExposedWalletStore(db)
        wallets.saveDescriptor(WalletDescriptor(id = "first"))
        wallets.saveDescriptor(WalletDescriptor(id = "second"))
        val first = ExposedIssuanceSessionStore("first", db)
        val second = ExposedIssuanceSessionStore("second", db)
        val original = WalletIssuanceSessionRecord("shared-id", "session",
            WalletIssuanceSessionRecordKind.DEFERRED_CREDENTIAL, "first-sensitive-payload", 123)
        val other = original.copy(payload = "second-sensitive-payload")
        first.put(original)
        second.put(other)
        val restored = ExposedIssuanceSessionStore("first", db)
        assertEquals(original, restored.get(original.id))
        assertEquals(listOf(original), restored.list())
        val updated = original.copy(payload = "received-response", updatedAtEpochMilliseconds = 456)
        for (stale in listOf(original.copy(payload = "stale"), original.copy(sessionId = "stale"),
            original.copy(kind = WalletIssuanceSessionRecordKind.ACTIVE_SESSION), original.copy(updatedAtEpochMilliseconds = 0))) {
            assertFalse(restored.compareAndSet(stale, updated))
            assertFalse(restored.compareAndSet(stale, null))
        }
        assertFalse(second.compareAndSet(original, updated))
        val claims = coroutineScope {
            listOf(first, restored).map { adapter -> async(Dispatchers.Default) { adapter.compareAndSet(original, updated) } }.awaitAll()
        }
        assertEquals(1, claims.count { it })
        assertFalse(restored.compareAndSet(original, updated))
        assertEquals(updated, first.get(original.id))
        assertEquals(other, second.get(original.id))
        assertTrue(first.compareAndSet(updated, null))
        assertFalse(restored.compareAndSet(updated, original))
        assertFalse(first.remove(original.id))
        assertNull(restored.get(original.id))
        assertEquals(listOf(other), second.list())
        restored.put(updated)
        wallets.deleteWallet("first")
        assertFailsWith<IllegalStateException> { restored.put(updated) }
        assertFalse(restored.compareAndSet(updated, original))
        assertTrue(first.list().isEmpty())
        assertEquals(listOf(other), second.list())
    }
}
