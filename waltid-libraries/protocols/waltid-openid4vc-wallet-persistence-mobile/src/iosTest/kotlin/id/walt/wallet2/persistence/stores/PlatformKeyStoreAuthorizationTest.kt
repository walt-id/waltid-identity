package id.walt.wallet2.persistence.stores

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyHardwareBacking
import id.walt.crypto.keys.KeyMeta
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.KeyUseAuthorizationAware
import id.walt.crypto.keys.KeyUseAuthorizationException
import id.walt.crypto.keys.KeyUseAuthorizationFailure
import id.walt.crypto.keys.KeyUseAuthorizationPolicy
import id.walt.wallet2.data.WalletKeyInfo
import id.walt.wallet2.data.WalletKeyStore
import id.walt.wallet2.persistence.db.WalletPersistenceDatabase
import id.walt.wallet2.persistence.db.WalletPersistenceQueries
import id.walt.wallet2.persistence.encryption.DatabaseEncryptionKey
import id.walt.wallet2.persistence.keys.PlatformKeyProvider
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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

    @Test
    fun protectedLoadRejectsKeyWithoutAuthorizationContract() = runTest {
        withQueries { queries ->
            val key = TestProtectedKey("protected-key")
            PlatformKeyStore(RecordingProvider(key), queries).addKey(key, protectedKeyInfo())

            val failure = assertFailsWith<KeyUseAuthorizationException> {
                PlatformKeyStore(RecordingProvider(TestUnprotectedKey("protected-key")), queries)
                    .getKey("protected-key")
            }

            assertEquals(KeyUseAuthorizationFailure.ProtectedKeyInvalidated, failure.failure)
        }
    }

    @Test
    fun protectedLoadReportsMissingPlatformKey() = runTest {
        withQueries { queries ->
            val key = TestProtectedKey("protected-key")
            PlatformKeyStore(RecordingProvider(key), queries).addKey(key, protectedKeyInfo())

            val failure = assertFailsWith<KeyUseAuthorizationException> {
                PlatformKeyStore(RecordingProvider(null), queries).getKey("protected-key")
            }

            assertEquals(KeyUseAuthorizationFailure.ProtectedKeyMissing, failure.failure)
        }
    }

    @Test
    fun protectedLoadRejectsSoftwareBackedPersistenceBeforeReadingMaterial() = runTest {
        withQueries { queries ->
            queries.insert(
                key_id = "protected-key",
                key_type = KeyType.secp256r1.name,
                created_at = 0L,
                is_platform_backed = 0L,
                key_material = "must-not-be-read",
                requested_authorization_policy = KeyUseAuthorizationPolicy.BiometricCurrentSet.name,
                effective_authorization_policy = KeyUseAuthorizationPolicy.BiometricCurrentSet.name,
                effective_hardware_backing = KeyHardwareBacking.Software.name,
            )

            val failure = assertFailsWith<KeyUseAuthorizationException> {
                PlatformKeyStore(RecordingProvider(null), queries).getKey("protected-key")
            }

            assertEquals(KeyUseAuthorizationFailure.ProtectedKeyInvalidated, failure.failure)
        }
    }

    @Test
    fun legacyStoreDefaultRejectsProtectedMetadataInsteadOfDiscardingIt() = runTest {
        var legacyAddCalls = 0
        val legacyStore = object : WalletKeyStore {
            override suspend fun getKey(keyId: String): Key? = null
            override suspend fun listKeys() = emptyFlow<WalletKeyInfo>()

            override suspend fun addKey(key: Key): String {
                legacyAddCalls++
                return key.getKeyId()
            }

            override suspend fun removeKey(keyId: String): Boolean = false
        }

        val failure = assertFailsWith<KeyUseAuthorizationException> {
            legacyStore.addKey(TestUnprotectedKey("protected-key"), protectedKeyInfo())
        }

        assertEquals(KeyUseAuthorizationFailure.UnsupportedCombination, failure.failure)
        assertEquals(0, legacyAddCalls)
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

    private fun protectedKeyInfo(): WalletKeyInfo = WalletKeyInfo(
        keyId = "protected-key",
        keyType = KeyType.secp256r1.name,
        requestedKeyUseAuthorizationPolicy = KeyUseAuthorizationPolicy.BiometricCurrentSet,
        effectiveKeyUseAuthorizationPolicy = KeyUseAuthorizationPolicy.BiometricCurrentSet,
        isPlatformBacked = true,
        effectiveHardwareBacking = KeyHardwareBacking.SecureEnclave,
    )

    private class RecordingProvider(private val key: Key?) : PlatformKeyProvider {
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

    private class TestUnprotectedKey(
        private val keyId: String,
    ) : Key() {
        override val keyType: KeyType = KeyType.secp256r1
        override val hasPrivateKey: Boolean = true

        override suspend fun getKeyId(): String = keyId
        override suspend fun getThumbprint(): String = error("not used")
        override suspend fun exportJWK(): String = error("not used")
        override suspend fun exportJWKObject(): JsonObject = error("not used")
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
