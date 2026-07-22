@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.wallet2.mobile

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.KeyCapabilities
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.Key as Crypto2Key
import id.walt.crypto2.keys.ManagedKey
import id.walt.crypto2.keys.ProviderId
import id.walt.crypto2.keys.SoftwareKey
import id.walt.crypto2.keys.StoredKey
import id.walt.crypto2.keys.toPublicJwk
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import id.walt.crypto2.serialization.BinaryData
import id.walt.crypto2.serialization.StoredKeyCodec
import id.walt.crypto2.signum.SignumHardwarePolicy
import id.walt.crypto2.signum.SignumKeyPolicy
import id.walt.did.dids.Crypto2DidService
import id.walt.did.dids.DidService
import id.walt.did.dids.registrar.DidResult
import id.walt.did.dids.registrar.dids.DidCreateOptions
import id.walt.openid4vp.clientidprefix.ClientIdTrustConfiguration
import id.walt.wallet2.persistence.db.WalletPersistenceDatabase
import id.walt.wallet2.persistence.keys.Crypto2PlatformKeyProvider
import id.walt.wallet2.persistence.keys.PlatformKeyProvider
import id.walt.wallet2.persistence.stores.PlatformKeyStore
import id.walt.wallet2.stores.inmemory.InMemoryKeyStore
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MobileWalletCrypto2BootstrapTest {
    @Test
    fun `default crypto2 bootstrap persists restarts and signs with public-only DIDs`() = runTest {
        val cases = listOf(
            BootstrapCase(
                keyType = MobileWalletKeyType.secp256r1,
                didMethod = "key",
                signatureAlgorithm = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256),
            ),
            BootstrapCase(
                keyType = MobileWalletKeyType.secp384r1,
                didMethod = "jwk",
                signatureAlgorithm = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_384),
            ),
        )

        cases.forEach { case ->
            database().use { database ->
                val provider = FakeCrypto2PlatformKeyProvider()
                val config = MobileWalletConfig(defaultKeyType = case.keyType)
                val wallet = wallet(config, database, provider)

                val bootstrap = wallet.bootstrap(didMethod = case.didMethod)
                val row = database.queries.selectByKeyId(bootstrap.keyId).executeAsOne()
                val stored = assertIs<StoredKey.Managed>(
                    StoredKeyCodec.decodeFromString(assertNotNull(row.crypto2_stored_key))
                )
                assertEquals(null, row.key_material)
                assertEquals(stored.id.value, bootstrap.keyId)
                assertTrue(bootstrap.did.startsWith("did:${case.didMethod}:"))
                assertStoredDidContainsPublicMaterialOnly(database, bootstrap.did)

                val recreatedWallet = wallet(config, database, provider)
                assertEquals(bootstrap, recreatedWallet.bootstrap(didMethod = case.didMethod))
                assertEquals(1, provider.generateCount)

                val restored = assertNotNull(
                    PlatformKeyStore(provider, database.queries)
                        .getCrypto2Key(bootstrap.keyId, setOf(KeyUsage.SIGN))
                )
                val message = "mobile-crypto2-bootstrap".encodeToByteArray()
                val signature = assertNotNull(restored.capabilities.signer)
                    .sign(message, case.signatureAlgorithm)
                assertTrue(
                    assertNotNull(restored.capabilities.verifier)
                        .verify(message, signature, case.signatureAlgorithm)
                )
                assertDidMatchesPublicKey(bootstrap.did, restored)
                assertEquals(0, provider.legacyCalls)
            }
        }
    }

    @Test
    fun `unsupported DID method fails before crypto2 generation`() = runTest {
        database().use { database ->
            val provider = FakeCrypto2PlatformKeyProvider()

            val failure = assertFailsWith<IllegalArgumentException> {
                wallet(MobileWalletConfig(), database, provider).bootstrap(didMethod = "web")
            }

            assertTrue(failure.message.orEmpty().contains("supports only did:key and did:jwk"))
            assertEquals(0, provider.generateCount)
            assertTrue(database.queries.selectAll().executeAsList().isEmpty())
        }
    }

    @Test
    fun `explicit MobileWalletKeys override retains legacy bootstrap`() = runTest {
        database().use { database ->
            val provider = FakeCrypto2PlatformKeyProvider()
            val legacyStore = InMemoryKeyStore()
            val config = MobileWalletConfig(
                persistence = MobileWalletPersistence(
                    stores = MobileWalletStores(
                        keys = MobileWalletKeys(
                            store = legacyStore,
                            generate = { JWKKey.generate(it) },
                        )
                    )
                )
            )

            val bootstrap = wallet(config, database, provider).bootstrap()

            assertNotNull(legacyStore.getKey(bootstrap.keyId))
            assertTrue(bootstrap.did.startsWith("did:key:"))
            assertEquals(0, provider.generateCount)
            assertEquals(0, provider.legacyCalls)
        }
    }

    @Test
    fun `explicit crypto2 customization configures DID service and key policy`() = runTest {
        database().use { database ->
            val provider = FakeCrypto2PlatformKeyProvider()
            val didService = RecordingCrypto2DidService()
            val policy = SignumKeyPolicy(hardware = SignumHardwarePolicy.DISCOURAGED)

            val bootstrap = wallet(
                config = MobileWalletConfig(),
                database = database,
                provider = provider,
                crypto2Config = MobileWalletCrypto2Config(didService, policy),
            ).bootstrap(didMethod = "jwk")

            assertTrue(bootstrap.did.startsWith("did:jwk:"))
            assertEquals(listOf("jwk"), didService.registeredMethods)
            assertEquals(policy, provider.lastPolicy)
        }
    }

    @Test
    fun `crypto2 DID registration failure removes persisted key without legacy fallback`() = runTest {
        database().use { database ->
            val provider = FakeCrypto2PlatformKeyProvider()
            val failure = assertFailsWith<IllegalStateException> {
                wallet(
                    config = MobileWalletConfig(),
                    database = database,
                    provider = provider,
                    crypto2Config = MobileWalletCrypto2Config(
                        didService = object : Crypto2DidService by Crypto2DidService {
                            override suspend fun registerByKey(
                                method: String,
                                key: Crypto2Key,
                                options: DidCreateOptions,
                            ): DidResult = error("DID registration failed")
                        }
                    ),
                ).bootstrap()
            }

            assertTrue(failure.message.orEmpty().contains("DID registration failed"))
            assertTrue(database.queries.selectAll().executeAsList().isEmpty())
            assertEquals(1, provider.deleteCount)
            assertEquals(0, provider.legacyCalls)
        }
    }

    private fun wallet(
        config: MobileWalletConfig,
        database: TestDatabase,
        provider: FakeCrypto2PlatformKeyProvider,
        crypto2Config: MobileWalletCrypto2Config? = null,
    ): MobileWallet = createSqlDelightMobileWallet(
        config = config,
        clientIdTrustConfiguration = ClientIdTrustConfiguration(),
        crypto2Config = crypto2Config,
        db = database.database,
        keyProvider = provider,
        deleteLocalPersistence = {},
    )

    private suspend fun assertDidMatchesPublicKey(did: String, original: Crypto2Key) {
        DidService.minimalInit()
        val resolved = Crypto2DidService.resolveToKeys(did).getOrThrow().single()
        assertEquals(publicMembers(publicJwk(original)), publicMembers(publicJwk(resolved)))
    }

    private suspend fun publicJwk(key: Crypto2Key): JsonObject {
        val encoded = assertNotNull(key.capabilities.publicKeyExporter).exportPublicKey().toPublicJwk(key.spec)
        return Json.parseToJsonElement(encoded.data.toByteArray().decodeToString()).jsonObject
    }

    private fun assertStoredDidContainsPublicMaterialOnly(database: TestDatabase, did: String) {
        val document = Json.parseToJsonElement(
            database.queries.selectDidByDid(did).executeAsOne().document
        ).jsonObject
        val publicJwk = document.getValue("verificationMethod").jsonArray.single().jsonObject
            .getValue("publicKeyJwk").jsonObject
        assertTrue(PRIVATE_JWK_MEMBERS.none(publicJwk::containsKey))
    }

    private fun publicMembers(jwk: JsonObject): JsonObject = when (jwk.getValue("kty").jsonPrimitive.content) {
        "EC" -> JsonObject(jwk.filterKeys { it in setOf("kty", "crv", "x", "y") })
        "OKP" -> JsonObject(jwk.filterKeys { it in setOf("kty", "crv", "x") })
        "RSA" -> JsonObject(jwk.filterKeys { it in setOf("kty", "n", "e") })
        else -> error("Unsupported public JWK")
    }

    private fun database(): TestDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        WalletPersistenceDatabase.Schema.create(driver)
        return TestDatabase(driver, WalletPersistenceDatabase(driver))
    }

    private data class BootstrapCase(
        val keyType: MobileWalletKeyType,
        val didMethod: String,
        val signatureAlgorithm: SignatureAlgorithm,
    )

    private class TestDatabase(
        private val driver: JdbcSqliteDriver,
        val database: WalletPersistenceDatabase,
    ) : AutoCloseable {
        val queries = database.walletPersistenceQueries

        override fun close() = driver.close()
    }

    private class FakeCrypto2PlatformKeyProvider : PlatformKeyProvider, Crypto2PlatformKeyProvider {
        private val softwareProvider = CryptographySoftwareKeyProvider()
        private val keys = mutableMapOf<KeyId, SoftwareKey>()
        override val supportedPlatformKeyTypes: Set<KeyType> = emptySet()
        var generateCount = 0
        var deleteCount = 0
        var legacyCalls = 0
        var lastPolicy: SignumKeyPolicy? = null

        override suspend fun generateManagedKey(
            id: KeyId,
            spec: KeySpec,
            usages: Set<KeyUsage>,
            policy: SignumKeyPolicy?,
        ): ManagedKey {
            generateCount++
            lastPolicy = policy
            val software = softwareProvider.generate(GenerateSoftwareKeyRequest(id, spec, usages))
            keys[id] = software
            val publicKey = assertNotNull(software.capabilities.publicKeyExporter).exportPublicKey().toPublicJwk(spec)
            return managedKey(
                StoredKey.Managed(
                    version = StoredKey.CURRENT_VERSION,
                    id = id,
                    spec = spec,
                    usages = usages,
                    provider = PROVIDER_ID,
                    providerSchemaVersion = 1,
                    providerData = BinaryData(id.value.encodeToByteArray()),
                    publicKey = publicKey,
                ),
                software,
            )
        }

        override suspend fun restoreManagedKey(stored: StoredKey.Managed): Crypto2Key {
            require(stored.provider == PROVIDER_ID)
            return managedKey(stored, requireNotNull(keys[stored.id]))
        }

        override suspend fun deleteManagedKey(stored: StoredKey.Managed) {
            deleteCount++
            keys.remove(stored.id)
        }

        override suspend fun migratePlatformKey(
            id: KeyId,
            keyType: KeyType,
            usages: Set<KeyUsage>,
        ): StoredKey.Managed = error("Legacy key migration must not be used")

        override suspend fun generateKey(keyType: KeyType, keyId: String?): Key {
            legacyCalls++
            error("Default crypto2 bootstrap must not generate a v1 key")
        }

        override suspend fun loadKey(keyId: String, keyType: KeyType): Key? {
            legacyCalls++
            return null
        }

        override suspend fun loadSoftwareKey(keyId: String, keyType: KeyType, jwkMaterial: ByteArray): Key? {
            legacyCalls++
            return null
        }

        override suspend fun exportSoftwareKeyMaterial(key: Key): ByteArray {
            legacyCalls++
            error("Default crypto2 bootstrap must not export v1 material")
        }

        override suspend fun deleteKey(keyId: String, keyType: KeyType): Boolean {
            legacyCalls++
            return false
        }

        private fun managedKey(stored: StoredKey.Managed, software: SoftwareKey): ManagedKey = object : ManagedKey {
            override val storedKey = stored
            override val capabilities: KeyCapabilities = software.capabilities
        }

        private companion object {
            val PROVIDER_ID = ProviderId("mobile-bootstrap-test")
        }
    }

    private class RecordingCrypto2DidService : Crypto2DidService by Crypto2DidService {
        val registeredMethods = mutableListOf<String>()

        override suspend fun registerByKey(
            method: String,
            key: Crypto2Key,
            options: DidCreateOptions,
        ): DidResult {
            registeredMethods += method
            return Crypto2DidService.registerByKey(method, key, options)
        }
    }

    private companion object {
        val PRIVATE_JWK_MEMBERS = setOf("d", "p", "q", "dp", "dq", "qi", "oth", "k")
    }
}
