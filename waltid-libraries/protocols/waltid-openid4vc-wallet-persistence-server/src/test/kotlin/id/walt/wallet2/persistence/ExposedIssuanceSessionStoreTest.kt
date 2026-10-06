package id.walt.wallet2.persistence

import id.walt.credentials.formats.MdocsCredential
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.handlers.WalletIssuanceSessionService
import kotlinx.serialization.json.buildJsonObject
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.core.eq
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.io.path.createTempFile
import kotlin.test.assertNotNull
import kotlin.test.assertNotEquals
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
        val db = database()
        val wallets = ExposedWalletStore(db)
        wallets.saveDescriptor(WalletDescriptor(id = "first"))
        wallets.saveDescriptor(WalletDescriptor(id = "second"))
        val first = ExposedStoreRegistry(db).issuanceSessionStore("first")
        val second = ExposedStoreRegistry(db).issuanceSessionStore("second")
        val original = WalletIssuanceSessionRecord("shared-id", "session",
            WalletIssuanceSessionRecordKind.DEFERRED_CREDENTIAL, "first-sensitive-payload", 123)
        val other = original.copy(payload = "second-sensitive-payload")
        first.put(original)
        second.put(other)
        val restored = ExposedStoreRegistry(db).issuanceSessionStore("first")
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

    @Test
    fun staleWalletAdaptersCannotWriteToSharedStoresOrTouchARecreatedWallet() = runTest {
        val db = database()
        val wallets = ExposedWalletStore(db)
        val descriptor = WalletDescriptor(id = "wallet", credentialStoreIds = listOf("shared"))
        wallets.saveDescriptor(descriptor)
        wallets.saveDescriptor(descriptor.copy(id = "other"))
        // Simulate an existing row after the nullable generation column is added on upgrade.
        suspendTransaction(db) {
            Wallet2Tables.Wallets.update({ Wallet2Tables.Wallets.id eq descriptor.id }) {
                it[Wallet2Tables.Wallets.generation] = null
            }
        }
        val registry = ExposedStoreRegistry(db)
        val oldRecords = registry.issuanceSessionStore(descriptor.id)
        val oldCredentials = assertNotNull(registry.resolveCredentialStoreForWallet(descriptor.id, "shared"))
        val record = WalletIssuanceSessionRecord("same-id", "session",
            WalletIssuanceSessionRecordKind.DEFERRED_CREDENTIAL, "same-payload", 123)
        oldRecords.put(record)
        wallets.saveDescriptor(descriptor.copy(defaultDidId = "new-default"))
        assertEquals(oldRecords.walletGeneration, registry.issuanceSessionStore(descriptor.id).walletGeneration)
        oldCredentials.addCredential(credential("before-delete"))
        wallets.deleteWallet(descriptor.id)
        wallets.saveDescriptor(descriptor)
        val newRecords = registry.issuanceSessionStore(descriptor.id)
        assertNotEquals(oldRecords.walletGeneration, newRecords.walletGeneration)
        newRecords.put(record)
        assertFailsWith<IllegalStateException> { oldCredentials.addCredential(credential("late-write")) }
        assertFailsWith<IllegalStateException> { oldRecords.put(record.copy(id = "late-record")) }
        assertFalse(oldRecords.compareAndSet(record, null))
        assertFalse(oldRecords.remove(record.id))
        assertNull(oldRecords.get(record.id))
        assertTrue(oldRecords.list().isEmpty())
        assertEquals(record, newRecords.get(record.id))
        val fresh = assertNotNull(registry.resolveCredentialStoreForWallet(descriptor.id, "shared"))
        fresh.addCredential(credential("new-generation"))
        assertNotNull(fresh.getCredential("before-delete")) // The other wallet still owns the shared store.
        assertNull(fresh.getCredential("late-write"))
        wallets.saveDescriptor(descriptor.copy(credentialStoreIds = emptyList()))
        assertFailsWith<IllegalStateException> { fresh.addCredential(credential("detached-write")) }
    }

    @Test
    fun failedTransactionalDeletionPreservesContinuationsAndAllowsLaterCommittedDeletion() = runTest {
        val db = database()
        val wallets = ExposedWalletStore(db)
        wallets.saveDescriptor(WalletDescriptor(id = "wallet"))
        val records = ExposedStoreRegistry(db).issuanceSessionStore("wallet")
        val record = WalletIssuanceSessionRecord("pending", "session",
            WalletIssuanceSessionRecordKind.DEFERRED_CREDENTIAL, "uncertain-remote-response", 123)
        records.put(record)
        val sessions = WalletIssuanceSessionService(Wallet("wallet"), sessionStore = records)
        assertFailsWith<IllegalStateException> {
            sessions.closeSessions {
                suspendTransaction(db) {
                    wallets.deleteWallet("wallet")
                    error("Rollback the parent and continuation deletion")
                }
            }
        }
        assertNotNull(wallets.loadDescriptor("wallet"))
        assertEquals(record, records.get(record.id))
        sessions.closeSessions { wallets.deleteWallet("wallet") }
        assertNull(wallets.loadDescriptor("wallet"))
        assertTrue(records.list().isEmpty())
    }

    @Test
    fun deletionWaitsForAnAlreadyAdmittedCredentialWriteAcrossConnections() = runTest {
        val db = database(poolSize = 2)
        val wallets = ExposedWalletStore(db)
        wallets.saveDescriptor(WalletDescriptor(id = "wallet", credentialStoreIds = listOf("credentials")))
        val scope = ExposedWalletScope.resolve("wallet", db)
        val credentials = assertNotNull(ExposedStoreRegistry(db).resolveCredentialStoreForWallet("wallet", "credentials"))
        val admitted = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val writer = async(Dispatchers.IO) {
            suspendTransaction(db) {
                scope.requireCurrentForWrite()
                admitted.complete(Unit)
                release.await()
                credentials.addCredential(credential("in-flight"))
            }
        }
        admitted.await()
        val deleting = CompletableDeferred<Unit>()
        val deletion = async(Dispatchers.IO) {
            deleting.complete(Unit)
            wallets.deleteWallet("wallet")
        }
        deleting.await()
        try {
            assertNull(withContext(Dispatchers.IO) { withTimeoutOrNull(250) { deletion.await(); true } })
        } finally { release.complete(Unit) }
        writer.await()
        deletion.await()
        assertNull(wallets.loadDescriptor("wallet"))
        assertNull(credentials.getCredential("in-flight"))
        assertFailsWith<IllegalStateException> { credentials.addCredential(credential("after-delete")) }
    }

    /** The optional URL must point to a disposable fixture with the wallet_test role/password. */
    private fun database(poolSize: Int = 1): org.jetbrains.exposed.v1.jdbc.Database {
        val postgresUrl = System.getenv("WALLET2_TEST_POSTGRES_URL")
        val config = if (postgresUrl != null) {
            val schema = "wallet_test_" + java.util.UUID.randomUUID().toString().replace("-", "")
            java.sql.DriverManager.getConnection(postgresUrl, "wallet_test", "wallet_test").use { connection ->
                connection.createStatement().use { it.execute("CREATE SCHEMA $schema") }
            }
            Wallet2PersistenceConfig(
                jdbcUrl = postgresUrl + (if ('?' in postgresUrl) "&" else "?") + "currentSchema=$schema",
                driverClassName = "org.postgresql.Driver", username = "wallet_test", password = "wallet_test",
                maximumPoolSize = poolSize, minimumIdle = poolSize)
        } else {
            val url = if (poolSize == 1) "jdbc:sqlite::memory:" else {
                val file = createTempFile("wallet-lifecycle-", ".db").toFile().apply { deleteOnExit() }
                "jdbc:sqlite:${file.absolutePath}"
            }
            Wallet2PersistenceConfig(jdbcUrl = url, maximumPoolSize = poolSize, minimumIdle = poolSize)
        }
        return initWallet2Database(config)
    }

    private fun credential(id: String) = StoredCredential(id,
        MdocsCredential(credentialData = buildJsonObject { }, signed = null, docType = "org.iso.18013.5.1.mDL"))

}
