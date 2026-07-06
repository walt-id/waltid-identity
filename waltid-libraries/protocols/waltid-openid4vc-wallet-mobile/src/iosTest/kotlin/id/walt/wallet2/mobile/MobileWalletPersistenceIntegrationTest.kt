package id.walt.wallet2.mobile

import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.WalletCredentialStore
import id.walt.wallet2.persistence.encryption.DatabaseEncryptionKey
import id.walt.wallet2.persistence.encryption.DatabaseEncryptionKeyProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

class MobileWalletPersistenceIntegrationTest {

    @Test
    fun providedIosDatabaseKeyOpensEncryptedStoreAndIsDeleted() = runTest {
        val walletId = "ios-provided-key-${Uuid.random()}"
        val databaseName = "wallet_$walletId"
        val provider = RecordingDatabaseKeyProvider(
            DatabaseEncryptionKey(
                keyId = "provided-key",
                material = ByteArray(32) { index -> (index + 5).toByte() },
            )
        )
        val factory = MobileWalletFactory()
        val config = MobileWalletConfig(
            walletId = walletId,
            persistence = MobileWalletPersistence(
                databaseKey = MobileWalletDatabaseKey.Provided(provider),
            ),
        )

        val wallet = factory.create(config)
        assertEquals(emptyList(), wallet.credentials())

        val reopenedWallet = factory.create(config)
        assertEquals(emptyList(), reopenedWallet.credentials())
        assertEquals(listOf("$walletId:$databaseName", "$walletId:$databaseName"), provider.requestedKeys)
        reopenedWallet.deleteWallet()
        assertEquals(listOf("$walletId:$databaseName"), provider.deletedKeys)
    }

    @Test
    fun customIosCredentialStoreRoutesWithoutReplacingDidOrKeyStores() = runTest {
        val walletId = "ios-custom-credential-store-${Uuid.random()}"
        val databaseName = "wallet_$walletId"
        val credentialStore = RecordingCredentialStore()
        val databaseKeyProvider = RecordingDatabaseKeyProvider(
            DatabaseEncryptionKey(
                keyId = "provided-key",
                material = ByteArray(32) { index -> (index + 7).toByte() },
            )
        )
        val factory = MobileWalletFactory()
        val config = MobileWalletConfig(
            walletId = walletId,
            persistence = MobileWalletPersistence(
                databaseKey = MobileWalletDatabaseKey.Provided(databaseKeyProvider),
                stores = MobileWalletStores(credentials = credentialStore),
            ),
        )

        // Kotlin/Native iOS test hosts can report Keychain as unavailable. App-hosted
        // Swift integration tests cover managed Keychain persistence with this store shape.
        val wallet = factory.create(config)
        assertEquals(emptyList(), wallet.credentials())
        assertEquals(listOf("$walletId:$databaseName"), databaseKeyProvider.requestedKeys)
        assertEquals(1, credentialStore.listCredentialsCalls)

        wallet.deleteWallet()
        assertEquals(listOf("$walletId:$databaseName"), databaseKeyProvider.deletedKeys)
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

    private class RecordingCredentialStore : WalletCredentialStore {
        var listCredentialsCalls = 0

        override suspend fun getCredential(id: String): StoredCredential? = null

        override suspend fun listCredentials(): Flow<StoredCredential> {
            listCredentialsCalls++
            return emptyList<StoredCredential>().asFlow()
        }

        override suspend fun addCredential(entry: StoredCredential) =
            error("This test only verifies credential-store routing")

        override suspend fun removeCredential(id: String): Boolean = false
    }
}
