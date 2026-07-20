package id.walt.wallet2.persistence.stores

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyHardwareBacking
import id.walt.crypto.keys.KeyMeta
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.KeyUseAuthorizationAware
import id.walt.crypto.keys.KeyUseAuthorizationPolicy
import id.walt.wallet2.data.WalletKeyInfo
import id.walt.wallet2.persistence.db.WalletPersistenceDatabase
import id.walt.wallet2.persistence.encryption.DatabaseEncryptionKey
import id.walt.wallet2.persistence.keys.PlatformKeyProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.fail
import kotlin.uuid.Uuid

class PlatformKeyStoreAuthorizationTest {

    @Test
    fun protectedPolicyAndEffectiveBackingSurviveDatabaseRestart() = runTest {
        val databaseName = "platform_key_metadata_${Uuid.random()}"
        val databaseKey = DatabaseEncryptionKey(databaseName, ByteArray(32) { (it + 2).toByte() })
        val protectedKey = TestProtectedKey("protected-key")
        val provider = RecordingProvider(protectedKey)
        val factory = DriverFactory()

        try {
            val firstDriver = factory.createEncryptedDriver(databaseName, databaseKey, true, databaseName)
            try {
                val driver = firstDriver
                val store = PlatformKeyStore(provider, WalletPersistenceDatabase(driver).walletPersistenceQueries)
                store.addKey(
                    protectedKey,
                    WalletKeyInfo(
                        keyId = "protected-key",
                        keyType = KeyType.secp256r1.name,
                        requestedKeyUseAuthorizationPolicy = KeyUseAuthorizationPolicy.BiometricCurrentSet,
                        effectiveKeyUseAuthorizationPolicy = KeyUseAuthorizationPolicy.BiometricCurrentSet,
                        isPlatformBacked = true,
                        effectiveHardwareBacking = KeyHardwareBacking.SecureEnclave,
                    )
                )
            } finally {
                firstDriver.close()
            }

            val secondDriver = factory.createEncryptedDriver(databaseName, databaseKey, true, databaseName)
            try {
                val driver = secondDriver
                val store = PlatformKeyStore(provider, WalletPersistenceDatabase(driver).walletPersistenceQueries)
                val info = store.listKeys().first()
                val loaded = store.getKey("protected-key")

                assertEquals(KeyUseAuthorizationPolicy.BiometricCurrentSet, info.requestedKeyUseAuthorizationPolicy)
                assertEquals(KeyUseAuthorizationPolicy.BiometricCurrentSet, info.effectiveKeyUseAuthorizationPolicy)
                assertEquals(KeyHardwareBacking.SecureEnclave, info.effectiveHardwareBacking)
                assertEquals(KeyUseAuthorizationPolicy.BiometricCurrentSet, provider.loadedPolicy)
                assertSame(protectedKey, loaded)
            } finally {
                secondDriver.close()
            }
        } finally {
            factory.deleteDatabase(databaseName)
        }
    }

    private class RecordingProvider(private val key: Key) : PlatformKeyProvider {
        var loadedPolicy: KeyUseAuthorizationPolicy? = null

        override val supportedPlatformKeyTypes: Set<KeyType> = setOf(KeyType.secp256r1)

        override suspend fun generateKey(keyType: KeyType, keyId: String?): Key = fail("not used")

        override suspend fun loadKey(keyId: String, keyType: KeyType): Key? = fail("policy-aware load required")

        override suspend fun loadKey(
            keyId: String,
            keyType: KeyType,
            keyUseAuthorizationPolicy: KeyUseAuthorizationPolicy,
        ): Key? {
            loadedPolicy = keyUseAuthorizationPolicy
            return key
        }

        override suspend fun loadSoftwareKey(keyId: String, keyType: KeyType, jwkMaterial: ByteArray): Key? =
            fail("protected key material must not be loaded from the database")

        override suspend fun exportSoftwareKeyMaterial(key: Key): ByteArray =
            fail("protected key material must not be exported")

        override suspend fun deleteKey(keyId: String, keyType: KeyType): Boolean = true
    }

    private class TestProtectedKey(
        private val keyId: String,
    ) : Key(), KeyUseAuthorizationAware {
        override val keyType: KeyType = KeyType.secp256r1
        override val hasPrivateKey: Boolean = true
        override val keyUseAuthorizationPolicy: KeyUseAuthorizationPolicy =
            KeyUseAuthorizationPolicy.BiometricCurrentSet
        override val isPlatformBacked: Boolean = true

        override suspend fun effectiveHardwareBacking(): KeyHardwareBacking = KeyHardwareBacking.SecureEnclave
        override suspend fun getKeyId(): String = keyId
        override suspend fun getThumbprint(): String = error("not used")
        override suspend fun exportJWK(): String = error("protected private key material is not exportable")
        override suspend fun exportJWKObject(): JsonObject = error("protected private key material is not exportable")
        override suspend fun exportPEM(): String = error("not used")
        override suspend fun signRaw(plaintext: ByteArray, customSignatureAlgorithm: String?): Any =
            error("not used")
        override suspend fun signJws(plaintext: ByteArray, headers: Map<String, JsonElement>): String =
            error("not used")
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
