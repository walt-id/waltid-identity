package id.walt.issuer2.config

import id.walt.commons.config.ConfigManager
import id.walt.crypto.keys.KeySerialization
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.migration.v1.V1KeyMigration
import id.walt.crypto2.serialization.StoredKeyCodec
import id.walt.issuer2.application.openid4vci.OpenId4VciModule
import id.walt.issuer2.testsupport.clearIssuer2TestEnvironment
import id.walt.openid4vci.clientauth.attestation.verifier.ClientAttestationVerificationMethod
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertNotNull

class Issuer2ServiceConfigTest {

    private val tempFiles = mutableListOf<Path>()

    @BeforeEach
    fun resetConfig() {
        clearIssuer2TestEnvironment()
    }

    @AfterEach
    fun clearConfig() {
        clearIssuer2TestEnvironment()
        tempFiles.forEach { Files.deleteIfExists(it) }
        tempFiles.clear()
    }

    @Test
    fun `service config does not enable client authentication by default`() {
        val config = loadServiceConfig(
            """
                baseUrl = "http://localhost:7002"
            """.trimIndent(),
        )

        assertNull(config.clientAuthenticationConfig)
        assertNull(config.clientAttestationConfig())
        assertEquals(false, config.supportsPreAuthAnonymous())
        assertEquals("http://localhost:7002/openid4vci", config.openId4VciBaseUrl())
    }

    @Test
    fun `service config decodes credential encryption key`() {
        val config = loadServiceConfig(
            """
                baseUrl = "http://localhost:7002"
                credentialEncryptionKey = ${hoconTripleQuoted(CREDENTIAL_ENCRYPTION_KEY)}
            """.trimIndent(),
        )

        assertEquals(CREDENTIAL_ENCRYPTION_KEY, config.credentialEncryptionKey)
    }

    @Test
    fun `startup loads preferred token StoredKey without rewriting config`() = kotlinx.coroutines.test.runTest {
        val legacyKey = JWKKey.generate(KeyType.secp256r1)
        val storedKey = V1KeyMigration().migrate(
            recordId = KeyId(legacyKey.getKeyId()),
            serialized = KeySerialization.serializeKeyToJson(legacyKey).jsonObject,
            usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
        )
        val content = """
            baseUrl = "http://localhost:7002"
            ciTokenKey = ${hoconTripleQuoted(KeySerialization.serializeKey(legacyKey))}
            ciTokenStoredKey = ${hoconTripleQuoted(StoredKeyCodec.encodeToString(storedKey))}
        """.trimIndent()

        val config = loadServiceConfig(content)
        assertNotNull(OpenId4VciModule.resolveCrypto2TokenKey(config))
        assertEquals(content, Files.readString(tempFiles.last()))
    }

    @Test
    fun `startup migrates legacy token JWK only in memory`() = kotlinx.coroutines.test.runTest {
        val legacyKey = JWKKey.generate(KeyType.secp256r1)
        val content = """
            baseUrl = "http://localhost:7002"
            ciTokenKey = ${hoconTripleQuoted(KeySerialization.serializeKey(legacyKey))}
        """.trimIndent()

        val config = loadServiceConfig(content)
        assertNotNull(OpenId4VciModule.resolveCrypto2TokenKey(config))
        assertNull(config.ciTokenStoredKey)
        assertEquals(content, Files.readString(tempFiles.last()))
    }

    @Test
    fun `legacy positional constructor order remains available`() {
        val config = Issuer2ServiceConfig(
            "https://issuer.example",
            ED25519_KEY,
            null,
            false,
            null,
        )

        assertNull(config.ciTokenStoredKey)
    }

    @Test
    fun `service config rejects unsupported credential encryption key`() {
        assertFails {
            loadServiceConfig(
                """
                    baseUrl = "http://localhost:7002"
                    credentialEncryptionKey = ${hoconTripleQuoted(ED25519_KEY)}
                """.trimIndent(),
            )
        }
    }

