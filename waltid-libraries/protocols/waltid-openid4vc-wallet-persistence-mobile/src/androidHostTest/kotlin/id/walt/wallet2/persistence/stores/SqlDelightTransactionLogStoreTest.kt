package id.walt.wallet2.persistence.stores

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import id.walt.wallet2.persistence.db.WalletPersistenceDatabase
import id.walt.wallet2.persistence.db.WalletPersistenceQueries
import id.walt.wallet2.transactionlog.CredentialDeletion
import id.walt.wallet2.transactionlog.CredentialDeletionTransaction
import id.walt.wallet2.transactionlog.Presentation
import id.walt.wallet2.transactionlog.PresentationTransaction
import id.walt.wallet2.transactionlog.TransactionLogQuery
import id.walt.wallet2.transactionlog.TransactionLogSortField
import id.walt.wallet2.transactionlog.TransactionLogSortOrder
import id.walt.wallet2.transactionlog.TransactionResult
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.sql.SQLException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqlDelightTransactionLogStoreTest {
    @Test
    fun `round trips polymorphic envelopes and paginates in both directions`() = runTest {
        withDatabase { queries ->
            val store = SqlDelightTransactionLogStore(queries)
            assertTrue(store.read().entries.isEmpty())
            assertNull(store.read().nextCursor)
            val declined = PresentationTransaction(
                transactionIdentifier = "c",
                time = LocalDateTime.parse("2026-10-19T19:00:00"),
                transactionResult = TransactionResult.NotCompleted,
                presentation = Presentation(
                    listOfClaimsRequested = emptyList(),
                    listOfClaimsPresented = emptyList(),
                    reasonOfNoncompletion = "User declined",
                ),
            )
            val entries = listOf(deletion("z", 18), deletion("a"), declined, deletion("d"), deletion("e"))
            entries.reversed().forEach { store.store(it) }

            for (order in TransactionLogSortOrder.entries) {
                val first = store.read(TransactionLogQuery(limit = 2, sortBy = TransactionLogSortField.Time, sortOrder = order))
                val cursor = assertNotNull(first.nextCursor)
                assertEquals(TransactionLogSortField.Time, cursor.sortBy)
                assertEquals(order, cursor.sortOrder)
                assertEquals(first.entries.last().time, LocalDateTime.parse(cursor.sortValue))
                val second = store.read(TransactionLogQuery(limit = 2, sortOrder = order, cursor = assertNotNull(first.nextCursor)))
                val last = store.read(TransactionLogQuery(limit = 2, sortOrder = order, cursor = assertNotNull(second.nextCursor)))
                val expected = if (order == TransactionLogSortOrder.Ascending) entries else entries.reversed()
                assertEquals(expected, first.entries + second.entries + last.entries)
                assertEquals(listOf(2, 2, 1), listOf(first, second, last).map { it.entries.size })
                assertNull(last.nextCursor)
            }
            val payload = Json.parseToJsonElement(queries.selectTransactionLogNewestFirst(null, null, 1).executeAsOne()).jsonObject
            assertEquals("CredentialDeletion", payload.getValue("transactionType").jsonPrimitive.content)
            assertEquals("urn:example:pid", payload.getValue("credentialDeletion").jsonObject
                .getValue("credentialIdentifier").jsonPrimitive.content)
            assertEquals(entries.reversed(), store.read(TransactionLogQuery(limit = Int.MAX_VALUE)).entries)
        }
    }

    @Test
    fun `fractional seconds round trip and equivalent ISO cursors paginate consistently`() = runTest {
        withDatabase { queries ->
            val store = SqlDelightTransactionLogStore(queries)
            val entries = listOf(
                deletion("a"),
                deletion("b"),
                deletion("c").copy(time = LocalDateTime(2026, 10, 19, 19, 0, 0, 100000000)),
                deletion("d").copy(time = LocalDateTime(2026, 10, 19, 19, 0, 0, 123456789)),
            )
            entries.reversed().forEach { store.store(it) }

            for (order in TransactionLogSortOrder.entries) {
                val first = store.read(TransactionLogQuery(limit = 2, sortOrder = order))
                val cursor = assertNotNull(first.nextCursor)
                val second = store.read(TransactionLogQuery(sortOrder = order, cursor = cursor))
                val expected = if (order == TransactionLogSortOrder.Ascending) entries else entries.reversed()
                assertEquals(expected, first.entries + second.entries)
                val equivalentTime = if (order == TransactionLogSortOrder.Ascending) {
                    "2026-10-19T19:00"
                } else {
                    "2026-10-19T19:00:00.100"
                }
                val equivalentCursor = cursor.copy(sortValue = equivalentTime)
                assertEquals(second, store.read(TransactionLogQuery(sortOrder = order, cursor = equivalentCursor)))
            }
        }
    }

    @Test
    fun `default read returns only the fifty newest entries`() = runTest {
        withDatabase { queries ->
            val store = SqlDelightTransactionLogStore(queries)
            val entries = (0..50).map { deletion(it.toString().padStart(2, '0')) }
            entries.forEach { store.store(it) }
            val first = store.read()

            assertEquals(entries.reversed().take(50), first.entries)
            val last = store.read(TransactionLogQuery(cursor = assertNotNull(first.nextCursor)))
            assertEquals(listOf(entries.first()), last.entries)
            assertNull(last.nextCursor)
        }
    }

    @Test
    fun `insertion before cursor does not shift pages and boundary need not exist`() = runTest {
        for (order in TransactionLogSortOrder.entries) {
            withDatabase { queries ->
                val store = SqlDelightTransactionLogStore(queries)
                val original = listOf(deletion("a"), deletion("b"), deletion("c"))
                original.forEach { store.store(it) }
                val first = store.read(TransactionLogQuery(limit = 1, sortOrder = order))
                val cursor = assertNotNull(first.nextCursor)
                store.store(deletion("inserted", if (order == TransactionLogSortOrder.Ascending) 18 else 20))
                val rest = store.read(TransactionLogQuery(sortOrder = order, cursor = cursor))

                val expected = if (order == TransactionLogSortOrder.Ascending) original else original.reversed()
                assertEquals(expected, first.entries + rest.entries)
                assertNull(rest.nextCursor)

                assertTrue(store.remove(cursor.transactionIdentifier))
                assertEquals(rest.entries, store.read(TransactionLogQuery(sortOrder = order, cursor = cursor)).entries)
            }
        }
    }

    @Test
    fun `removing a single entry preserves other history and wallet data`() = runTest {
        withDatabase { queries ->
            val store = SqlDelightTransactionLogStore(queries)
            queries.insertDid("did:example:holder", """{"id":"did:example:holder"}""")
            listOf("a", "b", "c").forEach { store.store(deletion(it)) }

            assertTrue(store.remove("b"))
            assertFalse(store.remove("b"))
            assertFalse(store.remove("missing"))
            assertEquals(listOf(deletion("c"), deletion("a")), store.read().entries)
            assertEquals("did:example:holder", queries.selectAllDids().executeAsOne().did)
        }
    }

    @Test
    fun `removing a single entry does not require a valid payload`() = runTest {
        withDatabase { queries ->
            val store = SqlDelightTransactionLogStore(queries)
            store.store(deletion("valid"))
            queries.insertTransactionLogEntry("invalid", "2026-10-19T19:00:00", "{}")

            assertTrue(store.remove("invalid"))
            assertEquals(listOf(deletion("valid")), store.read().entries)
        }
    }

    @Test
    fun `duplicate identifiers fail without overwriting history`() = runTest {
        withDatabase { queries ->
            val store = SqlDelightTransactionLogStore(queries)
            val original = deletion("same")
            store.store(original)

            assertFailsWith<SQLException> { store.store(original.copy(time = LocalDateTime.parse("2026-10-20T19:00:00"))) }
            assertEquals(listOf(original), store.read().entries)
        }
    }

    @Test
    fun `corrupt history fails and clearing preserves other wallet data`() = runTest {
        withDatabase { queries ->
            val store = SqlDelightTransactionLogStore(queries)
            queries.insertDid("did:example:holder", """{"id":"did:example:holder"}""")
            store.store(deletion("valid"))
            queries.insertTransactionLogEntry("invalid", "2026-10-19T19:00:00", "{}")

            assertFailsWith<SerializationException> { store.read() }
            store.clear()
            store.clear()
            assertTrue(store.read().entries.isEmpty())
            assertEquals("did:example:holder", queries.selectAllDids().executeAsOne().did)
        }
    }

    @Test
    fun `entries and cursors survive database reopening`() = runTest {
        val file = Files.createTempFile("wallet-transaction-log-", ".db")
        try {
            val cursor = JdbcSqliteDriver("jdbc:sqlite:$file").use { driver ->
                WalletPersistenceDatabase.Schema.create(driver)
                val store = SqlDelightTransactionLogStore(WalletPersistenceDatabase(driver).walletPersistenceQueries)
                store.store(deletion("a"))
                store.store(deletion("b"))
                assertNotNull(store.read(TransactionLogQuery(limit = 1)).nextCursor)
            }
            JdbcSqliteDriver("jdbc:sqlite:$file").use { driver ->
                val store = SqlDelightTransactionLogStore(WalletPersistenceDatabase(driver).walletPersistenceQueries)
                assertEquals(listOf(deletion("b"), deletion("a")), store.read().entries)
                assertEquals(listOf(deletion("a")), store.read(TransactionLogQuery(cursor = cursor)).entries)
            }
        } finally {
            Files.deleteIfExists(file)
        }
    }

    private fun deletion(id: String, day: Int = 19) = CredentialDeletionTransaction(
        transactionIdentifier = id,
        time = LocalDateTime(2026, 10, day, 19, 0, 0),
        transactionResult = TransactionResult.Completed,
        credentialDeletion = CredentialDeletion("urn:example:pid"),
    )

    private suspend fun withDatabase(block: suspend (WalletPersistenceQueries) -> Unit) {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            WalletPersistenceDatabase.Schema.create(driver)
            block(WalletPersistenceDatabase(driver).walletPersistenceQueries)
        }
    }
}
