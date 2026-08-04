package id.walt.wallet2

import id.walt.commons.config.ConfigManager
import id.walt.crypto.keys.DirectSerializedKey
import id.walt.crypto.keys.KeySerialization
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.ManagedKey
import id.walt.crypto2.keys.ProviderId
import id.walt.crypto2.keys.StoredKey
import id.walt.crypto2.providers.GenerateManagedKeyRequest
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.ManagedKeyProvider
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.crypto2.serialization.BinaryData
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.migration.v1.V1KeyMigration
import id.walt.crypto2.serialization.StoredKeyCodec
import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.sessions.AuthSession
import id.walt.ktorauthnz.tokens.jwttoken.JwtTokenHandler
import id.walt.wallet2.auth.configureWallet2Auth
import id.walt.wallet2.auth.resolveWallet2AuthSigningKey
import id.walt.wallet2.config.registerWallet2ConfigDecoders
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.hours
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class Wallet2AuthStoredKeyStartupTest {
    private var configFile: Path? = null

    @BeforeEach
    fun reset() {
        cleanup()
    }

    @AfterEach
    fun cleanup() {
        System.clearProperty("config.file.auth")
        ConfigManager.preclear()
        configFile?.let(Files::deleteIfExists)
    }

    @Test
    fun `stored auth key is wired through startup and survives restart without rewriting config`() = runTest {
        val legacyKey = JWKKey.generate(KeyType.Ed25519)
        val stored = V1KeyMigration().migrate(
            recordId = KeyId(legacyKey.getKeyId()),
            serialized = KeySerialization.serializeKeyToJson(legacyKey).jsonObject,
            usages = usages,
        )
        val content = authConfig(legacyKey, StoredKeyCodec.encodeToString(stored))
        loadConfig(content)

        configureAuth()
        val firstHandler = assertIs<JwtTokenHandler>(KtorAuthnzManager.tokenHandler)
        val token = firstHandler.generateToken(AuthSession(id = "session", accountId = "account"))

        configureAuth()
        val restartedHandler = assertIs<JwtTokenHandler>(KtorAuthnzManager.tokenHandler)
        assertTrue(restartedHandler.validateToken(token))
        assertEquals(content, Files.readString(requireNotNull(configFile)))
    }

    @Test
    fun `malformed stored auth key fails startup without legacy downgrade`() = runTest {
        loadConfig(authConfig(JWKKey.generate(KeyType.secp256r1), "not-a-stored-key"))

        assertFailsWith<IllegalArgumentException> { configureAuth() }
    }

    @Test
    fun `legacy auth JWK migrates only in memory`() = runTest {
        val content = authConfig(JWKKey.generate(KeyType.secp256r1))
        loadConfig(content)

        configureAuth()

        assertIs<JwtTokenHandler>(KtorAuthnzManager.tokenHandler)
        assertEquals(content, Files.readString(requireNotNull(configFile)))
    }

    @Test
    fun `mismatched stored auth key fails startup validation`() = runTest {
        val legacyKey = JWKKey.generate(KeyType.secp256r1)
        val replacement = JWKKey.generate(KeyType.secp256r1)
        val stored = V1KeyMigration().migrate(
            recordId = KeyId(legacyKey.getKeyId()),
            serialized = KeySerialization.serializeKeyToJson(replacement).jsonObject,
            usages = usages,
        )
        loadConfig(authConfig(legacyKey, StoredKeyCodec.encodeToString(stored)))

        assertFailsWith<IllegalArgumentException> { configureAuth() }
    }

    @Test
    fun `overgranted stored auth key fails startup validation`() = runTest {
        val legacyKey = JWKKey.generate(KeyType.secp256r1)
        val stored = V1KeyMigration().migrate(
            recordId = KeyId(legacyKey.getKeyId()),
            serialized = KeySerialization.serializeKeyToJson(legacyKey).jsonObject,
            usages = usages + KeyUsage.ENCRYPT,
        )
        loadConfig(authConfig(legacyKey, StoredKeyCodec.encodeToString(stored)))

        assertFailsWith<IllegalArgumentException> { configureAuth() }
    }

    @Test
    fun `standalone stored auth key configures auth without a legacy key`() = runTest {
        val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
        val key = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("wallet-auth-key"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = usages,
            )
        )
        val content = authConfig(storedKey = StoredKeyCodec.encodeToString(key.storedKey))
        loadConfig(content)

        val config = ConfigManager.getConfig<OSSWallet2AuthConfig>()
        assertEquals(null, config.signingKey)

        // The algorithm comes from the StoredKey when no legacy key pins it.
        val resolved = resolveWallet2AuthSigningKey(config, runtime)
        assertEquals(JwsAlgorithm.ES256, resolved.algorithm)
        assertEquals(KeyId("wallet-auth-key"), resolved.key.id)

        configureAuth()
        val handler = assertIs<JwtTokenHandler>(KtorAuthnzManager.tokenHandler)
        assertTrue(handler.validateToken(handler.generateToken(AuthSession(id = "session", accountId = "account"))))
        assertEquals(content, Files.readString(requireNotNull(configFile)))
    }

    @Test
    fun `managed stored auth key is resolved through the configured runtime`() = runTest {
        // A managed descriptor cannot be expressed by the legacy key at all, and cannot be restored
        // by the default software-only runtime - it needs the provider that owns it.
        val software = CryptoRuntime(defaultSoftwareKeyProviders()).generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("managed-auth-key"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = usages,
            )
        )
        val config = OSSWallet2AuthConfig(
            signingStoredKey = StoredKeyCodec.encodeToString(
                FakeManagedKeyProvider.managedDescriptor(software.storedKey)
            ),
        )

        assertFailsWith<IllegalArgumentException> {
            resolveWallet2AuthSigningKey(config, CryptoRuntime(defaultSoftwareKeyProviders()))
        }

        val resolved = resolveWallet2AuthSigningKey(
            config,
            CryptoRuntime(defaultSoftwareKeyProviders(), listOf(FakeManagedKeyProvider())),
        )
        assertEquals(KeyId("managed-auth-key"), resolved.key.id)
        assertEquals(JwsAlgorithm.ES256, resolved.algorithm)
    }

    @Test
    fun `auth config without any key is rejected`() {
        assertFailsWith<IllegalArgumentException> { OSSWallet2AuthConfig() }
    }

    @Test
    fun `legacy positional constructor order remains available`() = runTest {
        val config = OSSWallet2AuthConfig(
            DirectSerializedKey(JWKKey.generate(KeyType.secp256r1)),
            1.hours,
        )

        assertEquals(null, config.signingStoredKey)
    }

    private fun loadConfig(content: String) {
        configFile = Files.createTempFile("wallet-auth", ".conf")
        Files.writeString(configFile, content)
        System.setProperty("config.file.auth", configFile.toString())
        registerWallet2ConfigDecoders()
        ConfigManager.registerConfig("auth", OSSWallet2AuthConfig::class)
        ConfigManager.loadConfigs()
    }

    private fun configureAuth() {
        testApplication {
            application {
                runBlocking { configureWallet2Auth() }
            }
            startApplication()
        }
    }

    private fun authConfig(legacyKey: JWKKey? = null, storedKey: String? = null): String {
        val tripleQuotes = "\"\"\""
        return buildString {
            legacyKey?.let { appendLine("signingKey = ${KeySerialization.serializeKey(it)}") }
            storedKey?.let { appendLine("signingStoredKey = $tripleQuotes$it$tripleQuotes") }
            append("tokenExpiry = \"PT1H\"")
        }
    }

    /**
     * Minimal managed provider for tests. It carries the software key material in `providerData`, so
     * a fresh provider instance can restore it, while the descriptor stays a [StoredKey.Managed] that
     * only this provider can resolve - which is what a real KMS/HSM descriptor looks like from the
     * configuration side.
     */
    private class FakeManagedKeyProvider : ManagedKeyProvider {
        override val id: ProviderId = PROVIDER_ID
        private val softwareRuntime = CryptoRuntime(defaultSoftwareKeyProviders())

        override suspend fun generate(request: GenerateManagedKeyRequest): ManagedKey =
            throw UnsupportedOperationException("Test provider restores pre-provisioned keys only")

        override suspend fun restore(stored: StoredKey.Managed): ManagedKey {
            val software = softwareRuntime.restore(
                StoredKeyCodec.decodeFromByteArray(stored.providerData.toByteArray())
            )
            return object : ManagedKey {
                override val storedKey = stored
                // Managed keys never export private material - see CryptoRuntime capability validation.
                override val capabilities = software.capabilities.copy(privateKeyExporter = null)
            }
        }

        companion object {
            val PROVIDER_ID = ProviderId("fake-managed")

            fun managedDescriptor(software: StoredKey.Software) = StoredKey.Managed(
                version = StoredKey.CURRENT_VERSION,
                id = software.id,
                spec = software.spec,
                usages = software.usages,
                provider = PROVIDER_ID,
                providerSchemaVersion = 1,
                providerData = BinaryData(StoredKeyCodec.encodeToByteArray(software)),
            )
        }
    }

    private companion object {
        val usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY)
    }
}
