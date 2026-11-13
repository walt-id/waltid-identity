package id.walt.wallet2.persistence.stores

import kotlinx.serialization.json.Json
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import id.walt.credentials.CredentialParser
import id.walt.credentials.examples.MdocsExamples
import id.walt.credentials.status.CredentialStatus
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.persistence.db.WalletPersistenceDatabase
import id.walt.wallet2.status.PersistedCredentialStatus
import id.walt.wallet2.status.statusFingerprint
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class SqlDelightCredentialStatusStoreTest {
    @Test
    fun migrationPreservesAnExistingCredentialAndSnapshotRestartAndDeletionAreAtomic() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            driver.execute(null, "CREATE TABLE credentials (id TEXT NOT NULL PRIMARY KEY, serialized_credential TEXT NOT NULL, format TEXT NOT NULL DEFAULT '', label TEXT, added_at INTEGER NOT NULL, metadata TEXT, holder_key_binding TEXT)", 0)
            val raw = MdocsExamples.mdocsExampleBase64Url
            driver.execute(null, "INSERT INTO credentials (id, serialized_credential, format, added_at) VALUES ('resident', ?, 'mso_mdoc', 1000)", 1) { bindString(0, raw) }
            WalletPersistenceDatabase.Schema.migrate(driver, 1, 2)
            val database = WalletPersistenceDatabase(driver)
            val store = SqlDelightCredentialStore(database.walletPersistenceQueries)
            val credential = assertNotNull(store.getCredential("resident"))
            assertEquals(raw, credential.credential.signed)
            val at = Instant.fromEpochSeconds(1_800_000_000)
            val snapshot = PersistedCredentialStatus(credentialFingerprint = credential.statusFingerprint(), trustPolicyId = "test-v1",
                metadata = Json.decodeFromString(CredentialStatus.serializer(), """{"type":"references","entries":[{"type":"mso","referenceId":"ref","check":{"type":"observed","observation":{"value":"REVOKED","checkedAt":"$at","freshUntil":"$at"}}}]}"""))
            assertTrue(store.saveCredentialStatusIfCurrent(credential, snapshot))
            assertEquals(snapshot, SqlDelightCredentialStore(WalletPersistenceDatabase(driver).walletPersistenceQueries).readCredentialStatus("resident"))
            assertTrue(store.removeCredential("resident"))
            assertNull(store.readCredentialStatus("resident"))
            assertFalse(store.saveCredentialStatusIfCurrent(credential, snapshot))
        } finally { driver.close() }
    }
}
