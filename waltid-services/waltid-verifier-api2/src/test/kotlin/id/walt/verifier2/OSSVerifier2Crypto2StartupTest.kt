@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.verifier2

import id.walt.commons.config.ConfigManager
import id.walt.crypto.keys.KeySerialization
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import id.walt.crypto2.migration.v1.V1KeyMigration
import id.walt.crypto2.serialization.StoredKeyCodec
import id.walt.verifier2.config.ClientMetadataHopliteDecoder
import id.walt.verifier2.data.CrossDeviceFlowSetup
import id.walt.verifier2.data.GeneralFlowConfig
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class OSSVerifier2Crypto2StartupTest {
    private val tempFiles = mutableListOf<Path>()

    @BeforeEach
    fun resetConfig() {
        clearConfig()
    }

    @AfterEach
    fun cleanup() {
        clearConfig()
        tempFiles.forEach(Files::deleteIfExists)
        tempFiles.clear()
    }

    @Test
    fun `stored key config signs request through startup path`() = runTest {
        val runtime = CryptoRuntime(listOf(CryptographySoftwareKeyProvider()))
        val key = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("configured-verifier"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        )
        loadConfig(StoredKeyCodec.encodeToString(key.storedKey))
        OSSVerifier2Manager.initialize()

        val session = OSSVerifier2Manager.createVerificationSession(
            CrossDeviceFlowSetup(core = GeneralFlowConfig(signedRequest = true))
        )
        val resolved = assertNotNull(OSSVerifier2Manager.resolveCrypto2RequestSigningKey(session))

        CompactJws.verify(assertNotNull(session.signedAuthorizationRequestJwt), resolved, JwsAlgorithm.ES256)
        assertNotNull(session.requestSigningKeyReference)
    }

    @Test
    fun `malformed stored key fails startup initialization`() = runTest {
        loadConfig("""{"version":999}""")

        assertFailsWith<IllegalArgumentException> { OSSVerifier2Manager.initialize() }
    }

    @Test
    fun `legacy JWK migrates in memory without rewriting config`() = runTest {
        val legacyKey = JWKKey.generate(KeyType.secp256r1)
        val (configFile, content) = loadConfig(legacyKey = KeySerialization.serializeKey(legacyKey))

        OSSVerifier2Manager.initialize()
        val session = OSSVerifier2Manager.createVerificationSession(
            CrossDeviceFlowSetup(core = GeneralFlowConfig(signedRequest = true))
        )

        assertNotNull(OSSVerifier2Manager.resolveCrypto2RequestSigningKey(session))
        assertEquals(content, Files.readString(configFile))
    }

    @Test
    fun `mismatched stored sidecar fails startup without downgrade`() = runTest {
        val legacyKey = JWKKey.generate(KeyType.secp256r1)
        val replacement = JWKKey.generate(KeyType.secp256r1)
        val stored = V1KeyMigration().migrate(
            recordId = KeyId(legacyKey.getKeyId()),
            serialized = KeySerialization.serializeKeyToJson(replacement).jsonObject,
            usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
        )
        loadConfig(
            storedKey = StoredKeyCodec.encodeToString(stored),
            legacyKey = KeySerialization.serializeKey(legacyKey),
        )

        assertFailsWith<IllegalArgumentException> { OSSVerifier2Manager.initialize() }
    }

    @Test
    fun `overgranted stored sidecar fails startup validation`() = runTest {
        val legacyKey = JWKKey.generate(KeyType.secp256r1)
        val stored = V1KeyMigration().migrate(
            recordId = KeyId(legacyKey.getKeyId()),
            serialized = KeySerialization.serializeKeyToJson(legacyKey).jsonObject,
            usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY, KeyUsage.ENCRYPT),
        )
        loadConfig(
            storedKey = StoredKeyCodec.encodeToString(stored),
            legacyKey = KeySerialization.serializeKey(legacyKey),
        )

        assertFailsWith<IllegalArgumentException> { OSSVerifier2Manager.initialize() }
    }

    @Test
    fun `legacy config change invalidates cached native key validation`() = runTest {
        val legacyKey = JWKKey.generate(KeyType.secp256r1)
        val stored = V1KeyMigration().migrate(
            recordId = KeyId(legacyKey.getKeyId()),
            serialized = KeySerialization.serializeKeyToJson(legacyKey).jsonObject,
            usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
        )
        val encodedStored = StoredKeyCodec.encodeToString(stored)
        loadConfig(encodedStored, KeySerialization.serializeKey(legacyKey))
        OSSVerifier2Manager.initialize()

        clearConfig()
        loadConfig(encodedStored, KeySerialization.serializeKey(JWKKey.generate(KeyType.secp256r1)))

        assertFailsWith<IllegalArgumentException> { OSSVerifier2Manager.initialize() }
    }

    @Test
    fun `legacy positional constructor order remains available`() {
        val config = OSSVerifier2ServiceConfig(
            "verifier",
            null,
            "https://verifier.example/request",
            "openid4vp://authorize",
            null,
            null,
        )

        assertEquals(null, config.requestSigningStoredKey)
    }

    private fun loadConfig(storedKey: String? = null, legacyKey: String? = null): Pair<Path, String> {
        val configFile = Files.createTempFile("verifier-service", ".conf")
        tempFiles.add(configFile)
        val tripleQuotes = "\"\"\""
        val content = buildString {
            appendLine("clientId = \"verifier2\"")
            appendLine("urlPrefix = \"http://localhost:7003/verification-session\"")
            appendLine("urlHost = \"openid4vp://authorize\"")
            storedKey?.let { appendLine("requestSigningStoredKey = $tripleQuotes$it$tripleQuotes") }
            legacyKey?.let { append("key = $it") }
        }
        Files.writeString(configFile, content)
        System.setProperty("config.file.verifier-service", configFile.toString())
        ConfigManager.registerCustomDecoder(ClientMetadataHopliteDecoder())
        ConfigManager.registerConfig("verifier-service", OSSVerifier2ServiceConfig::class)
        ConfigManager.loadConfigs()
        return configFile to content
    }

    private fun clearConfig() {
        System.clearProperty("config.file.verifier-service")
        ConfigManager.preclear()
    }
}
