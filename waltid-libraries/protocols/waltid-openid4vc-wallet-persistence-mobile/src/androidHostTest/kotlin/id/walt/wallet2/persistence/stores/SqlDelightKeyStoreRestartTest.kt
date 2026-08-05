package id.walt.wallet2.persistence.stores

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeyCapabilities
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.ManagedKey
import id.walt.crypto2.keys.ProviderId
import id.walt.crypto2.keys.StoredKey
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.crypto2.serialization.BinaryData
import id.walt.crypto2.serialization.StoredKeyCodec
import id.walt.crypto2.signum.SignumKeyInvalidatedException
import id.walt.crypto2.signum.SignumUserCancelledException
import id.walt.wallet2.persistence.db.WalletPersistenceDatabase
import id.walt.wallet2.persistence.keys.KeyUseAuthorizationPolicy
import id.walt.wallet2.persistence.keys.KeyUseAuthorizationException
import id.walt.wallet2.persistence.keys.KeyUseAuthorizationFailure
import id.walt.wallet2.persistence.keys.PlatformKeyPreflight
import id.walt.wallet2.persistence.keys.PlatformKeyRequest
import id.walt.wallet2.persistence.keys.PlatformManagedKeyInfo
import id.walt.wallet2.persistence.keys.PlatformManagedKeyProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqlDelightKeyStoreRestartTest {
    @Test
    fun `managed key descriptor restores after store recreation and is deleted atomically`() = runTest {
        database().use { database ->
            val provider = FakePlatformManagedKeyProvider()
            val store = SqlDelightKeyStore(provider, database.queries)
            val key = store.generateManagedKey(KeyId("managed"), KeySpec.Ec(EcCurve.P256), KEY_USAGES)

            val persisted = database.queries.selectByKeyId(key.id.value).executeAsOne()
            assertIs<StoredKey.Managed>(StoredKeyCodec.decodeFromString(persisted.stored_key))

            val restored = assertNotNull(
                SqlDelightKeyStore(provider, database.queries).getCrypto2Key(key.id.value, setOf(KeyUsage.SIGN))
            )
            val signature = assertNotNull(restored.capabilities.signer).sign(
                "payload".encodeToByteArray(), SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256)
            )
            assertContentEquals("signed:payload".encodeToByteArray(), signature)

            assertTrue(store.removeKey(key.id.value))
            assertEquals(1, provider.deleteCount)
            assertNull(database.queries.selectByKeyId(key.id.value).executeAsOneOrNull())
        }
    }

    @Test
    fun `failed SQL persistence removes generated managed key`() = runTest {
        database().use { database ->
            val provider = FakePlatformManagedKeyProvider()
            val store = SqlDelightKeyStore(provider, database.queries)
            val id = KeyId("failed-insert")
            database.failKeyInserts()

            assertFails {
                store.generateManagedKey(id, KeySpec.Ec(EcCurve.P256), KEY_USAGES)
            }

            assertEquals(1, provider.deleteCount)
            assertTrue(id !in provider.managedIds)
        }
    }

    @Test
    fun `software key descriptor restores after store recreation and deletes only the row`() = runTest {
        database().use { database ->
            val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
            val softwareKey = runtime.generateSoftwareKey(
                GenerateSoftwareKeyRequest(
                    id = KeyId("software"),
                    spec = KeySpec.Rsa(2048),
                    usages = KEY_USAGES,
                )
            )
            val provider = FakePlatformManagedKeyProvider()
            val store = SqlDelightKeyStore(provider, database.queries)

            assertEquals("software", store.addCrypto2Key(softwareKey))
            val persisted = database.queries.selectByKeyId("software").executeAsOne()
            assertIs<StoredKey.Software>(StoredKeyCodec.decodeFromString(persisted.stored_key))
            assertFails { store.addCrypto2Key(softwareKey) }
            assertEquals(persisted, database.queries.selectByKeyId("software").executeAsOne())

            val restored = assertNotNull(
                SqlDelightKeyStore(provider, database.queries)
                    .getCrypto2Key("software", setOf(KeyUsage.SIGN))
            )
            val message = "software-key-restart".encodeToByteArray()
            val algorithm = SignatureAlgorithm.RsaPkcs1(DigestAlgorithm.SHA_256)
            val signature = assertNotNull(restored.capabilities.signer).sign(message, algorithm)
            assertTrue(assertNotNull(restored.capabilities.verifier).verify(message, signature, algorithm))

            assertTrue(store.removeKey("software"))
            assertEquals(0, provider.deleteCount)
            assertNull(database.queries.selectByKeyId("software").executeAsOneOrNull())
        }
    }

    @Test
    fun `corrupt descriptor fails closed and cannot be deleted through a legacy fallback`() = runTest {
        database().use { database ->
            database.queries.insert("corrupt", 0, "{not-a-stored-key")
            val store = SqlDelightKeyStore(FakePlatformManagedKeyProvider(), database.queries)

            assertFails { store.getCrypto2Key("corrupt", setOf(KeyUsage.SIGN)) }
            assertFailsWith<IllegalArgumentException> { store.removeKey("corrupt") }
            assertNotNull(database.queries.selectByKeyId("corrupt").executeAsOneOrNull())
        }
    }

    @Test
    fun `missing ordinary managed key is reported as null`() = runTest {
        database().use { database ->
            val stored = storedDescriptor("ordinary-missing")
            database.queries.insert(
                key_id = stored.id.value,
                created_at = 0,
                stored_key = StoredKeyCodec.encodeToString(stored),
            )

            val store = SqlDelightKeyStore(
                FakePlatformManagedKeyProvider(authorizationPolicy = KeyUseAuthorizationPolicy.None),
                database.queries,
            )

            assertNull(store.getCrypto2Key(stored.id.value, KEY_USAGES))
        }
    }

    @Test
    fun `missing protected managed key is a stable unavailable failure`() = runTest {
        database().use { database ->
            val stored = storedDescriptor("protected-missing")
            database.queries.insert(
                key_id = stored.id.value,
                created_at = 0,
                stored_key = StoredKeyCodec.encodeToString(stored),
            )

            val store = SqlDelightKeyStore(
                FakePlatformManagedKeyProvider(authorizationPolicy = KeyUseAuthorizationPolicy.BiometricCurrentSet),
                database.queries,
            )

            val error = assertFailsWith<KeyUseAuthorizationException> {
                store.getCrypto2Key(stored.id.value, KEY_USAGES)
            }
            assertEquals(KeyUseAuthorizationFailure.ProtectedKeyUnavailable, error.failure)
        }
    }

    @Test
    fun `unexpected managed restore failure is propagated`() = runTest {
        database().use { database ->
            val stored = storedDescriptor("unexpected-failure")
            database.queries.insert(
                key_id = stored.id.value,
                created_at = 0,
                stored_key = StoredKeyCodec.encodeToString(stored),
            )
            val unexpected = IllegalStateException("keystore unavailable")
            val store = SqlDelightKeyStore(
                FakePlatformManagedKeyProvider(restoreFailure = unexpected),
                database.queries,
            )

            assertFailsWith<IllegalStateException> {
                store.getCrypto2Key(stored.id.value, KEY_USAGES)
            }
        }
    }

    @Test
    fun `invalidated protected managed key is a stable unavailable failure`() = runTest {
        database().use { database ->
            val stored = storedDescriptor("protected-invalidated")
            database.queries.insert(
                key_id = stored.id.value,
                created_at = 0,
                stored_key = StoredKeyCodec.encodeToString(stored),
            )
            val store = SqlDelightKeyStore(
                FakePlatformManagedKeyProvider(
                    authorizationPolicy = KeyUseAuthorizationPolicy.BiometricCurrentSet,
                    restoreFailure = SignumKeyInvalidatedException(stored.id.value),
                ),
                database.queries,
            )

            val error = assertFailsWith<KeyUseAuthorizationException> {
                store.getCrypto2Key(stored.id.value, KEY_USAGES)
            }
            assertEquals(KeyUseAuthorizationFailure.ProtectedKeyUnavailable, error.failure)
        }
    }

    @Test
    fun `protected authorization cancellation is a stable failure`() = runTest {
        database().use { database ->
            val provider = FakePlatformManagedKeyProvider(
                authorizationPolicy = KeyUseAuthorizationPolicy.BiometricCurrentSet,
                signFailure = SignumUserCancelledException(IllegalStateException("cancelled")),
            )
            val store = SqlDelightKeyStore(provider, database.queries)
            val key = store.generateKey(
                PlatformKeyRequest(
                    id = KeyId("protected-cancelled"),
                    spec = KeySpec.Ec(EcCurve.P256),
                    usages = KEY_USAGES,
                    authorizationPolicy = KeyUseAuthorizationPolicy.BiometricCurrentSet,
                )
            )

            val error = assertFailsWith<KeyUseAuthorizationException> {
                assertNotNull(key.capabilities.signer).sign(
                    "payload".encodeToByteArray(), SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256)
                )
            }
            assertEquals(KeyUseAuthorizationFailure.AuthorizationNotCompleted, error.failure)
        }
    }

    private fun database(): TestDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        WalletPersistenceDatabase.Schema.create(driver)
        return TestDatabase(driver, WalletPersistenceDatabase(driver))
    }

    private class TestDatabase(
        private val driver: JdbcSqliteDriver,
        database: WalletPersistenceDatabase,
    ) : AutoCloseable {
        val queries = database.walletPersistenceQueries

        fun failKeyInserts() {
            driver.execute(
                identifier = null,
                sql = """
                    CREATE TRIGGER fail_key_insert
                    BEFORE INSERT ON key_references
                    BEGIN
                        SELECT RAISE(FAIL, 'forced key insert failure');
                    END
                """.trimIndent(),
                parameters = 0,
            )
        }

        override fun close() = driver.close()
    }

    private class FakePlatformManagedKeyProvider : PlatformManagedKeyProvider {
        constructor(
            authorizationPolicy: KeyUseAuthorizationPolicy = KeyUseAuthorizationPolicy.None,
            restoreFailure: Throwable? = null,
            signFailure: Throwable? = null,
        ) {
            this.authorizationPolicy = authorizationPolicy
            this.restoreFailure = restoreFailure
            this.signFailure = signFailure
        }

        private val authorizationPolicy: KeyUseAuthorizationPolicy
        private val restoreFailure: Throwable?
        private val signFailure: Throwable?
        val managedIds = mutableSetOf<KeyId>()
        var deleteCount = 0

        override suspend fun preflight(request: PlatformKeyRequest): PlatformKeyPreflight =
            PlatformKeyPreflight(true)

        override suspend fun generateManagedKey(request: PlatformKeyRequest): ManagedKey {
            managedIds += request.id
            return managedKey(descriptor(request.id, request.spec, request.usages))
        }

        override suspend fun restoreManagedKey(stored: StoredKey.Managed): ManagedKey? {
            restoreFailure?.let { throw it }
            if (stored.id !in managedIds) return null
            return managedKey(stored)
        }

        override suspend fun deleteManagedKey(stored: StoredKey.Managed) {
            managedIds -= stored.id
            deleteCount++
        }

        override fun inspectManagedKey(stored: StoredKey.Managed): PlatformManagedKeyInfo =
            PlatformManagedKeyInfo(authorizationPolicy)

        private fun descriptor(id: KeyId, spec: KeySpec, usages: Set<KeyUsage>) = StoredKey.Managed(
            version = StoredKey.CURRENT_VERSION,
            id = id,
            spec = spec,
            usages = usages,
            provider = ProviderId("test-mobile-platform"),
            providerSchemaVersion = 1,
            providerData = BinaryData(id.value.encodeToByteArray()),
        )

        private fun managedKey(stored: StoredKey.Managed): ManagedKey = object : ManagedKey {
            override val storedKey = stored
            override val capabilities = KeyCapabilities(
                signer = { data, _ ->
                    signFailure?.let { throw it }
                    "signed:".encodeToByteArray() + data
                },
            )
        }
    }

    private companion object {
        val KEY_USAGES = setOf(KeyUsage.SIGN, KeyUsage.VERIFY)

        fun storedDescriptor(id: String) = StoredKey.Managed(
            version = StoredKey.CURRENT_VERSION,
            id = KeyId(id),
            spec = KeySpec.Ec(EcCurve.P256),
            usages = KEY_USAGES,
            provider = ProviderId("test-mobile-platform"),
            providerSchemaVersion = 1,
            providerData = BinaryData(id.encodeToByteArray()),
        )
    }
}
