package id.walt.wallet2.persistence.stores

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeyCapabilities
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.ManagedKey
import id.walt.crypto2.keys.ProviderId
import id.walt.crypto2.keys.SoftwareKey
import id.walt.crypto2.keys.StoredKey
import id.walt.crypto2.keys.Key as ManagedKeyMaterial
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

class PlatformKeyStoreRestartTest {
    @Test
    fun `managed key descriptor restores after store recreation and is deleted atomically`() = runTest {
        database().use { database ->
            val provider = FakePlatformManagedKeyProvider()
            val store = PlatformKeyStore(provider, database.queries)
            val key = store.generateManagedKey(KeyId("managed"), KeySpec.Ec(EcCurve.P256), KEY_USAGES)

            val persisted = database.queries.selectByKeyId(key.id.value).executeAsOne()
            assertIs<StoredKey.Managed>(StoredKeyCodec.decodeFromString(persisted.stored_key))

            val restored = assertNotNull(
                PlatformKeyStore(provider, database.queries).getCrypto2Key(key.id.value, setOf(KeyUsage.SIGN))
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
        val database = database()
        val provider = FakePlatformManagedKeyProvider()
        val store = PlatformKeyStore(provider, database.queries)
        val id = KeyId("failed-insert")
        database.close()

        assertFails {
            store.generateManagedKey(id, KeySpec.Ec(EcCurve.P256), KEY_USAGES)
        }

        assertEquals(1, provider.deleteCount)
        assertTrue(id !in provider.managedIds)
    }

    @Test
    fun `software keys are rejected rather than persisted with an Android-incompatible fallback`() = runTest {
        database().use { database ->
            val softwareKey = object : SoftwareKey {
                override val storedKey = StoredKey.Software(
                    version = StoredKey.CURRENT_VERSION,
                    id = KeyId("software"),
                    spec = KeySpec.Ec(EcCurve.P256),
                    usages = KEY_USAGES,
                    material = EncodedKey.Jwk(BinaryData("{}".encodeToByteArray()), privateMaterial = true),
                )
            }
            val store = PlatformKeyStore(FakePlatformManagedKeyProvider(), database.queries)

            assertFailsWith<IllegalArgumentException> { store.addCrypto2Key(softwareKey) }
            assertNull(database.queries.selectByKeyId("software").executeAsOneOrNull())
        }
    }

    @Test
    fun `corrupt descriptor fails closed and cannot be deleted through a legacy fallback`() = runTest {
        database().use { database ->
            database.queries.insert("corrupt", 0, "{not-a-stored-key")
            val store = PlatformKeyStore(FakePlatformManagedKeyProvider(), database.queries)

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

        override suspend fun restoreManagedKey(stored: StoredKey.Managed): ManagedKeyMaterial {
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
