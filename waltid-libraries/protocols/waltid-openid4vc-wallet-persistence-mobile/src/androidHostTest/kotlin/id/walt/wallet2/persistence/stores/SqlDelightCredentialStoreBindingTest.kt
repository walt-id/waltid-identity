package id.walt.wallet2.persistence.stores

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import id.walt.credentials.CredentialParser
import id.walt.credentials.examples.SdJwtExamples
import id.walt.credentials.examples.MdocsExamples
import id.walt.wallet2.data.HolderKeyBinding
import id.walt.wallet2.data.HolderKeyBindingOrigin
import id.walt.wallet2.data.HolderKeyBindingErrorCode
import id.walt.wallet2.data.HolderKeyBindingException
import id.walt.wallet2.data.PublicKeyThumbprint
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.persistence.db.WalletPersistenceDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.time.Instant

class SqlDelightCredentialStoreBindingTest {

    @Test
    fun migratedInventoryBackfillsAndInvalidatesOnReplacementWithoutChangingSignedBytes() = runTest {
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            driver.execute(null, """
                CREATE TABLE credentials (id TEXT PRIMARY KEY NOT NULL, serialized_credential TEXT NOT NULL,
                    format TEXT NOT NULL DEFAULT '', label TEXT, added_at INTEGER NOT NULL, metadata TEXT, holder_key_binding TEXT)
            """.trimIndent(), 0)
            val queries = WalletPersistenceDatabase(driver).walletPersistenceQueries
            val original = MdocsExamples.mdocsExampleBase64Url
            queries.insertCredential("one", original, "mso_mdoc", "Original", 0, null, null)
            WalletPersistenceDatabase.Schema.migrate(driver, 1, WalletPersistenceDatabase.Schema.version)
            val store = SqlDelightCredentialStore(queries)
            assertNull(queries.selectCredentialSummaries().executeAsOne().type_metadata)
            val row = store.credentialSummaries().single()
            assertEquals("Original", row.label)
            assertFalse(row.typeMetadataJson.contains("portrait"))
            assertEquals(row.typeMetadataJson, queries.selectCredentialSummaries().executeAsOne().type_metadata)
            assertEquals(original, queries.selectCredentialById("one").executeAsOne().serialized_credential)

            val replacement = SdJwtExamples.sdJwtVcSignedExample2
            queries.insertCredential("one", replacement, "dc+sd-jwt", "Replacement", 1, null, null)
            queries.saveCredentialInventoryIfCurrent(row.issuer, row.subject, row.typeMetadataJson, "one", original)
            assertNull(queries.selectCredentialSummaries().executeAsOne().type_metadata)
            val current = store.credentialSummaries().single()
            assertEquals("Replacement", current.label)
            assertEquals(current, store.credentialSummaries().single())
            assertEquals(replacement, queries.selectCredentialById("one").executeAsOne().serialized_credential)
            store.removeCredential("one")
            assertEquals(emptyList(), store.credentialSummaries())
        }
    }

    @Test
    fun `fresh schema round trips holder key binding`() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            WalletPersistenceDatabase.Schema.create(driver)
            val database = WalletPersistenceDatabase(driver)
            val store = SqlDelightCredentialStore(database.walletPersistenceQueries)
            val binding = HolderKeyBinding(
                keyReference = "urn:waltid:wallet-key:v1:store:0:aG9sZGVy",
                publicKeyThumbprint = PublicKeyThumbprint(value = "thumbprint"),
                origin = HolderKeyBindingOrigin.ISSUANCE,
                createdAt = Instant.fromEpochMilliseconds(1_725_000_000_000),
            )
            val credential = StoredCredential(
                id = "mdoc-1",
                credential = CredentialParser.detectAndParse(MdocsExamples.mdocsExampleBase64Url).second,
                label = "mDL",
                holderKeyBinding = binding,
            )

            store.addCredential(credential)

            assertNotNull(
                database.walletPersistenceQueries.selectCredentialById(credential.id)
                    .executeAsOne().holder_key_binding
            )
            assertEquals(binding, store.getCredential(credential.id)?.holderKeyBinding)
        } finally {
            driver.close()
        }
    }

    @Test
    fun `invalid persisted binding reports a stable holder key error`() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            WalletPersistenceDatabase.Schema.create(driver)
            val database = WalletPersistenceDatabase(driver)
            val store = SqlDelightCredentialStore(database.walletPersistenceQueries)
            store.addCredential(
                StoredCredential(
                    id = "mdoc-corrupt-binding",
                    credential = CredentialParser.detectAndParse(MdocsExamples.mdocsExampleBase64Url).second,
                )
            )
            driver.execute(
                identifier = null,
                sql = "UPDATE credentials SET holder_key_binding = '{' WHERE id = 'mdoc-corrupt-binding'",
                parameters = 0,
                binders = null,
            )

            val failure = assertFailsWith<HolderKeyBindingException> {
                store.getCredential("mdoc-corrupt-binding")
            }

            assertEquals(HolderKeyBindingErrorCode.BINDING_INVALID, failure.code)
            assertEquals("mdoc-corrupt-binding", failure.credentialId)
        } finally {
            driver.close()
        }
    }
}
