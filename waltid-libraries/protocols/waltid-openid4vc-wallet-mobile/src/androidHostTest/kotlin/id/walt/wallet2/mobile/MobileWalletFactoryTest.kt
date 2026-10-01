@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.wallet2.mobile

import id.walt.crypto2.keys.Key as ManagedKeyMaterial
import id.walt.wallet2.mobile.identity.*
import id.walt.crypto2.keys.PlatformKeyFacts
import id.walt.wallet2.persistence.keys.WalletKeyProtection
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.KeyCapabilities
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.ManagedKey
import id.walt.crypto2.keys.ProviderId
import id.walt.crypto2.keys.SoftwareKey
import id.walt.crypto2.keys.StoredKey
import id.walt.crypto2.keys.toPublicJwk
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import id.walt.crypto2.serialization.BinaryData
import id.walt.crypto2.serialization.StoredKeyCodec
import id.walt.crypto2.signum.SignumKeyPolicyMismatchException
import id.walt.did.dids.Crypto2DidService
import id.walt.did.dids.DidService
import id.walt.did.dids.registrar.DidResult
import id.walt.did.dids.registrar.dids.DidCreateOptions
import id.walt.openid4vp.clientidprefix.ClientIdTrustConfiguration
import id.walt.wallet2.persistence.db.WalletPersistenceDatabase
import id.walt.crypto2.keys.KeyUseAuthorizationException
import id.walt.crypto2.keys.KeyUseAuthorizationFailure
import id.walt.crypto2.keys.KeyUseAuthorizationPolicy
import id.walt.crypto2.keys.KeyUseAuthorizationReuseEnforcement
import id.walt.crypto2.keys.KeyUseAuthorizationReuseTimeoutValidation
import id.walt.wallet2.persistence.keys.WalletKeyCreationRequest
import id.walt.wallet2.persistence.keys.WalletKeyRequirements
import id.walt.crypto2.keys.KeyUseAuthorizationSupport
import id.walt.crypto2.keys.KeyUseAuthorizationUnsupportedReason
import id.walt.wallet2.persistence.keys.PlatformManagedKeyRestoration
import id.walt.wallet2.persistence.keys.PlatformManagedKeyProvider
import id.walt.wallet2.persistence.stores.SqlDelightKeyStore
import id.walt.credentials.CredentialParser
import id.walt.credentials.examples.SdJwtExamples
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.did.dids.registrar.dids.DidKeyCreateOptions
import id.walt.mdoc.proximity.ReaderSelectedTransportProvider
import id.walt.mdoc.proximity.mobile.BleMdocRoleSelection
import id.walt.mdoc.proximity.mobile.BleProximityAvailability
import id.walt.mdoc.proximity.mobile.BleProximityTransportConfiguration
import id.walt.mdoc.proximity.mobile.BleProximityTransportFactory
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.persistence.encryption.DatabaseEncryptionKey
import id.walt.wallet2.persistence.encryption.DatabaseEncryptionKeyProvider
import id.walt.wallet2.persistence.stores.SqlDelightCredentialStore
import id.waltid.openid4vp.wallet.request.AuthorizationRequestResolver
import io.ktor.http.URLBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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
    fun `cold factory reopens saved identity and authenticates DID requests without creating keys`() = runTest {
        database().use { database ->
            val provider = FakePlatformManagedKeyProvider()
            val config = MobileWalletConfig()
            val original = wallet(config, database, provider)
            val identity = assertIs<SigningIdentityOperationResult.Active>(original.signingIdentity.initialize()).identity
            SqlDelightCredentialStore(database.queries).addCredential(StoredCredential(
                id = "saved-pid",
                credential = CredentialParser.detectAndParse(SdJwtExamples.sdJwtVcSignedExample2).second,
            ))
            val savedCredentials = original.credentials()
            val verifier = JWKKey.generate(KeyType.secp256r1)
            val verifierDid = DidService.registerByKey("key", verifier, DidKeyCreateOptions()).did
            val clientId = "decentralized_identifier:$verifierDid"

            withColdDidSupport {
                assertTrue(DidService.resolverMethods.isEmpty())
                assertTrue(Crypto2DidService.resolveToKeys(verifierDid).isFailure)
                val reopened = encryptedWallet(config, database, provider)
                // Authenticate before initialize(): resolver setup must not depend on identity work.
                val online = assertIs<MobileWalletPresentationPreviewResult.Ready>(reopened.previewPresentation(
                    requestUrl(clientId, signedDidRequest(verifier, verifierDid, "direct_post")),
                )).preview
                assertEquals(clientId, online.request.clientId)
                assertEquals("saved-pid", online.credentialOptions.single().credentialId)
                assertIs<MobileWalletRequestAuthentication.Authenticated>(online.request.requestAuthentication)
                val dc = reopened.previewDigitalCredentialPresentation(MobileWalletDigitalCredentialRequest(
                    protocol = MobileWalletDigitalCredentialProtocols.OPENID4VP_SIGNED,
                    dataJson = buildJsonObject { put("request", signedDidRequest(verifier, verifierDid, "dc_api")) }.toString(),
                    verifiedOrigin = "https://verifier.example",
                ))
                assertEquals(clientId, dc.request.clientId)
                assertEquals("saved-pid", dc.credentialOptions.single().credentialId)
                assertEquals(identity, assertIs<SigningIdentityOperationResult.Active>(reopened.signingIdentity.initialize()).identity)
                assertEquals(savedCredentials, reopened.credentials())
                assertEquals(1, provider.generateCount)
                assertEquals(0, provider.deleteCount)

                val badSignature = signedDidRequest(JWKKey.generate(KeyType.secp256r1), verifierDid, "direct_post")
                assertFailsWith<AuthorizationRequestResolver.SignedAuthorizationRequestValidationException> {
                    reopened.previewPresentation(requestUrl(clientId, badSignature))
                }
                val badKid = signedDidRequest(verifier, verifierDid, "direct_post", "$verifierDid#missing")
                assertFailsWith<AuthorizationRequestResolver.SignedAuthorizationRequestValidationException> {
                    reopened.previewPresentation(requestUrl(clientId, badKid))
                }
            }
        }
    }

    @Test
    fun `concurrent factory opens initialize local resolvers only once`() = runTest {
        withColdDidSupport {
            (1..8).map {
                async(Dispatchers.Default) {
                    database().use { database ->
                        encryptedWallet(MobileWalletConfig(), database, FakePlatformManagedKeyProvider())
                    }
                }
            }.awaitAll()
            assertEquals(1, DidService.didResolvers.size)
            assertEquals(1, DidService.didRegistrars.size)
            assertTrue(DidService.resolverMethods.keys.containsAll(listOf("key", "jwk")))
        }
    }

    private suspend fun encryptedWallet(
        config: MobileWalletConfig,
        database: TestDatabase,
        provider: FakePlatformManagedKeyProvider,
    ): MobileWallet = createEncryptedSqlDelightMobileWallet(
        config = config,
        clientIdTrustConfiguration = ClientIdTrustConfiguration(),
        managedDatabaseKeyProvider = object : DatabaseEncryptionKeyProvider {
            override suspend fun getOrCreateKey(walletId: String, databaseName: String) =
                DatabaseEncryptionKey("test", ByteArray(32))
            override suspend fun deleteKey(walletId: String, databaseName: String) = Unit
        },
        platformKeyProvider = provider,
        proximityTransportFactory = object : BleProximityTransportFactory {
            override suspend fun capability(roles: BleMdocRoleSelection) =
                BleProximityAvailability.Unavailable("test", "No transport needed")
            override fun create(configuration: BleProximityTransportConfiguration): ReaderSelectedTransportProvider =
                error("No transport needed")
        },
        openEncryptedDriver = { _, _, _, _ -> database.driver },
        deleteDatabase = {},
    )

    private suspend fun signedDidRequest(
        key: JWKKey,
        did: String,
        responseMode: String,
        kid: String = "$did#${did.removePrefix("did:key:")}",
    ): String = key.signJws(
        buildJsonObject {
            put("client_id", "decentralized_identifier:$did")
            put("aud", AuthorizationRequestResolver.DEFAULT_REQUEST_OBJECT_AUDIENCE)
            put("response_type", "vp_token")
            put("response_mode", responseMode)
            put("nonce", "cold-start-nonce")
            if (responseMode == "direct_post") put("response_uri", "https://verifier.example/response")
            put("expected_origins", Json.parseToJsonElement("[\"https://verifier.example\"]"))
            put("client_metadata", Json.parseToJsonElement("""{"vp_formats_supported":{"dc+sd-jwt":{"sd-jwt_alg_values":["ES256"],"kb-jwt_alg_values":["ES256"]}}}"""))
            put("dcql_query", Json.parseToJsonElement("""{"credentials":[{"id":"pid","format":"dc+sd-jwt","meta":{"vct_values":["https://credentials.example.com/identity_credential"]},"claims":[{"path":["family_name"]}]}]}"""))
        }.toString().encodeToByteArray(),
        mapOf("typ" to JsonPrimitive("oauth-authz-req+jwt"), "kid" to JsonPrimitive(kid)),
    )

    private fun requestUrl(clientId: String, request: String): String = URLBuilder("openid4vp://authorize").apply {
        parameters.append("client_id", clientId)
        parameters.append("request", request)
    }.buildString()

    // Isolate both pieces of process-local state. Resetting resolver maps alone leaves the
    // once-only initializer warm and cannot reproduce a real cold start. Restore other tests' state.
    private suspend fun withColdDidSupport(block: suspend () -> Unit) {
        val initialized = MobileDidSupport::class.java.getDeclaredField("initialized").apply { isAccessible = true }
        val wasInitialized = initialized.getBoolean(MobileDidSupport)
        val resolvers = DidService.didResolvers.toList()
        val registrars = DidService.didRegistrars.toList()
        val resolverMethods = DidService.resolverMethods.toMap()
        val registrarMethods = DidService.registrarMethods.toMap()
        DidService.didResolvers.clear()
        DidService.didRegistrars.clear()
        DidService.resolverMethods.clear()
        DidService.registrarMethods.clear()
        initialized.setBoolean(MobileDidSupport, false)
        try {
            block()
        } finally {
            DidService.didResolvers.clear()
            DidService.didResolvers.addAll(resolvers)
            DidService.didRegistrars.clear()
            DidService.didRegistrars.addAll(registrars)
            DidService.resolverMethods.clear()
            DidService.resolverMethods.putAll(resolverMethods)
            DidService.registrarMethods.clear()
            DidService.registrarMethods.putAll(registrarMethods)
            initialized.setBoolean(MobileDidSupport, wasInitialized)
        }
    }

    @Test
    fun `identity initialization persists one matched P256 DID and signs after restart`() = runTest {
        database().use { database ->
            val provider = FakePlatformManagedKeyProvider()
            val config = MobileWalletConfig()
            val original = wallet(config, database, provider)
            val identity = assertIs<SigningIdentityOperationResult.Active>(original.signingIdentity.initialize()).identity
            assertEquals(KeyUseAuthorizationPolicy.BiometricCurrentSet, identity.authorization)
            assertTrue(identity.did.startsWith("did:jwk:"))
            assertStoredDidContainsPublicMaterialOnly(database, identity.did)
            val reopened = wallet(config, database, provider)
            assertEquals(identity, assertIs<SigningIdentityOperationResult.Active>(reopened.signingIdentity.initialize()).identity)
            assertEquals(1, provider.generateCount)
            val key = assertNotNull(SqlDelightKeyStore(provider, database.queries).getCrypto2Key(identity.keyId))
            val algorithm = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256)
            val message = "identity-restart".encodeToByteArray()
            val signature = assertNotNull(key.capabilities.signer).sign(message, algorithm)
            assertTrue(assertNotNull(key.capabilities.verifier).verify(message, signature, algorithm))
            assertDidMatchesPublicKey(identity.did, key)
        }
    }

    @Test
    fun `identity creation uses the configured DID service`() = runTest {
        database().use { database ->
            val didService = RecordingDidService()
            val result = wallet(MobileWalletConfig(), database, FakePlatformManagedKeyProvider(), didService).signingIdentity.initialize()
            assertIs<SigningIdentityOperationResult.Active>(result)
            assertEquals(listOf("jwk"), didService.registeredMethods)
        }
    }

    @Test
    fun `unsupported native authorization never silently selects software`() = runTest {
        database().use { database ->
            val provider = FakePlatformManagedKeyProvider().apply {
                preflightResult = KeyUseAuthorizationSupport.Unsupported(KeyUseAuthorizationUnsupportedReason.BiometricNotEnrolled)
            }
            val result = wallet(MobileWalletConfig(), database, provider).signingIdentity.initialize()
            assertEquals(SigningIdentityFailure.UnsupportedPolicy, assertIs<SigningIdentityOperationResult.Failed>(result).reason)
            assertEquals(0, provider.generateCount)
            assertTrue(database.queries.selectAll().executeAsList().isEmpty())
        }
    }

    @Test
    fun `authorization cancellation cleans owned operation without choosing a weaker key`() = runTest {
        database().use { database ->
            val provider = FakePlatformManagedKeyProvider().apply {
                generateFailure = KeyUseAuthorizationException(KeyUseAuthorizationFailure.AuthorizationNotCompleted, "Test cancellation")
            }
            val result = wallet(MobileWalletConfig(), database, provider).signingIdentity.initialize()
            assertEquals(SigningIdentityFailure.AuthorizationNotCompleted, assertIs<SigningIdentityOperationResult.Failed>(result).reason)
            assertEquals(1, provider.generateCount)
            assertTrue(database.queries.selectAll().executeAsList().isEmpty())
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

    private data class PolicyCase(
        val configured: KeyUseAuthorizationPolicy,
        val requested: KeyUseAuthorizationPolicy?,
        val expected: KeyUseAuthorizationPolicy,
    )

    private class TestDatabase(
        val driver: JdbcSqliteDriver,
        val database: WalletPersistenceDatabase,
    ) : AutoCloseable {
        val queries = database.walletPersistenceQueries

        override fun close() = driver.close()
    }

    private class FakePlatformManagedKeyProvider : PlatformManagedKeyProvider {
        private val softwareProvider = CryptographySoftwareKeyProvider()
        private val keys = mutableMapOf<KeyId, SoftwareKey>()
        private val policies = mutableMapOf<KeyId, KeyUseAuthorizationPolicy>()
        var generateCount = 0
        var deleteCount = 0
        var generateFailure: Throwable? = null
        var preflightResult: KeyUseAuthorizationSupport? = null
        val preflightPolicies = mutableListOf<KeyUseAuthorizationPolicy>()
        val generatedPolicies = mutableListOf<KeyUseAuthorizationPolicy>()

        override suspend fun preflight(requirements: WalletKeyRequirements): KeyUseAuthorizationSupport {
            preflightPolicies += requirements.authorizationPolicy
            return preflightResult ?: if (requirements.protection == WalletKeyProtection.HardwareRequired)
                KeyUseAuthorizationSupport.Unsupported(KeyUseAuthorizationUnsupportedReason.UnsupportedCombination)
            else requirements.authorizationPolicy.toSupportedPreflight()
        }

        override suspend fun generateManagedKey(request: WalletKeyCreationRequest): ManagedKey {
            generateCount++
            generateFailure?.let { throw it }
            generatedPolicies += request.requirements.authorizationPolicy
            val software = softwareProvider.generate(
                GenerateSoftwareKeyRequest(request.id, request.requirements.spec, request.requirements.usages)
            )
            keys[request.id] = software
            policies[request.id] = request.requirements.authorizationPolicy
            val publicKey = assertNotNull(software.capabilities.publicKeyExporter)
                .exportPublicKey().toPublicJwk(request.requirements.spec)
            return managedKey(
                StoredKey.Managed(
                    version = StoredKey.CURRENT_VERSION,
                    id = request.id,
                    spec = request.requirements.spec,
                    usages = request.requirements.usages,
                    provider = PROVIDER_ID,
                    providerSchemaVersion = 1,
                    providerData = BinaryData(request.id.value.encodeToByteArray()),
                    publicKey = publicKey,
                ),
                software,
            )
        }

        override fun keyUseAuthorizationPolicy(stored: StoredKey.Managed): KeyUseAuthorizationPolicy =
            requireNotNull(policies[stored.id]) { "Missing authorization policy for ${stored.id.value}" }

        override suspend fun restoreManagedKey(stored: StoredKey.Managed): PlatformManagedKeyRestoration {
            require(stored.provider == PROVIDER_ID)
            return keys[stored.id]?.let {
                PlatformManagedKeyRestoration.Restored(
                    key = managedKey(stored, it),
                    authorizationPolicy = keyUseAuthorizationPolicy(stored),
                )
            } ?: PlatformManagedKeyRestoration.Missing(keyUseAuthorizationPolicy(stored))
        }

        override suspend fun keyFacts(stored: StoredKey.Managed): PlatformKeyFacts = PlatformKeyFacts(
            origin = id.walt.crypto2.keys.KeyOrigin.GENERATED,
            protection = id.walt.crypto2.keys.KeyProtectionLevel.SOFTWARE,
            securityLevel = id.walt.crypto2.keys.KeySecurityLevel.SOFTWARE,
        )
        override suspend fun deleteUncommittedKey(request: WalletKeyCreationRequest, imported: Boolean) = Unit

        override suspend fun deleteManagedKey(stored: StoredKey.Managed) {
            deleteCount++
            keys.remove(stored.id)
            policies.remove(stored.id)
        }

        private fun managedKey(stored: StoredKey.Managed, software: SoftwareKey): ManagedKey = object : ManagedKey {
            override val storedKey = stored
            override val capabilities: KeyCapabilities = software.capabilities
        }

        private companion object {
            val PROVIDER_ID = ProviderId("mobile-identity-test")
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

private fun KeyUseAuthorizationPolicy.toSupportedPreflight(): KeyUseAuthorizationSupport.Supported = when (this) {
    is KeyUseAuthorizationPolicy.BiometricTimedReuse -> KeyUseAuthorizationSupport.Supported(
        effectivePolicy = this,
        reuseEnforcement = KeyUseAuthorizationReuseEnforcement.ProviderProcess,
        timeoutValidation = KeyUseAuthorizationReuseTimeoutValidation.ProviderConfigurationOnly,
    )
    else -> KeyUseAuthorizationSupport.Supported(this)
}
