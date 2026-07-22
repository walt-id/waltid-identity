package id.walt.wallet2.persistence.stores

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EdwardsCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeyCapabilities
import id.walt.crypto2.keys.Key as Crypto2Key
import id.walt.crypto2.keys.ManagedKey
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.ProviderId
import id.walt.crypto2.keys.Signer
import id.walt.crypto2.keys.SoftwareKey
import id.walt.crypto2.keys.StoredKey
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import id.walt.crypto2.serialization.BinaryData
import id.walt.crypto2.serialization.StoredKeyCodec
import id.walt.crypto2.signum.SignumKeyPolicy
import id.walt.wallet2.persistence.db.WalletPersistenceDatabase
import id.walt.wallet2.persistence.keys.Crypto2PlatformKeyProvider
import id.walt.wallet2.persistence.keys.PlatformKeyProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PlatformKeyStoreRestartTest {
    @Test
    fun `version one schema migration preserves wallet data`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.use {
            driver.execute(
                null,
                "CREATE TABLE key_references (key_id TEXT NOT NULL PRIMARY KEY, key_type TEXT NOT NULL, " +
                    "created_at INTEGER NOT NULL, is_platform_backed INTEGER NOT NULL DEFAULT 1, key_material TEXT)",
                0,
            )
            driver.execute(
                null,
                "CREATE TABLE credentials (id TEXT NOT NULL PRIMARY KEY, serialized_credential TEXT NOT NULL, " +
                    "format TEXT NOT NULL DEFAULT '', label TEXT, added_at INTEGER NOT NULL)",
                0,
            )
            driver.execute(null, "CREATE TABLE dids (did TEXT NOT NULL PRIMARY KEY, document TEXT NOT NULL)", 0)
            driver.execute(null, "INSERT INTO key_references VALUES ('legacy-key', 'Ed25519', 1, 0, '{}')", 0)
            driver.execute(null, "INSERT INTO credentials VALUES ('credential', '{}', 'dc+sd-jwt', NULL, 1)", 0)
            driver.execute(null, "INSERT INTO dids VALUES ('did:key:legacy', '{}')", 0)

            WalletPersistenceDatabase.Schema.migrate(driver, 1, 2)
            val queries = WalletPersistenceDatabase(driver).walletPersistenceQueries

            assertEquals(null, queries.selectByKeyId("legacy-key").executeAsOne().crypto2_stored_key)
            assertEquals("credential", queries.selectAllCredentials().executeAsOne().id)
            assertEquals("did:key:legacy", queries.selectAllDids().executeAsOne().did)
        }
    }

    @Test
    fun `software key dual-write and legacy backfill survive store recreation`() = runTest {
        database().use { database ->
            val provider = FakePlatformKeyProvider()
            val key = JWKKey.generate(KeyType.Ed25519)
            val keyId = PlatformKeyStore(provider, database.queries).addKey(key)

            val persisted = assertNotNull(database.queries.selectByKeyId(keyId).executeAsOneOrNull())
            assertIs<StoredKey.Software>(StoredKeyCodec.decodeFromString(assertNotNull(persisted.crypto2_stored_key)))
            val recreated = PlatformKeyStore(provider, database.queries)
            assertNotNull(recreated.getKey(keyId))
            assertNotNull(recreated.getCrypto2Key(keyId, setOf(KeyUsage.SIGN)))

            val legacyKey = JWKKey.generate(KeyType.Ed25519)
            val legacyKeyId = legacyKey.getKeyId()
            database.queries.insert(
                key_id = legacyKeyId,
                key_type = legacyKey.keyType.name,
                created_at = 1L,
                is_platform_backed = 0L,
                key_material = legacyKey.exportJWK(),
                crypto2_stored_key = null,
            )

            assertNotNull(PlatformKeyStore(provider, database.queries).getKey(legacyKeyId))
            assertNotNull(database.queries.selectByKeyId(legacyKeyId).executeAsOne().crypto2_stored_key)
        }
    }

    @Test
    fun `managed key descriptor restores alias after store recreation`() = runTest {
        database().use { database ->
            val provider = FakePlatformKeyProvider(platformBacked = true)
            val keyId = PlatformKeyStore(provider, database.queries).addKey(JWKKey.generate(KeyType.secp256r1))
            val descriptor = StoredKeyCodec.decodeFromString(
                assertNotNull(database.queries.selectByKeyId(keyId).executeAsOne().crypto2_stored_key)
            )

            assertIs<StoredKey.Managed>(descriptor)
            val recreated = PlatformKeyStore(provider, database.queries)
            assertNotNull(recreated.getKey(keyId))
            assertNotNull(recreated.getCrypto2Key(keyId, setOf(KeyUsage.SIGN)))
            assertTrue(provider.restoreCount >= 2)
            val restoreCount = provider.restoreCount
            assertTrue(recreated.removeKey(keyId))
            assertEquals(1, provider.crypto2DeleteCount)
            assertEquals(restoreCount, provider.restoreCount)
            assertEquals(null, database.queries.selectByKeyId(keyId).executeAsOneOrNull())
        }
    }

    @Test
    fun `directly generated crypto2 managed key survives recreation and signs`() = runTest {
        database().use { database ->
            val provider = FakePlatformKeyProvider()
            val store = PlatformKeyStore(provider, database.queries)
            val key = store.generateCrypto2Key(
                id = KeyId("crypto2-managed"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = KEY_USAGES,
            )

            assertIs<ManagedKey>(key)
            val persisted = database.queries.selectByKeyId(key.id.value).executeAsOne()
            assertEquals(KeyType.secp256r1.name, persisted.key_type)
            assertEquals(null, persisted.key_material)
            assertIs<StoredKey.Managed>(
                StoredKeyCodec.decodeFromString(assertNotNull(persisted.crypto2_stored_key))
            )

            val restored = assertNotNull(
                PlatformKeyStore(provider, database.queries).getCrypto2Key(key.id.value, setOf(KeyUsage.SIGN))
            )
            val signature = assertNotNull(restored.capabilities.signer).sign(
                "payload".encodeToByteArray(),
                SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256),
            )

            assertContentEquals("signed:payload".encodeToByteArray(), signature)
            assertEquals(0, provider.legacyGenerateCount)
            assertEquals(0, provider.softwareExportCount)
        }
    }

    @Test
    fun `failed SQL persistence deletes directly generated platform key`() = runTest {
        val database = database()
        val provider = FakePlatformKeyProvider()
        val store = PlatformKeyStore(provider, database.queries)
        val id = KeyId("failed-insert")
        database.close()

        assertFails {
            store.generateCrypto2Key(
                id = id,
                spec = KeySpec.Ec(EcCurve.P256),
                usages = KEY_USAGES,
            )
        }

        assertEquals(1, provider.crypto2DeleteCount)
        assertFalse(provider.hasManagedKey(id))
    }

    @Test
    fun `failed platform deletion retains SQL metadata`() = runTest {
        database().use { database ->
            val provider = FakePlatformKeyProvider()
            val store = PlatformKeyStore(provider, database.queries)
            val id = KeyId("failed-delete")
            store.generateCrypto2Key(id, KeySpec.Ec(EcCurve.P256), KEY_USAGES)
            provider.crypto2DeleteFailure = IllegalStateException("platform unavailable")

            assertFalse(store.removeKey(id.value))
            assertNotNull(database.queries.selectByKeyId(id.value).executeAsOneOrNull())

            provider.crypto2DeleteFailure = null
            assertTrue(store.removeKey(id.value))
            assertEquals(null, database.queries.selectByKeyId(id.value).executeAsOneOrNull())
        }
    }

    @Test
    fun `crypto2 software JWK persists without legacy material and restores`() = runTest {
        database().use { database ->
            val provider = FakePlatformKeyProvider()
            val softwareKey = CryptoRuntime(listOf(CryptographySoftwareKeyProvider())).generateSoftwareKey(
                GenerateSoftwareKeyRequest(
                    id = KeyId("crypto2-software"),
                    spec = KeySpec.Edwards(EdwardsCurve.ED25519),
                    usages = KEY_USAGES,
                )
            )
            val store = PlatformKeyStore(provider, database.queries)

            assertEquals(softwareKey.id.value, store.addCrypto2Key(softwareKey))
            val persisted = database.queries.selectByKeyId(softwareKey.id.value).executeAsOne()
            assertEquals(KeyType.Ed25519.name, persisted.key_type)
            assertEquals(null, persisted.key_material)
            val restored = assertNotNull(
                PlatformKeyStore(provider, database.queries)
                    .getCrypto2Key(softwareKey.id.value, setOf(KeyUsage.SIGN))
            )

            assertTrue(
                assertNotNull(restored.capabilities.signer)
                    .sign("payload".encodeToByteArray(), SignatureAlgorithm.EdDsa)
                    .isNotEmpty()
            )
            assertEquals(0, provider.softwareLoadCount)
        }
    }

    @Test
    fun `crypto2 generation rejects specs the compatibility schema cannot represent`() = runTest {
        database().use { database ->
            val provider = FakePlatformKeyProvider()
            val failure = assertFailsWith<IllegalArgumentException> {
                PlatformKeyStore(provider, database.queries).generateCrypto2Key(
                    id = KeyId("unsupported-spec"),
                    spec = KeySpec.Edwards(EdwardsCurve.ED448),
                    usages = KEY_USAGES,
                )
            }

            assertTrue(failure.message.orEmpty().contains("Edwards curve"))
            assertEquals(0, provider.managedGenerateCount)
            assertEquals(null, database.queries.selectByKeyId("unsupported-spec").executeAsOneOrNull())
        }
    }

    @Test
    fun `crypto2 generation requires a crypto2 platform provider`() = runTest {
        database().use { database ->
            val legacyProvider: PlatformKeyProvider = object : PlatformKeyProvider by FakePlatformKeyProvider() {}
            val failure = assertFailsWith<IllegalArgumentException> {
                PlatformKeyStore(legacyProvider, database.queries).generateCrypto2Key(
                    id = KeyId("missing-provider"),
                    spec = KeySpec.Ec(EcCurve.P256),
                    usages = KEY_USAGES,
                )
            }

            assertTrue(failure.message.orEmpty().contains("does not support managed crypto2 keys"))
            assertEquals(null, database.queries.selectByKeyId("missing-provider").executeAsOneOrNull())
        }
    }

    @Test
    fun `crypto2 persistence rejects non JWK software material`() = runTest {
        database().use { database ->
            val stored = StoredKey.Software(
                version = StoredKey.CURRENT_VERSION,
                id = KeyId("unsupported-material"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.VERIFY),
                material = EncodedKey.SpkiDer(BinaryData(byteArrayOf(1))),
            )
            val key = object : SoftwareKey {
                override val storedKey = stored
            }

            val failure = assertFailsWith<IllegalArgumentException> {
                PlatformKeyStore(FakePlatformKeyProvider(), database.queries).addCrypto2Key(key)
            }

            assertTrue(failure.message.orEmpty().contains("stored as JWK only"))
            assertEquals(null, database.queries.selectByKeyId(stored.id.value).executeAsOneOrNull())
        }
    }

    @Test
    fun `malformed crypto2 descriptor never downgrades to v1 material`() = runTest {
        database().use { database ->
            val provider = FakePlatformKeyProvider()
            val keyId = PlatformKeyStore(provider, database.queries).addKey(JWKKey.generate(KeyType.Ed25519))
            database.queries.updateCrypto2StoredKey("{malformed", keyId)
            val loadCount = provider.softwareLoadCount

            assertFails { PlatformKeyStore(provider, database.queries).getKey(keyId) }
            assertEquals(loadCount, provider.softwareLoadCount)
            assertTrue(PlatformKeyStore(provider, database.queries).removeKey(keyId))
            assertEquals(null, database.queries.selectByKeyId(keyId).executeAsOneOrNull())
        }
    }

    @Test
    fun `stale software descriptor is repaired from current key material`() = runTest {
        database().use { database ->
            val provider = FakePlatformKeyProvider()
            val store = PlatformKeyStore(provider, database.queries)
            val keyId = store.addKey(JWKKey.generate(KeyType.Ed25519))
            val oldRow = database.queries.selectByKeyId(keyId).executeAsOne()
            val replacement = JWKKey.generate(KeyType.Ed25519)
            database.queries.insert(
                key_id = oldRow.key_id,
                key_type = replacement.keyType.name,
                created_at = oldRow.created_at,
                is_platform_backed = 0L,
                key_material = replacement.exportJWK(),
                crypto2_stored_key = oldRow.crypto2_stored_key,
            )

            assertNotNull(store.getCrypto2Key(keyId, setOf(KeyUsage.SIGN)))
            val repaired = database.queries.selectByKeyId(keyId).executeAsOne().crypto2_stored_key

            assertTrue(repaired != oldRow.crypto2_stored_key)
        }
    }

    @Test
    fun `crypto2 descriptor kind must match persisted key backing`() = runTest {
        database().use { database ->
            val provider = FakePlatformKeyProvider()
            val store = PlatformKeyStore(provider, database.queries)
            val keyId = store.addKey(JWKKey.generate(KeyType.Ed25519))
            val row = database.queries.selectByKeyId(keyId).executeAsOne()
            database.queries.insert(
                key_id = row.key_id,
                key_type = row.key_type,
                created_at = row.created_at,
                is_platform_backed = 1L,
                key_material = row.key_material,
                crypto2_stored_key = row.crypto2_stored_key,
            )

            assertFails { store.getKey(keyId) }
        }
    }

    @Test
    fun `corrupt managed descriptor falls back to authoritative alias deletion`() = runTest {
        database().use { database ->
            val provider = FakePlatformKeyProvider(platformBacked = true)
            val store = PlatformKeyStore(provider, database.queries)
            val keyId = store.addKey(JWKKey.generate(KeyType.secp256r1))
            val stored = assertIs<StoredKey.Managed>(
                StoredKeyCodec.decodeFromString(
                    assertNotNull(database.queries.selectByKeyId(keyId).executeAsOne().crypto2_stored_key)
                )
            )
            database.queries.updateCrypto2StoredKey(
                StoredKeyCodec.encodeToString(stored.copy(provider = ProviderId("corrupt-provider"))),
                keyId,
            )

            assertTrue(store.removeKey(keyId))
            assertEquals(1, provider.legacyDeleteCount)
            assertEquals(null, database.queries.selectByKeyId(keyId).executeAsOneOrNull())
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

    private class FakePlatformKeyProvider(
        private val platformBacked: Boolean = false,
    ) : PlatformKeyProvider, Crypto2PlatformKeyProvider {
        override val supportedPlatformKeyTypes = PlatformKeyProvider.DEFAULT_SUPPORTED_PLATFORM_KEY_TYPES
        private var key: Key? = null
        var restoreCount = 0
        var crypto2DeleteCount = 0
        var legacyDeleteCount = 0
        var softwareLoadCount = 0
        var legacyGenerateCount = 0
        var softwareExportCount = 0
        var managedGenerateCount = 0
        var crypto2DeleteFailure: Throwable? = null
        private val managedKeys = mutableSetOf<KeyId>()

        fun hasManagedKey(id: KeyId): Boolean = id in managedKeys

        override suspend fun generateKey(keyType: KeyType, keyId: String?): Key {
            legacyGenerateCount++
            return JWKKey.generate(keyType).also { key = it }
        }

        override suspend fun loadKey(keyId: String, keyType: KeyType): Key? = key

        override suspend fun loadSoftwareKey(keyId: String, keyType: KeyType, jwkMaterial: ByteArray): Key {
            softwareLoadCount++
            return JWKKey.importJWK(jwkMaterial.decodeToString()).getOrThrow()
        }

        override suspend fun exportSoftwareKeyMaterial(key: Key): ByteArray {
            softwareExportCount++
            return key.exportJWK().encodeToByteArray()
        }

        override suspend fun deleteKey(keyId: String, keyType: KeyType): Boolean {
            legacyDeleteCount++
            return (key != null).also { key = null }
        }

        override fun isPlatformBacked(key: Key): Boolean {
            this.key = key
            return platformBacked
        }

        override suspend fun generateManagedKey(
            id: KeyId,
            spec: KeySpec,
            usages: Set<KeyUsage>,
            policy: SignumKeyPolicy?,
        ): ManagedKey {
            managedGenerateCount++
            managedKeys += id
            return managedKey(
                StoredKey.Managed(
                    version = StoredKey.CURRENT_VERSION,
                    id = id,
                    spec = spec,
                    usages = usages,
                    provider = ProviderId("fake-mobile-platform"),
                    providerSchemaVersion = 1,
                    providerData = BinaryData(id.value.encodeToByteArray()),
                )
            )
        }

        override suspend fun migratePlatformKey(
            id: KeyId,
            keyType: KeyType,
            usages: Set<KeyUsage>,
        ): StoredKey.Managed {
            managedKeys += id
            return StoredKey.Managed(
                version = StoredKey.CURRENT_VERSION,
                id = id,
                spec = KeySpec.Ec(EcCurve.P256),
                usages = usages,
                provider = ProviderId("fake-mobile-platform"),
                providerSchemaVersion = 1,
                providerData = BinaryData(id.value.encodeToByteArray()),
            )
        }

        override suspend fun restoreManagedKey(stored: StoredKey.Managed): Crypto2Key {
            require(stored.provider == ProviderId("fake-mobile-platform"))
            require(stored.id in managedKeys)
            restoreCount++
            return managedKey(stored)
        }

        override suspend fun deleteManagedKey(stored: StoredKey.Managed) {
            require(stored.provider == ProviderId("fake-mobile-platform"))
            crypto2DeleteFailure?.let { throw it }
            managedKeys -= stored.id
            crypto2DeleteCount++
        }

        private fun managedKey(stored: StoredKey.Managed): ManagedKey = object : ManagedKey {
            override val storedKey = stored
            override val capabilities = KeyCapabilities(
                signer = Signer { data, _ -> "signed:".encodeToByteArray() + data },
            )
        }
    }

    private companion object {
        val KEY_USAGES = setOf(KeyUsage.SIGN, KeyUsage.VERIFY)
    }
}
