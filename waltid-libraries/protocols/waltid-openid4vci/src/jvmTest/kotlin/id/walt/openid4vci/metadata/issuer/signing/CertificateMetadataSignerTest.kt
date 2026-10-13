package id.walt.openid4vci.metadata.issuer.signing

import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.openid4vci.metadata.issuer.signing.MetadataCertificateFixture
import id.walt.openid4vci.metadata.issuer.CredentialIssuerMetadata
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import id.walt.certificate.x509.extension.KeyUsageExtension.KeyUsage
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CertificateMetadataSignerTest {
    private val metadata = CredentialIssuerMetadata(
        credentialIssuer = "https://issuer.example/openid4vci",
        credentialEndpoint = "https://issuer.example/openid4vci/credential",
        credentialConfigurationsSupported = emptyMap(),
    )

    @Test
    fun `leaf first chain is standard base64 and signature verifies independently`() = runTest {
        val fixture = MetadataCertificateFixture()
        for (chain in listOf(listOf(fixture.leaf), fixture.chain)) {
            val signer = CertificateMetadataSigner.load(fixture.config(chain))
            val jwt = signer.sign(metadata)
            val header = jwt.decodeJws().header
            assertNull(header["jwk"])
            assertEquals(chain.map { Base64.getEncoder().encodeToString(it.encoded) }, header["x5c"]!!.jsonArray.map { it.jsonPrimitive.content })
            assertTrue(MetadataCertificateFixture.verifies(jwt, fixture.leaf))
            assertFalse(MetadataCertificateFixture.verifies(jwt, fixture.intermediate))
        }
    }

    @Test
    fun `inline PEM sources sign without exposing the private key`() = runTest {
        val fixture = MetadataCertificateFixture()
        val original = fixture.config()
        val pem = original.privateKeyPem
        val configs = listOf(
            original,
            original.copy(privateKeyPem = "\n" + pem.prependIndent("    ") + "\n"),
        )
        for (config in configs) {
            val jwt = CertificateMetadataSigner.load(config).sign(metadata)
            assertTrue(MetadataCertificateFixture.verifies(jwt, fixture.leaf))
            assertFalse(config.toString().contains(pem.lineSequence().drop(1).first()))
        }
        val invalid = configs.first().copy(privateKeyPem = "secret-inline-key")
        val error = assertFailsWith<IllegalArgumentException> { CertificateMetadataSigner.load(invalid) }
        assertFalse(error.stackTraceToString().contains("secret-inline-key"))
        val otherKey = MetadataCertificateFixture.pem("PRIVATE KEY", MetadataCertificateFixture.generateKey().private.encoded)
        assertFailsWith<IllegalArgumentException> { CertificateMetadataSigner.load(configs.first().copy(privateKeyPem = otherKey)) }
    }

    @Test
    fun `certificate expiry is enforced after startup`() = runTest {
        val fixture = MetadataCertificateFixture()
        var now = fixture.now
        val clock = object : Clock() {
            override fun getZone(): ZoneId = ZoneOffset.UTC
            override fun withZone(zone: ZoneId): Clock = this
            override fun instant(): Instant = now
        }
        val config = fixture.config()
        val signer = CertificateMetadataSigner.load(config) { kotlin.time.Instant.fromEpochMilliseconds(clock.instant().toEpochMilli()) }
        assertTrue(MetadataCertificateFixture.verifies(signer.sign(metadata), fixture.leaf))
        now = fixture.notAfter.plusSeconds(1)
        assertFailsWith<IllegalArgumentException> { signer.sign(metadata) }
    }

    @Test
    fun `invalid keys and chains fail with bounded diagnostics`() = runTest {
        val fixture = MetadataCertificateFixture()
        val cases = listOf(
            fixture.config(privateKey = MetadataCertificateFixture.generateKey()) to "does not match",
            fixture.config(fixture.chain.reversed()) to "unordered",
            fixture.config(listOf(fixture.leaf, MetadataCertificateFixture().intermediate)) to "signatures",
            fixture.config(listOf(fixture.leaf, fixture.intermediate, fixture.intermediate)) to "unordered",
            MetadataCertificateFixture(notAfter = Instant.now().minusSeconds(10)).config() to "expired",
            MetadataCertificateFixture(notBefore = Instant.now().plusSeconds(10)).config() to "not-yet-valid",
            MetadataCertificateFixture(leafUsage = KeyUsage.keyAgreement).config() to "digital signatures",
            MetadataCertificateFixture(intermediateIsCa = false).config() to "CA constraints",
            MetadataCertificateFixture(intermediateUsage = KeyUsage.digitalSignature).config() to "CA constraints",
        )
        for ((config, diagnostic) in cases) {
            val error = assertFailsWith<IllegalArgumentException> { CertificateMetadataSigner.load(config) }
            assertTrue(error.message!!.contains(diagnostic), error.message)
            assertNull(error.cause)
            assertFalse(error.message!!.contains("PRIVATE KEY"))
        }
    }

    @Test
    fun `malformed encrypted and empty material cannot be loaded`() = runTest {
        val config = MetadataCertificateFixture().config()
        for (contents in listOf("secret-malformed-input", "-----BEGIN ENCRYPTED PRIVATE KEY-----\nsecret\n-----END ENCRYPTED PRIVATE KEY-----")) {
            val error = assertFailsWith<IllegalArgumentException> {
                CertificateMetadataSigner.load(config.copy(privateKeyPem = contents))
            }
            assertTrue(error.message!!.contains("privateKeyPem"))
            assertFalse(error.message!!.contains("secret"))
            assertNull(error.cause)
        }
        for (chain in listOf(
            listOf("garbage"),
            listOf(MetadataCertificateFixture.pem("CERTIFICATE", byteArrayOf(1, 2, 3))),
            listOf(config.certificateChainPem.first() + "garbage"),
            listOf(config.certificateChainPem.joinToString("")),
        )) {
            assertFailsWith<IllegalArgumentException> { CertificateMetadataSigner.load(config.copy(certificateChainPem = chain)) }
        }
        assertFailsWith<IllegalArgumentException> { config.copy(privateKeyPem = " ") }
        assertFailsWith<IllegalArgumentException> { config.copy(certificateChainPem = emptyList()) }
        assertFailsWith<IllegalArgumentException> { config.copy(certificateChainPem = listOf(" ")) }
    }

    @Test
    fun `certificate signing algorithm follows the subject key rather than the CA signature`() = runTest {
        val fixture = MetadataCertificateFixture(curve = "secp384r1")
        val config = fixture.config()
        val jwt = CertificateMetadataSigner.load(config).sign(metadata)
        assertEquals("ES384", jwt.decodeJws().header["alg"]?.jsonPrimitive?.content)
        assertEquals("SHA256WITHECDSA", fixture.leaf.sigAlgName.uppercase())
        assertTrue(MetadataCertificateFixture.verifies(jwt, fixture.leaf, "SHA384withECDSAinP1363Format"))
    }

    @Test
    fun `RSA and Edwards certificates select algorithms from their public key`() = runTest {
        val rsa = java.security.KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val edwards = java.security.KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
        for ((key, expected, verifierAlgorithm) in listOf(
            Triple(rsa, "RS256", "SHA256withRSA"), Triple(edwards, "Ed25519", "Ed25519"),
        )) {
            val fixture = MetadataCertificateFixture(providedKeyPair = key)
            val jwt = CertificateMetadataSigner.load(fixture.config()).sign(metadata)
            assertEquals(expected, jwt.decodeJws().header["alg"]?.jsonPrimitive?.content)
            assertTrue(MetadataCertificateFixture.verifies(jwt, fixture.leaf, verifierAlgorithm))
        }
    }

    @Test
    fun `absent key usage extension permits signing`() = runTest {
        val fixture = MetadataCertificateFixture(leafUsage = null)
        assertTrue(MetadataCertificateFixture.verifies(CertificateMetadataSigner.load(fixture.config()).sign(metadata), fixture.leaf))
    }
}
