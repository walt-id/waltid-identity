@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.wallet2.mobile

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.KeyCapabilities
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.Key as ManagedKeyMaterial
import id.walt.crypto2.keys.ManagedKey
import id.walt.crypto2.keys.ProviderId
import id.walt.crypto2.keys.SoftwareKey
import id.walt.crypto2.keys.StoredKey
import id.walt.crypto2.keys.toPublicJwk
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import id.walt.crypto2.serialization.BinaryData
import id.walt.crypto2.serialization.StoredKeyCodec
import id.walt.crypto2.signum.SignumKeyPolicy
import id.walt.did.dids.Crypto2DidService
import id.walt.did.dids.DidService
import id.walt.did.dids.registrar.DidResult
import id.walt.did.dids.registrar.dids.DidCreateOptions
import id.walt.openid4vp.clientidprefix.ClientIdTrustConfiguration
import id.walt.wallet2.persistence.db.WalletPersistenceDatabase
import id.walt.wallet2.persistence.keys.PlatformManagedKeyProvider
import id.walt.wallet2.persistence.stores.PlatformKeyStore
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

class MobileWalletFactoryTest {
    @Test
    fun `default bootstrap persists restarts and signs with public-only DIDs`() = runTest {
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
                val provider = FakePlatformManagedKeyProvider()
                val config = MobileWalletConfig(defaultKeyType = case.keyType)
                val wallet = wallet(config, database, provider)

                val bootstrap = wallet.bootstrap(didMethod = case.didMethod)
                val row = database.queries.selectByKeyId(bootstrap.keyId).executeAsOne()
                val stored = assertIs<StoredKey.Managed>(
                    StoredKeyCodec.decodeFromString(assertNotNull(row.stored_key))
                )
                assertEquals(stored.id.value, bootstrap.keyId)
                assertTrue(bootstrap.did.startsWith("did:${case.didMethod}:"))
                assertStoredDidContainsPublicMaterialOnly(database, bootstrap.did)
                assertTrue(DidService.resolverMethods.containsKey(case.didMethod))

                val recreatedWallet = wallet(config, database, provider)
                assertEquals(bootstrap, recreatedWallet.bootstrap(didMethod = case.didMethod))
                assertTrue(DidService.resolverMethods.containsKey(case.didMethod))
                assertEquals(1, provider.generateCount)

                val restored = assertNotNull(
                    PlatformKeyStore(provider, database.queries)
                        .getCrypto2Key(bootstrap.keyId, setOf(KeyUsage.SIGN))
                )
                val message = "mobile-key-bootstrap".encodeToByteArray()
                val signature = assertNotNull(restored.capabilities.signer)
                    .sign(message, case.signatureAlgorithm)
                assertTrue(
                    assertNotNull(restored.capabilities.verifier)
                        .verify(message, signature, case.signatureAlgorithm)
                )
                assertDidMatchesPublicKey(bootstrap.did, restored)
            }
        }
    }

    @Test
    fun `unsupported DID method fails before key generation`() = runTest {
        database().use { database ->
            val provider = FakePlatformManagedKeyProvider()

            val failure = assertFailsWith<IllegalArgumentException> {
                wallet(MobileWalletConfig(), database, provider).bootstrap(didMethod = "web")
            }

            assertTrue(failure.message.orEmpty().contains("supports only did:key and did:jwk"))
            assertEquals(0, provider.generateCount)
            assertTrue(database.queries.selectAll().executeAsList().isEmpty())
        }
    }

    @Test
    fun `configured DID service registers the requested method`() = runTest {
        database().use { database ->
            val provider = FakePlatformManagedKeyProvider()
            val didService = RecordingDidService()

            val bootstrap = wallet(
                database = database,
                provider = provider,
                config = MobileWalletConfig(),
                didService = didService,
            ).bootstrap(didMethod = "jwk")

            assertTrue(bootstrap.did.startsWith("did:jwk:"))
            assertEquals(listOf("jwk"), didService.registeredMethods)
        }
    }

    @Test
    fun `DID registration failure removes the persisted key`() = runTest {
        database().use { database ->
            val provider = FakePlatformManagedKeyProvider()
            val failure = assertFailsWith<IllegalStateException> {
                wallet(
                    config = MobileWalletConfig(),
                    database = database,
                    provider = provider,
                    didService = object : Crypto2DidService by Crypto2DidService {
                        override suspend fun registerByKey(
                            method: String,
                            key: ManagedKeyMaterial,
                            options: DidCreateOptions,
                        ): DidResult = error("DID registration failed")
                    },
                ).bootstrap()
            }

            assertTrue(failure.message.orEmpty().contains("DID registration failed"))
            assertTrue(database.queries.selectAll().executeAsList().isEmpty())
            assertEquals(1, provider.deleteCount)
        }
    }

    private fun wallet(
        config: MobileWalletConfig,
        database: TestDatabase,
        provider: FakePlatformManagedKeyProvider,
        didService: Crypto2DidService = Crypto2DidService,
    ): MobileWallet = createSqlDelightMobileWallet(
        config = config,
        clientIdTrustConfiguration = ClientIdTrustConfiguration(),
        db = database.database,
        keyProvider = provider,
        didService = didService,
        deleteLocalPersistence = {},
    )

    private suspend fun assertDidMatchesPublicKey(did: String, original: ManagedKeyMaterial) {
        val resolved = Crypto2DidService.resolveToKeys(did).getOrThrow().single()
        assertEquals(publicMembers(publicJwk(original)), publicMembers(publicJwk(resolved)))
    }

    private suspend fun publicJwk(key: ManagedKeyMaterial): JsonObject {
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

    private class FakePlatformManagedKeyProvider : PlatformManagedKeyProvider {
        private val softwareProvider = CryptographySoftwareKeyProvider()
        private val keys = mutableMapOf<KeyId, SoftwareKey>()
        var generateCount = 0
        var deleteCount = 0

        override suspend fun generateManagedKey(
            id: KeyId,
            spec: KeySpec,
            usages: Set<KeyUsage>,
            policy: SignumKeyPolicy?,
        ): ManagedKey {
            generateCount++
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

        override suspend fun restoreManagedKey(stored: StoredKey.Managed): ManagedKeyMaterial {
            require(stored.provider == PROVIDER_ID)
            return managedKey(stored, requireNotNull(keys[stored.id]))
        }

        override suspend fun deleteManagedKey(stored: StoredKey.Managed) {
            deleteCount++
            keys.remove(stored.id)
        }

        private fun managedKey(stored: StoredKey.Managed, software: SoftwareKey): ManagedKey = object : ManagedKey {
            override val storedKey = stored
            override val capabilities: KeyCapabilities = software.capabilities
        }

        private companion object {
            val PROVIDER_ID = ProviderId("mobile-bootstrap-test")
        }
    }

    private class RecordingDidService : Crypto2DidService by Crypto2DidService {
        val registeredMethods = mutableListOf<String>()

        override suspend fun registerByKey(
            method: String,
            key: ManagedKeyMaterial,
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
