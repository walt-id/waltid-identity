package id.walt.wallet2

import id.walt.commons.config.ConfigManager
import id.walt.crypto.keys.DirectSerializedKey
import id.walt.crypto.keys.KeySerialization
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.migration.v1.V1KeyMigration
import id.walt.crypto2.serialization.StoredKeyCodec
import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.sessions.AuthSession
import id.walt.ktorauthnz.tokens.jwttoken.JwtTokenHandler
import id.walt.wallet2.auth.configureWallet2Auth
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

    private suspend fun authConfig(legacyKey: JWKKey, storedKey: String? = null): String {
        val tripleQuotes = "\"\"\""
        return buildString {
            appendLine("signingKey = ${KeySerialization.serializeKey(legacyKey)}")
            storedKey?.let { appendLine("signingStoredKey = $tripleQuotes$it$tripleQuotes") }
            append("tokenExpiry = \"PT1H\"")
        }
    }

    private companion object {
        val usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY)
    }
}
