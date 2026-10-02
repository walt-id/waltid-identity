package id.walt.wallet2.persistence.stores

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import id.walt.wallet2.handlers.WalletIssuanceSessionRecord
import id.walt.wallet2.handlers.WalletIssuanceSessionRecordKind
import id.walt.wallet2.persistence.db.WalletPersistenceDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.*

class SqlDelightIssuanceSessionStoreTest {
    @Test
    fun conditionalUpdatesCoordinateSeparateDatabaseConnections() = runTest {
        val file = Files.createTempFile("wallet-issuance-cas-", ".db")
        val firstDriver = JdbcSqliteDriver("jdbc:sqlite:$file")
        val secondDriver = JdbcSqliteDriver("jdbc:sqlite:$file")
        try {
            WalletPersistenceDatabase.Schema.create(firstDriver)
            val first = SqlDelightIssuanceSessionStore(WalletPersistenceDatabase(firstDriver).walletPersistenceQueries)
            val second = SqlDelightIssuanceSessionStore(WalletPersistenceDatabase(secondDriver).walletPersistenceQueries)
            val original = WalletIssuanceSessionRecord("target", "session",
                WalletIssuanceSessionRecordKind.DEFERRED_CREDENTIAL, "pending", 123)
            val claimed = original.copy(payload = "claimed", updatedAtEpochMilliseconds = 456)
            assertFalse(first.compareAndSet(original, claimed))
            first.put(original)
            for (stale in listOf(original.copy(payload = "stale"), original.copy(sessionId = "stale"),
                original.copy(kind = WalletIssuanceSessionRecordKind.ACTIVE_SESSION), original.copy(updatedAtEpochMilliseconds = 0))) {
                assertFalse(second.compareAndSet(stale, claimed))
                assertFalse(second.compareAndSet(stale, null))
            }
            val claims = listOf(first, second).map { store ->
                async(Dispatchers.Default) { store.compareAndSet(original, claimed) }
            }.awaitAll()
            assertEquals(1, claims.count { it })
            assertEquals(claimed, first.get(original.id))
            assertEquals(claimed, second.get(original.id))
            assertFalse(first.compareAndSet(original, claimed))
            assertFailsWith<IllegalArgumentException> { first.compareAndSet(claimed, claimed.copy(id = "other")) }
            assertFalse(first.compareAndSet(original, null))
            assertTrue(second.compareAndSet(claimed, null))
            assertFalse(first.compareAndSet(claimed, original))
            assertNull(first.get(original.id))
        } finally {
            firstDriver.close()
            secondDriver.close()
            Files.deleteIfExists(file)
        }
    }
}
