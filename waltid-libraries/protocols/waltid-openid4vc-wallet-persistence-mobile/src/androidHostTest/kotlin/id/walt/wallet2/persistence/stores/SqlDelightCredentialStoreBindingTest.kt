package id.walt.wallet2.persistence.stores

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import id.walt.credentials.CredentialParser
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
import kotlin.test.assertNotNull
import kotlin.time.Instant

class SqlDelightCredentialStoreBindingTest {

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