    @Test
    fun `service config decodes static jwk client attestation from client authentication config`() {
        val config = loadServiceConfig(
            """
                baseUrl = "http://localhost:7002"
                clientAuthenticationConfig {
                    supportedMethods = [
                        {
                            type = "preauth-anonymous"
                        },
                        {
                            type = "client-attestation"
                            config {
                                verificationMethod {
                                    type = "static-jwk"
                                    jwk {
                                        kty = "EC"
                                        crv = "P-256"
                                        x = "x"
                                        y = "y"
                                    }
                                }
                            }
                        }
                    ]
                }
            """.trimIndent(),
        )

        val method = assertIs<ClientAttestationVerificationMethod.StaticJwk>(
            config.clientAttestationConfig()?.verificationMethod,
        )
        assertEquals("EC", method.jwk["kty"]?.jsonPrimitive?.contentOrNull)
        assertEquals(true, config.supportsPreAuthAnonymous())
    }

    @Test
    fun `service config decodes x509 chain client attestation from client authentication config`() {
        val config = loadServiceConfig(
            """
                baseUrl = "http://localhost:7002"
                clientAuthenticationConfig {
                    supportedMethods = [
                        {
                            type = "client-attestation"
                            config {
                                verificationMethod {
                                    type = "x509-chain"
                                    trustedRootCertificatesPem = [
                                        "-----BEGIN CERTIFICATE-----\nMIIB\n-----END CERTIFICATE-----"
                                    ]
                                }
                            }
                        }
                    ]
                }
            """.trimIndent(),
        )

        val method = assertIs<ClientAttestationVerificationMethod.X509Chain>(
            config.clientAttestationConfig()?.verificationMethod,
        )
        assertEquals(1, method.trustedRootCertificatesPem.size)
        assertEquals(false, config.supportsPreAuthAnonymous())
    }

    @Test
    fun `service config decodes preauth anonymous without client attestation`() {
        val config = loadServiceConfig(
            """
                baseUrl = "http://localhost:7002"
                clientAuthenticationConfig {
                    supportedMethods = [
                        {
                            type = "preauth-anonymous"
                        }
                    ]
                }
            """.trimIndent(),
        )

        assertNull(config.clientAttestationConfig())
        assertEquals(true, config.supportsPreAuthAnonymous())
    }

    private fun loadServiceConfig(content: String): Issuer2ServiceConfig {
        registerIssuer2ConfigDecoders()
        val configFile = Files.createTempFile("issuer-service", ".conf")
        tempFiles.add(configFile)
        Files.writeString(configFile, content)

        System.setProperty("config.file.issuer-service", configFile.toString())
        ConfigManager.registerConfig("issuer-service", Issuer2ServiceConfig::class)
        ConfigManager.loadConfigs()

        return ConfigManager.getConfig()
    }

    private fun hoconTripleQuoted(value: String): String =
        "\"\"\"$value\"\"\""

    private companion object {
        const val CREDENTIAL_ENCRYPTION_KEY =
            """{"type":"jwk","jwk":{"kty":"EC","d":"ZSHgIcRvbwV9s224kHUaFqkEPShCAdwXocGl_w3M42Q","crv":"P-256","kid":"issuer2-credential-encryption-key","x":"GWKpdL3jPoPJ5wKgSA-jxS2jgp-ZUDE6sIQbeB86vF0","y":"F3xAwH96_xVciV7mFQslU_eRQgP-5pSZiNf8bjMoGfo"}}"""

        const val ED25519_KEY =
            """{"type":"jwk","jwk":{"kty":"OKP","crv":"Ed25519","d":"LjxmEnd5oC7hFabwjKQFyeIgMG0OVZ_EBZQ0ZTKBZQs","x":"UDiPRbt76NoaAye5AonMirL7jjTKppMSzAXH0ZwuenU"}}"""
    }
}
