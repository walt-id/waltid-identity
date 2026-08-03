package id.walt.wallet2.persistence.stores

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyMeta
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.KeyUseAuthorizationException
import id.walt.crypto.keys.KeyUseAuthorizationFailure
import id.walt.crypto.keys.KeyUseAuthorizationPolicy
import id.walt.wallet2.persistence.db.WalletPersistenceDatabase
import id.walt.wallet2.persistence.db.WalletPersistenceQueries
import id.walt.wallet2.persistence.encryption.DatabaseEncryptionKey
import id.walt.wallet2.persistence.keys.GeneratedPlatformKey
import id.walt.wallet2.persistence.keys.PlatformKeyPreflight
import id.walt.wallet2.persistence.keys.PlatformKeyProvider
import id.walt.wallet2.persistence.keys.PlatformKeyRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.uuid.Uuid

class PlatformKeyStoreAuthorizationTest {

    @Test
    fun protectedPolicySurvivesDatabaseRestart() = runTest {
        val databaseName = "platform_key_metadata_${Uuid.random()}"
        val databaseKey = DatabaseEncryptionKey(databaseName, ByteArray(32) { (it + 2).toByte() })
        val protectedKey = TestKey("protected-key")
        val provider = RecordingProvider(protectedKey)
        val factory = DriverFactory()

        try {
            val firstDriver = factory.createEncryptedDriver(databaseName, databaseKey, true, databaseName)
            PlatformKeyStore(provider, WalletPersistenceDatabase(firstDriver).walletPersistenceQueries).addKey(
                protectedKey,
                MobileWalletKeyRecord(
                    keyId = "protected-key",
                    keyType = KeyType.secp256r1,
                    keyUseAuthorizationPolicy = KeyUseAuthorizationPolicy.BiometricCurrentSet,
                    isPlatformBacked = true,
                ),
            )
            firstDriver.close()

            val secondDriver = factory.createEncryptedDriver(databaseName, databaseKey, true, databaseName)
            try {
                val store = PlatformKeyStore(provider, WalletPersistenceDatabase(secondDriver).walletPersistenceQueries)
                val record = store.listKeyRecords().first()
                val loaded = store.getKey("protected-key")

                assertEquals(KeyUseAuthorizationPolicy.BiometricCurrentSet, record.keyUseAuthorizationPolicy)
                assertEquals(KeyType.secp256r1, record.keyType)
                assertSame(protectedKey, loaded)
                assertEquals(KeyUseAuthorizationPolicy.BiometricCurrentSet, provider.loadedPolicy)
            } finally {
                secondDriver.close()
            }
        } finally {
            factory.deleteDatabase(databaseName)
        }
    }

    @Test
    fun protectedLoadReportsUnavailablePlatformKey() = runTest {
        withQueries { queries ->
            val key = TestKey("protected-key")
            PlatformKeyStore(RecordingProvider(key), queries).addKey(
                key,
                MobileWalletKeyRecord(
                    keyId = "protected-key",
                    keyType = KeyType.secp256r1,
                    keyUseAuthorizationPolicy = KeyUseAuthorizationPolicy.BiometricCurrentSet,
                    isPlatformBacked = true,
                ),
            )

            val failure = assertFailsWith<KeyUseAuthorizationException> {
                PlatformKeyStore(RecordingProvider(null), queries).getKey("protected-key")
            }

            assertEquals(KeyUseAuthorizationFailure.ProtectedKeyUnavailable, failure.failure)
        }
    }

    @Test
    fun corruptProtectedMetadataFailsClosedBeforeProviderLoad() = runTest {
        withQueries { queries ->
            queries.insert(
                key_id = "protected-key",
                key_type = KeyType.secp256r1.name,
                created_at = 0L,
                is_platform_backed = 0L,
                key_material = "must-not-be-read",
                authorization_policy = KeyUseAuthorizationPolicy.BiometricCurrentSet.name,
            )

            val provider = RecordingProvider(null)
            val failure = assertFailsWith<KeyUseAuthorizationException> {
                PlatformKeyStore(provider, queries).getKey("protected-key")
            }

            assertEquals(KeyUseAuthorizationFailure.InvalidStoredKeyMetadata, failure.failure)
            assertEquals(null, provider.loadedPolicy)
        }
    }

    private suspend fun withQueries(block: suspend (WalletPersistenceQueries) -> Unit) {
        val databaseName = "platform_key_validation_${Uuid.random()}"
        val databaseKey = DatabaseEncryptionKey(databaseName, ByteArray(32) { (it + 3).toByte() })
        val factory = DriverFactory()
        val driver = factory.createEncryptedDriver(databaseName, databaseKey, true, databaseName)
        try {
            block(WalletPersistenceDatabase(driver).walletPersistenceQueries)
        } finally {
            driver.close()
            factory.deleteDatabase(databaseName)
        }
    }

    private class RecordingProvider(private val key: Key?) : PlatformKeyProvider {
        var loadedPolicy: KeyUseAuthorizationPolicy? = null

        override suspend fun preflight(request: PlatformKeyRequest) = PlatformKeyPreflight(true)

        override suspend fun generate(request: PlatformKeyRequest) = GeneratedPlatformKey(
            key = requireNotNull(key),
            record = MobileWalletKeyRecord(
                keyId = requireNotNull(key).getKeyId(),
                keyType = requireNotNull(key).keyType,
                keyUseAuthorizationPolicy = request.keyUseAuthorizationPolicy,
                isPlatformBacked = request.keyUseAuthorizationPolicy != KeyUseAuthorizationPolicy.None,
            ),
        )

        override suspend fun load(record: MobileWalletKeyRecord): Key? {
            loadedPolicy = record.keyUseAuthorizationPolicy
            return key
        }

        override suspend fun delete(record: MobileWalletKeyRecord): Boolean = true

        override suspend fun loadSoftwareKey(keyId: String, keyType: KeyType, jwkMaterial: ByteArray): Key? = null

        override suspend fun exportSoftwareKeyMaterial(key: Key): ByteArray = error("not used")
    }

    private class TestKey(private val keyId: String) : Key() {
        override val keyType: KeyType = KeyType.secp256r1
        override val hasPrivateKey: Boolean = true

        override suspend fun getKeyId(): String = keyId
        override suspend fun getThumbprint(): String = error("not used")
        override suspend fun exportJWK(): String = error("not used")
        override suspend fun exportJWKObject(): JsonObject = error("not used")
        override suspend fun exportPEM(): String = error("not used")
        override suspend fun signRaw(plaintext: ByteArray, customSignatureAlgorithm: String?): Any = error("not used")
        override suspend fun signJws(plaintext: ByteArray, headers: Map<String, JsonElement>): String = error("not used")
        override suspend fun verifyRaw(
            signed: ByteArray,
            detachedPlaintext: ByteArray?,
            customSignatureAlgorithm: String?,
        ): Result<ByteArray> = error("not used")
        override suspend fun verifyJws(signedJws: String): Result<JsonElement> = error("not used")
        override suspend fun getPublicKey(): Key = error("not used")
        override suspend fun getPublicKeyRepresentation(): ByteArray = error("not used")
        override suspend fun getMeta(): KeyMeta = error("not used")
        override suspend fun deleteKey(): Boolean = true
    }
}
