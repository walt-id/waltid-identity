package id.walt.issuer2.config

import id.walt.commons.config.ConfigManager
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.*
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.issuer2.application.openid4vci.OpenId4VciModule
import id.walt.openid4vci.proofs.attestation.*
import id.walt.trust.model.TrustedEntityType
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.test.*

class Issuer2KeyAttestationConfigTest {
    @Test
    fun `all documented trust methods load from disk`() = runTest {
        val key = CryptoRuntime(defaultSoftwareKeyProviders()).generateSoftwareKey(GenerateSoftwareKeyRequest(
            KeyId("attester"), KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
        ))
        val configs = listOf(
            KeyAttestationConfig(KeyAttestationVerificationMethod.StaticJwk(key.exportPublicJwkObject())),
            KeyAttestationConfig(KeyAttestationVerificationMethod.X509Chain(listOf("root-pem-parsing-is-validated-at-startup"))),
            KeyAttestationConfig(KeyAttestationVerificationMethod.KeyReference("attester-key")),
            KeyAttestationConfig(
                KeyAttestationVerificationMethod.X509TrustRegistry(setOf(TrustedEntityType.WALLET_PROVIDER), true),
            ),
        )
        for (config in configs) {
            assertEquals(config, load(Json.encodeToJsonElement(KeyAttestationConfig.serializer(), config)).getOrThrow().keyAttestationConfig)
        }
        assertNull(load(null).getOrThrow().keyAttestationConfig)
    }

    @Test
    fun `malformed trust settings fail instead of selecting the legacy constructor`() {
        for (config in listOf(
            """{"verificationMethod":{"type":"unknown"}}""",
            """{"verificationMethod":{"type":"static-jwk"}}""",
            """{"verificationMethod":{"type":"x509-chain","trustedRootCertificatesPem":[]}}""",
            """{"verificationMethod":{"type":"key-reference","reference":""}}""",
            """{"verificationMethod":{"type":"x509-trust-registry","expectedEntityTypes":[]}}""",
        )) {
            assertTrue(load(Json.parseToJsonElement(config)).isFailure, config)
        }
    }

    @Test
    fun `startup fails when x509 trust registry key attestation has no resolver`() {
        val method = KeyAttestationConfig(KeyAttestationVerificationMethod.X509TrustRegistry(setOf(TrustedEntityType.WALLET_PROVIDER)))
        val config = load(Json.encodeToJsonElement(KeyAttestationConfig.serializer(), method)).getOrThrow()

        val error = assertFails { OpenId4VciModule.createAttestationTrustEvaluator(config) }
        assertTrue("attestationTrustResolver" in error.message.orEmpty(), error.message)
        val resolver = TrustDecisionResolverConfig("https://registry.example/trust-registry/resolve/certificate-chain")
        assertNotNull(OpenId4VciModule.createAttestationTrustEvaluator(config.copy(attestationTrustResolver = resolver)))
    }

    private fun load(config: JsonElement?): Result<Issuer2ServiceConfig> {
        val file = Files.createTempFile("issuer2-key-attestation-", ".conf")
        val property = "config.file.issuer2-key-attestation-test"
        val previous = System.getProperty(property)
        try {
            ConfigManager.preclear()
            registerIssuer2ConfigDecoders()
            Files.writeString(file, buildJsonObject {
                put("baseUrl", "https://issuer.example")
                put("ciTokenKey", "unused-by-configuration-decoding")
                put("enforcePushedAuthorizationRequests", false)
                put("clientAuthenticationConfig", buildJsonObject { put("supportedMethods", JsonArray(emptyList())) })
                config?.let { put("keyAttestationConfig", it) }
            }.toString())
            System.setProperty(property, file.toString())
            return ConfigManager.loadConfig(
                ConfigManager.ConfigData("issuer2-key-attestation-test", Issuer2ServiceConfig::class), emptyArray(),
            ).map { it as Issuer2ServiceConfig }
        } finally {
            ConfigManager.preclear()
            if (previous == null) System.clearProperty(property) else System.setProperty(property, previous)
            Files.deleteIfExists(file)
        }
    }
}
