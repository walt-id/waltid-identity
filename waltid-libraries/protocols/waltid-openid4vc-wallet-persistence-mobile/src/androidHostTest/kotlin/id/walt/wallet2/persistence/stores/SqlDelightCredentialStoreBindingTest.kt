package id.walt.wallet2.persistence.stores

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import id.walt.credentials.CredentialParser
import id.walt.credentials.examples.MdocsExamples
import id.walt.wallet2.data.HolderKeyBinding
import id.walt.wallet2.data.HolderKeyBindingOrigin
import id.walt.wallet2.data.PublicKeyThumbprint
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.persistence.db.WalletPersistenceDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
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
}
