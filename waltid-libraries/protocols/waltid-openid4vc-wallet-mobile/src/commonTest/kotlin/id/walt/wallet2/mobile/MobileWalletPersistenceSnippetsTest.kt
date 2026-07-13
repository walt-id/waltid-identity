package id.walt.wallet2.mobile

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.WalletCredentialStore
import id.walt.wallet2.data.WalletDidEntry
import id.walt.wallet2.data.WalletDidStore
import id.walt.wallet2.data.WalletKeyInfo
import id.walt.wallet2.data.WalletKeyStore
import id.walt.wallet2.persistence.encryption.DatabaseEncryptionKey
import id.walt.wallet2.persistence.encryption.DatabaseEncryptionKeyProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class MobileWalletPersistenceSnippetsTest {

    @Test
    fun defaultPersistenceSnippetCompiles() {
        // doc-snippet:start kotlin-default-persistence
        val config = MobileWalletConfig(walletId = "consumer-wallet")
        // doc-snippet:end kotlin-default-persistence

        assertEquals("consumer-wallet", config.walletId)
        assertIs<MobileWalletDatabaseKey.Managed>(config.persistence.databaseKey)
    }

    @Test
    fun providedDatabaseKeySnippetCompiles() {
        // doc-snippet:start kotlin-provided-database-key
        class KmsDatabaseKeyProvider : DatabaseEncryptionKeyProvider {
            override suspend fun getOrCreateKey(walletId: String, databaseName: String): DatabaseEncryptionKey {
                val keyBytes = loadOrCreateKeyBytes(walletId, databaseName)
                return DatabaseEncryptionKey(keyId = "$walletId:$databaseName", material = keyBytes)
            }

            override suspend fun deleteKey(walletId: String, databaseName: String) {
                deleteKeyBytes(walletId, databaseName)
            }
        }

        val config = MobileWalletConfig(
            walletId = "consumer-wallet",
            persistence = MobileWalletPersistence(
                databaseKey = MobileWalletDatabaseKey.Provided(
                    provider = KmsDatabaseKeyProvider()
                )
            )
        )
        // doc-snippet:end kotlin-provided-database-key

        assertIs<MobileWalletDatabaseKey.Provided>(config.persistence.databaseKey)
    }

    @Test
    fun customCredentialStoreSnippetCompiles() {
        // doc-snippet:start kotlin-custom-credential-store
        val config = MobileWalletConfig(
            walletId = "consumer-wallet",
            persistence = MobileWalletPersistence(
                stores = MobileWalletStores(
                    credentials = appCredentialStore
                )
            )
        )
        // doc-snippet:end kotlin-custom-credential-store

        assertSame(appCredentialStore, config.persistence.stores.credentials)
    }

    @Test
    fun fullStoreOverridesSnippetCompiles() {
        // doc-snippet:start kotlin-full-store-overrides
        val config = MobileWalletConfig(
            walletId = "consumer-wallet",
            persistence = MobileWalletPersistence(
                stores = MobileWalletStores(
                    credentials = appCredentialStore,
                    dids = appDidStore,
                    keys = MobileWalletKeys(
                        store = appKeyStore,
                        generate = { keyType -> appKeyProvider.generateKey(keyType) }
                    )
                )
            )
        )
        // doc-snippet:end kotlin-full-store-overrides

        assertSame(appCredentialStore, config.persistence.stores.credentials)
        assertSame(appDidStore, config.persistence.stores.dids)
        assertNotNull(config.persistence.stores.keys)
    }
}

private suspend fun loadOrCreateKeyBytes(walletId: String, databaseName: String): ByteArray {
    require(walletId.isNotBlank())
    require(databaseName.isNotBlank())
    return ByteArray(32)
}

private suspend fun deleteKeyBytes(walletId: String, databaseName: String) {
    require(walletId.isNotBlank())
    require(databaseName.isNotBlank())
}

private val appCredentialStore = object : WalletCredentialStore {
    override suspend fun getCredential(id: String): StoredCredential? = null

    override suspend fun listCredentials(): Flow<StoredCredential> = emptyFlow()

    override suspend fun addCredential(entry: StoredCredential) = Unit

    override suspend fun removeCredential(id: String): Boolean = false
}

private val appDidStore = object : WalletDidStore {
    override suspend fun getDid(did: String): WalletDidEntry? = null

    override suspend fun listDids(): Flow<WalletDidEntry> = emptyFlow()

    override suspend fun addDid(entry: WalletDidEntry) = Unit

    override suspend fun removeDid(did: String): Boolean = false
}

private val appKeyStore = object : WalletKeyStore {
    override suspend fun getKey(keyId: String): Key? = null

    override suspend fun listKeys(): Flow<WalletKeyInfo> = emptyFlow()

    override suspend fun addKey(key: Key): String = key.getKeyId()

    override suspend fun removeKey(keyId: String): Boolean = false
}

private object appKeyProvider {
    suspend fun generateKey(keyType: KeyType): Key {
        error("Replace with app-owned key generation for $keyType")
    }
}
