package id.walt.openid4vp.conformance

import com.nimbusds.jose.jwk.ECKey
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigRenderOptions
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IssuerCiConfigurationTest {
    private fun fixture(name: String): Config = ConfigFactory.parseURL(
        requireNotNull(javaClass.getResource("/issuer2/$name"))
    ).resolve()

    private val configurationIds = setOf(
        "identity_credential", "org.iso.18013.5.1.mDL",
        "identity_credential_haip", "org.iso.18013.5.1.mDL.haip",
    )

    @Test
    fun ciEnablesBatchAndUsesTheRunnerAttesterTrust() {
        val service = fixture("issuer-service-ci.conf.template")
        assertEquals(10, service.getInt("batchCredentialIssuance.batchSize"))
        assertEquals("__ISSUER_BASE_URL__", service.getString("baseUrl"))
        val attestation = service.getConfigList("clientAuthenticationConfig.supportedMethods")
            .single { it.getString("type") == "client-attestation" }
        val root = requireNotNull(javaClass.getResource("/certs/root-ca.pem")).readText().trim()
        assertEquals(listOf(root), attestation.getStringList("config.verificationMethod.trustedRootCertificatesPem").map(String::trim))
    }

    @Test
    fun profilesExactlyMatchTheFourAdvertisedConfigurations() {
        val metadata = fixture("credential-issuer-metadata-ci.conf").getObject("credentialConfigurations")
        assertEquals(configurationIds, metadata.keys)
        val profiles = fixture("issuer2-profiles-ci.conf").getConfig("profiles")
        val configurations = profiles.root().keys.map { profiles.getConfig(it) }
        assertEquals(configurationIds, configurations.map { it.getString("credentialConfigurationId") }.toSet())
        assertEquals(4, configurations.size)
        configurations.forEach { profile ->
            assertFalse(profile.hasPath("credentialStatus"), "Do not use preconfigured-status batch fixtures")
            assertFalse(profile.getConfig("credentialData").hasPath("id"), "Do not introduce a stable SD-JWT dataset ID")
        }
        metadata.values.forEach { value ->
            val config = (value as com.typesafe.config.ConfigObject).toConfig()
            assertTrue("ES256" in config.getStringList("proof_types_supported.jwt.proof_signing_alg_values_supported"))
        }
    }

    @Test
    fun sdJwtProfilesDoNotAddDatasetIdsThroughDataOrMapping() {
        val profiles = fixture("issuer2-profiles-ci.conf").getConfig("profiles")
        listOf("identityCredentialSdJwt", "identityCredentialHaipSdJwt").forEach { name ->
            val profile = profiles.getConfig(name)
            assertFalse(profile.getConfig("credentialData").hasPath("id"), "$name must not include a dataset ID")
            // Mapping runs for each credential; a generated UUID makes batch datasets differ.
            assertFalse(profile.getConfig("mapping").hasPath("id"), "$name must not map a dataset ID")
        }
    }

    @Test
    fun signingCertificatesMatchKeysAndTheRunnerTrustAnchor() {
        val profiles = fixture("issuer2-profiles-ci.conf").getConfig("profiles")
        val factory = CertificateFactory.getInstance("X.509")
        val root = requireNotNull(javaClass.getResource("/certs/issuer2-haip-root-ca.pem"))
            .openStream().use { factory.generateCertificate(it) as X509Certificate }
        profiles.root().keys.forEach { name ->
            val profile = profiles.getConfig(name)
            if (profile.getString("credentialConfigurationId") == "identity_credential") {
                // Basic SD-JWT uses its DID; HAIP and mdoc use the certificate chains.
                assertTrue(profile.getString("issuerDid").startsWith("did:jwk:"))
                return@forEach
            }
            val key = ECKey.parse(profile.getConfig("issuerKey.jwk").root().render(ConfigRenderOptions.concise()))
            val leaf = factory.generateCertificate(profile.getStringList("x5Chain").single().byteInputStream()) as X509Certificate
            assertContentEquals(key.toECPublicKey().encoded, leaf.publicKey.encoded, name)
            leaf.verify(root.publicKey)
            leaf.checkValidity()
        }
    }
}
