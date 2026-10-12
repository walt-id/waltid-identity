package id.walt.openid4vci.metadata.oauth

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.Key
import id.walt.openid4vci.metadata.issuer.signing.CertificateMetadataSigner
import id.walt.openid4vci.metadata.issuer.signing.MetadataCertificateFixture
import id.walt.openid4vci.metadata.issuer.signing.MetadataJwtSigner
import id.walt.openid4vci.metadata.issuer.signing.MetadataSigningCertificateReferenceResolver
import id.walt.openid4vci.metadata.issuer.signing.MetadataSigningKeyReferenceResolver
import id.walt.openid4vci.metadata.issuer.signing.MetadataSigningMethod
import id.walt.openid4vci.metadata.issuer.signing.ResolvedMetadataSigningKey
import id.walt.openid4vci.metadata.issuer.signing.SignedMetadataConfig
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.jupiter.api.Test
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class AuthorizationServerMetadataJwtTest {
    private val metadata = AuthorizationServerMetadata.fromBaseUrl("https://as.example/tenant/openid4vci").copy(
        customParameters = mapOf("extension" to JsonPrimitive("preserved")),
        signedMetadata = "previous.jwt.value",
    )

    @Test
    fun `inline asymmetric keys sign all metadata claims without nesting signed metadata`() = runTest {
        for (type in listOf(KeyType.secp256r1, KeyType.RSA, KeyType.Ed25519)) {
            val key = JWKKey.generate(type)
            val signer = assertNotNull(MetadataJwtSigner.dedicatedSigner(
                SignedMetadataConfig(MetadataSigningMethod.StaticJwk(key.exportJWKObject())),
            ))
            val jwt = signer.sign(metadata)
            assertTrue(key.getPublicKey().verifyJws(jwt).isSuccess)
            assertClaims(jwt)
            val header = jwt.decodeJws().header
            assertNotNull(header["jwk"])
            assertNull(header["x5c"])
            assertNull(header.getValue("jwk").jsonObject["d"])
        }
    }

    @Test
    fun `inline certificates and referenced signing capabilities use the same certificate chain`() = runTest {
        val fixture = MetadataCertificateFixture()
        val original = fixture.crypto2SigningKey()
        val remote = object : Key by original {
            override val capabilities = original.capabilities.copy(
                privateKeyExporter = null, publicKeyExporter = null, verifier = null,
            )
        }
        val chain = fixture.config().certificateChainPem
        val references = chain.indices.map { "certificates.cert-$it" }
        val signers = listOf(
            assertNotNull(MetadataJwtSigner.dedicatedSigner(SignedMetadataConfig(fixture.config()))),
            assertNotNull(MetadataJwtSigner.dedicatedSigner(
                SignedMetadataConfig(MetadataSigningMethod.KeyReference("kms.key", references)),
                MetadataSigningKeyReferenceResolver { ResolvedMetadataSigningKey(remote) },
                MetadataSigningCertificateReferenceResolver { chain[references.indexOf(it)] },
            )),
        )
        for (signer in signers) {
            val jwt = signer.sign(metadata)
            assertTrue(MetadataCertificateFixture.verifies(jwt, fixture.leaf))
            assertClaims(jwt)
            val header = jwt.decodeJws().header
            assertNull(header["jwk"])
            assertEquals(fixture.chain.map { Base64.getEncoder().encodeToString(it.encoded) },
                header.getValue("x5c").jsonArray.map { it.jsonPrimitive.content })
        }
    }

    @Test
    fun `referenced key without certificates publishes its public jwk`() = runTest {
        val fixture = MetadataCertificateFixture()
        val signer = assertNotNull(MetadataJwtSigner.dedicatedSigner(
            SignedMetadataConfig(MetadataSigningMethod.KeyReference("kms.key")),
            MetadataSigningKeyReferenceResolver { ResolvedMetadataSigningKey(fixture.crypto2SigningKey()) },
        ))
        val jwt = signer.sign(metadata)
        assertTrue(MetadataCertificateFixture.verifies(jwt, fixture.leaf))
        assertNotNull(jwt.decodeJws().header["jwk"])
        assertNull(jwt.decodeJws().header["x5c"])
        assertClaims(jwt)
    }

    @Test
    fun `expired certificates cannot sign OAuth metadata after initialization`() = runTest {
        val fixture = MetadataCertificateFixture()
        var now = Instant.fromEpochMilliseconds(fixture.now.toEpochMilli())
        val signer = CertificateMetadataSigner.load(fixture.config()) { now }
        assertTrue(MetadataCertificateFixture.verifies(signer.sign(metadata), fixture.leaf))
        now = Instant.fromEpochMilliseconds(fixture.notAfter.plusSeconds(1).toEpochMilli())
        assertFailsWith<IllegalArgumentException> { signer.sign(metadata) }
    }

    @Test
    fun `custom metadata cannot supply registered JWT claims`() = runTest {
        val key = MetadataCertificateFixture().crypto2SigningKey()
        for (claim in listOf("iss", "sub", "iat", "exp", "nbf", "aud", "jti")) {
            assertFailsWith<IllegalArgumentException> {
                metadata.copy(customParameters = mapOf(claim to JsonPrimitive("override")))
                    .toSignedJwt(key, JwsAlgorithm.parse("ES256"))
            }
        }
    }

    private fun assertClaims(jwt: String) {
        assertEquals("JWT", jwt.decodeJws().header.getValue("typ").jsonPrimitive.content)
        val payload = Json.parseToJsonElement(String(Base64.getUrlDecoder().decode(jwt.split('.')[1]))).jsonObject
        assertEquals(metadata.issuer, payload.getValue("iss").jsonPrimitive.content)
        assertEquals(payload["issuer"], payload["iss"])
        assertFalse("signed_metadata" in payload)
        assertEquals(Json.encodeToJsonElement(AuthorizationServerMetadata.serializer(), metadata.copy(signedMetadata = null)),
            JsonObject(payload - setOf("iss", "iat")))
        assertTrue(kotlin.math.abs(Clock.System.now().epochSeconds - payload.getValue("iat").jsonPrimitive.long) < 60)
    }
}
