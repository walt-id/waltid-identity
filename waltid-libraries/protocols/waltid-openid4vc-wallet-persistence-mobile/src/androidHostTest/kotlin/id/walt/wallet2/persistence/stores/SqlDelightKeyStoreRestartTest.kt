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
import id.walt.crypto2.signum.SignumKeyPolicy
import id.walt.wallet2.persistence.db.WalletPersistenceDatabase
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
        val managedIds = mutableSetOf<KeyId>()
        var deleteCount = 0

        override suspend fun generateManagedKey(
            id: KeyId,
            spec: KeySpec,
            usages: Set<KeyUsage>,
            policy: SignumKeyPolicy?,
        ): ManagedKey {
            managedIds += id
            return managedKey(descriptor(id, spec, usages))
        }

        override suspend fun restoreManagedKey(stored: StoredKey.Managed): ManagedKey {
            require(stored.id in managedIds)
            return managedKey(stored)
        }

        override suspend fun deleteManagedKey(stored: StoredKey.Managed) {
            managedIds -= stored.id
            deleteCount++
        }

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
                signer = { data, _ -> "signed:".encodeToByteArray() + data },
            )
        }
    }

    private companion object {
        val KEY_USAGES = setOf(KeyUsage.SIGN, KeyUsage.VERIFY)
    }
}
