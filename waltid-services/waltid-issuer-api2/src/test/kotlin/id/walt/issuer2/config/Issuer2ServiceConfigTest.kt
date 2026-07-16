package id.walt.issuer2.config

import id.walt.commons.config.ConfigManager
import id.walt.issuer2.testsupport.clearIssuer2TestEnvironment
import id.walt.openid4vci.clientauth.attestation.verifier.ClientAttestationVerificationMethod
import kotlinx.serialization.json.contentOrNull
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
}
