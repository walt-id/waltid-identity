package id.walt.wallet2.persistence.stores

import id.walt.credentials.CredentialParser
import id.walt.credentials.examples.MdocsExamples
import id.walt.wallet2.data.HolderKeyBinding
import id.walt.wallet2.data.HolderKeyBindingOrigin
import id.walt.wallet2.data.PublicKeyThumbprint
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.handlers.WalletIssuanceSessionRecord
import id.walt.wallet2.handlers.WalletIssuanceSessionRecordKind
import id.walt.wallet2.persistence.db.WalletPersistenceDatabase
import id.walt.wallet2.persistence.encryption.DatabaseEncryptionKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class SqlDelightCredentialStoreBindingTest {
    @Test
    fun `fresh iOS schema retains holder bindings and coordinates continuation claims`() = runTest {
        val databaseName = "holder_binding_${Uuid.random()}"
        val driverFactory = DriverFactory()
        fun newDriver() = driverFactory.createEncryptedDriver(
            databaseName = databaseName,
            encryptionKey = DatabaseEncryptionKey(
                keyId = "$databaseName-key",
                material = ByteArray(32) { it.toByte() },
            ),
            isDeviceLocal = true,
            walletId = databaseName,
        )
        val driver = newDriver()
        try {
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

            val sessions = SqlDelightIssuanceSessionStore(database.walletPersistenceQueries)
            val pending = WalletIssuanceSessionRecord(
                "target", "session", WalletIssuanceSessionRecordKind.DEFERRED_CREDENTIAL, "pending", 123,
            )
            val claimed = pending.copy(payload = "claimed", updatedAtEpochMilliseconds = 456)
            sessions.put(pending)
            val secondDriver = newDriver()
            try {
                val second = SqlDelightIssuanceSessionStore(WalletPersistenceDatabase(secondDriver).walletPersistenceQueries)
                assertEquals(pending, second.get(pending.id))
                val claims = listOf(sessions, second).map { sessionStore ->
                    async(Dispatchers.Default) { sessionStore.compareAndSet(pending, claimed) }
                }.awaitAll()
                assertEquals(1, claims.count { it })
                assertEquals(claimed, sessions.get(pending.id))
                assertEquals(claimed, second.get(pending.id))
                assertFalse(second.compareAndSet(pending, null))
                assertTrue(second.compareAndSet(claimed, null))
                assertNull(sessions.get(pending.id))
                assertEquals(binding, store.getCredential(credential.id)?.holderKeyBinding)
            } finally {
                secondDriver.close()
            }
        } finally {
            driver.close()
            driverFactory.deleteDatabase(databaseName)
        }
    }
}
