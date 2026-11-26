package id.walt.wallet2.persistence.stores

import kotlinx.serialization.json.Json
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import id.walt.credentials.examples.MdocsExamples
import id.walt.credentials.status.CredentialStatus
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.persistence.db.WalletPersistenceDatabase
import id.walt.wallet2.status.CredentialStatusInput
import id.walt.wallet2.status.CredentialStatusStoredRecord
import id.walt.wallet2.status.PersistedCredentialStatus
import id.walt.wallet2.status.statusFingerprint
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
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
            val legacyStore = SqlDelightCredentialStore(WalletPersistenceDatabase(driver).walletPersistenceQueries)
            val credential = assertNotNull(legacyStore.getCredential("resident"))
            assertEquals(raw, credential.credential.signed)
            val at = Instant.fromEpochSeconds(1_800_000_000)
            val snapshot = PersistedCredentialStatus(credentialFingerprint = credential.statusFingerprint(), trustPolicyId = "test-v1",
                metadata = Json.decodeFromString(CredentialStatus.serializer(), """{"type":"references","entries":[{"type":"mso","referenceId":"ref","check":{"type":"observed","observation":{"value":"REVOKED","checkedAt":"$at","freshUntil":"$at"}}}]}"""))
            val original = CredentialStatusStoredRecord("first", snapshot)
            driver.execute(null, "INSERT INTO credential_status_snapshots (credential_id, snapshot) VALUES ('resident', ?)", 1) {
                bindString(0, Json.encodeToString(CredentialStatusStoredRecord.serializer(), original))
            }
            WalletPersistenceDatabase.Schema.migrate(driver, 2, 3)
            driver.execute(null, "INSERT INTO credential_status_maintenance (wallet_id, grant_id) VALUES ('existing', 'grant')", 0)
            WalletPersistenceDatabase.Schema.migrate(driver, 3, WalletPersistenceDatabase.Schema.version)
            val database = WalletPersistenceDatabase(driver)
            val store = SqlDelightCredentialStore(database.walletPersistenceQueries)
            val enrolment = assertNotNull(database.walletPersistenceQueries.selectCredentialStatusMaintenance("existing").executeAsOneOrNull())
            assertEquals("grant", enrolment.grant_id)
            assertNull(enrolment.cursor_id)
            assertEquals(original, store.readCredentialStatusRecord("resident"))
            assertNull(assertIs<CredentialStatusInput.Signed>(store.readStatusInput("resident")).notice)
            val cleared = CredentialStatusStoredRecord("cleared", null)
            assertFalse(store.compareAndSetCredentialStatus(credential, null, CredentialStatusStoredRecord("stale", snapshot)))
            assertTrue(store.compareAndSetCredentialStatus(credential, original, cleared))
            assertNull(store.readCredentialStatus(credential.id))
            assertFalse(store.compareAndSetCredentialStatus(credential, null, CredentialStatusStoredRecord("aba", snapshot)))
            assertFalse(store.compareAndSetCredentialStatus(credential, original, CredentialStatusStoredRecord("old", snapshot)))
            assertTrue(store.compareAndSetCredentialStatus(credential, cleared, CredentialStatusStoredRecord("restored", snapshot)))
            assertEquals(snapshot, SqlDelightCredentialStore(WalletPersistenceDatabase(driver).walletPersistenceQueries).readCredentialStatus("resident"))
            assertTrue(store.removeCredential("resident"))
            assertNull(store.readCredentialStatus("resident"))
            assertFalse(store.compareAndSetCredentialStatus(credential, null, CredentialStatusStoredRecord("first", snapshot)))
        } finally { driver.close() }
    }
}
