package id.walt.openid4vci.metadata.issuer.signing

import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.crypto2.keys.Key
import id.walt.openid4vci.metadata.issuer.signing.MetadataSigningMethod
import id.walt.openid4vci.metadata.issuer.signing.SignedMetadataConfig
import id.walt.openid4vci.metadata.issuer.signing.MetadataJwtSigner
import id.walt.openid4vci.metadata.issuer.signing.ResolvedMetadataSigningKey
import id.walt.openid4vci.metadata.issuer.signing.MetadataSigningKeyReferenceResolver
import id.walt.openid4vci.metadata.issuer.signing.MetadataCertificateFixture
import id.walt.openid4vci.metadata.issuer.CredentialIssuerMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MetadataSigningKeyReferenceTest {
    private val metadata = CredentialIssuerMetadata(
        credentialIssuer = "https://issuer.example/openid4vci",
        credentialEndpoint = "https://issuer.example/openid4vci/credential",
        credentialConfigurationsSupported = emptyMap(),
    )

    @Test
    fun `referenced keys never need private export and resolve only at initialization`() = runTest {
        val fixture = MetadataCertificateFixture()
        val original = fixture.crypto2SigningKey()
        for (withCertificates in listOf(false, true)) {
            // Models a remote signer; in certificate mode even public-key export is unnecessary.
            val key = object : Key by original {
                override val capabilities = original.capabilities.copy(
                    privateKeyExporter = null,
                    verifier = null,
                    publicKeyExporter = if (withCertificates) null else original.capabilities.publicKeyExporter,
                )
            }
            var calls = 0
            val method = MetadataSigningMethod.KeyReference("metadata-key")
            val signer = assertNotNull(MetadataJwtSigner.dedicatedSigner(config(method), MetadataSigningKeyReferenceResolver {
                assertEquals("metadata-key", it)
                calls++
                ResolvedMetadataSigningKey(key, if (withCertificates) fixture.config().certificateChainPem else null)
            }))
            repeat(2) {
                val jwt = signer.sign(metadata)
                assertTrue(MetadataCertificateFixture.verifies(jwt, fixture.leaf))
                val header = jwt.decodeJws().header
                assertEquals("ES256", header["alg"]?.jsonPrimitive?.content)
                assertFalse(header["kid"]?.jsonPrimitive?.content == original.id.value)
                if (withCertificates) {
                    assertNotNull(header["x5c"])
                    assertNull(header["jwk"])
                } else {
                    assertNotNull(header["jwk"])
                    assertNull(header["x5c"])
                }
            }
            assertEquals(1, calls)
        }
    }

    @Test
    fun `missing unresolved and failing resolvers fail without exposing reference or provider secrets`() = runTest {
        val config = config(MetadataSigningMethod.KeyReference("private-reference"))
        assertFailsWith<IllegalArgumentException> { MetadataJwtSigner.dedicatedSigner(config) }
        assertFailsWith<IllegalArgumentException> { MetadataJwtSigner.dedicatedSigner(config) { null } }
        val error = assertFailsWith<IllegalArgumentException> {
            MetadataJwtSigner.dedicatedSigner(config) { error("provider-secret") }
        }
        assertFalse(error.stackTraceToString().contains("provider-secret"))
        assertFalse(error.stackTraceToString().contains("private-reference"))
        assertFailsWith<CancellationException> {
            MetadataJwtSigner.dedicatedSigner(config) { throw CancellationException("cancelled") }
        }
    }

    @Test
    fun `referenced verification-only keys and invalid or mismatched chains fail startup`() = runTest {
        val fixture = MetadataCertificateFixture()
        val key = fixture.crypto2SigningKey()
        val publicOnly = object : Key by key {
            override val capabilities = key.capabilities.copy(signer = null, digestSigner = null)
        }
        assertFailsWith<IllegalArgumentException> {
            MetadataJwtSigner.dedicatedSigner(config(MetadataSigningMethod.KeyReference("key"))) { ResolvedMetadataSigningKey(publicOnly) }
        }
        val other = MetadataCertificateFixture()
        for (chain in listOf(emptyList(), listOf("garbage"), other.config().certificateChainPem)) {
            assertFailsWith<IllegalArgumentException> {
                MetadataJwtSigner.dedicatedSigner(config(MetadataSigningMethod.KeyReference("key"))) {
                    ResolvedMetadataSigningKey(key, chain)
                }
            }
        }
    }

    private fun config(method: MetadataSigningMethod) = SignedMetadataConfig(method)
}
