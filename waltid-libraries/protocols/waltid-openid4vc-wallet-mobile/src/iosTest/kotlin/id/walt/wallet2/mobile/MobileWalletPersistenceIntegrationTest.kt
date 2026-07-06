package id.walt.wallet2.mobile

import id.walt.wallet2.persistence.encryption.DatabaseEncryptionKey
import id.walt.wallet2.persistence.encryption.DatabaseEncryptionKeyProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class MobileWalletPersistenceIntegrationTest {

    @Test
    fun integratorManagedIosDatabaseKeyOpensEncryptedStoreAndIsDeleted() = runTest {
        val walletId = "ios-integrator-key-${Uuid.random()}"
        val databaseName = "wallet_$walletId"
        val provider = RecordingDatabaseKeyProvider(
            DatabaseEncryptionKey(
                keyId = "integrator-key",
                material = ByteArray(32) { index -> (index + 5).toByte() },
            )
        )
        val factory = MobileWalletFactory()
        val config = MobileWalletConfig(
            walletId = walletId,
            persistence = MobileWalletPersistenceConfig.IntegratorManagedKey(provider),
        )

        val wallet = factory.create(config)
        assertEquals(emptyList(), wallet.credentials())

        val reopenedWallet = factory.create(config)
        assertEquals(emptyList(), reopenedWallet.credentials())
        assertEquals(listOf("$walletId:$databaseName", "$walletId:$databaseName"), provider.requestedKeys)
        reopenedWallet.deleteWallet()
        assertEquals(listOf("$walletId:$databaseName"), provider.deletedKeys)
    }

    private class RecordingDatabaseKeyProvider(
        private val key: DatabaseEncryptionKey,
    ) : DatabaseEncryptionKeyProvider {
        val requestedKeys = mutableListOf<String>()
        val deletedKeys = mutableListOf<String>()

        override suspend fun getOrCreateKey(walletId: String, databaseName: String): DatabaseEncryptionKey {
            requestedKeys += "$walletId:$databaseName"
            return key
        }

        override suspend fun deleteKey(walletId: String, databaseName: String) {
            deletedKeys += "$walletId:$databaseName"
        }
    }
}
