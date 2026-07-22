package id.walt.crypto2.jose

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EdwardsCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.KeyCapabilities
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.Signer
import id.walt.crypto2.keys.SoftwareKey
import id.walt.crypto2.keys.Verifier
import id.walt.crypto2.keys.toStoredSoftwareKey
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class CompactJwsTest {
    private val runtime = CryptoRuntime(listOf(CryptographySoftwareKeyProvider()))

    @Test
    fun `arbitrary payload and protected headers round trip`() = runTest {
        val key = generate(KeySpec.Ec(EcCurve.P256), SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256))
        val payload = byteArrayOf(0, 1, 2, 3)
        val header = JsonObject(
            mapOf(
                "kid" to JsonPrimitive("key-1"),
                "nonce" to JsonPrimitive("nonce-value"),
                "x5c" to JsonArray(listOf(JsonPrimitive("certificate"))),
            ),
        )

        val signed = CompactJws.sign(payload, key, JwsAlgorithm.ES256, header)
        val verified = CompactJws.verify(signed, key, JwsAlgorithm.ES256)

        assertContentEquals(payload, verified.payload)
        assertEquals("key-1", verified.protectedHeader["kid"]?.let { (it as JsonPrimitive).content })
        assertEquals("nonce-value", verified.protectedHeader["nonce"]?.let { (it as JsonPrimitive).content })
    }

    @Test
    fun `fully specified Ed25519 and legacy EdDSA and RSA-PSS compact JWS round trip`() = runTest {
        val ed = generate(KeySpec.Edwards(EdwardsCurve.ED25519), SignatureAlgorithm.EdDsa)
        val pss = generate(KeySpec.Rsa(2048), JwsAlgorithm.PS256.toSignatureAlgorithm())

        listOf(
            ed to JwsAlgorithm.ED25519,
            ed to JwsAlgorithm.EDDSA,
            pss to JwsAlgorithm.PS256,
        ).forEach { (key, algorithm) ->
            val signed = CompactJws.sign("payload".encodeToByteArray(), key, algorithm)
            assertEquals(algorithm, CompactJws.decodeUnverified(signed).algorithm)
            assertEquals(
                "payload",
                CompactJws.verify(signed, key, algorithm).payload.decodeToString(),
            )
        }
        assertEquals(JwsAlgorithm.ED25519, ed.spec.defaultJwsAlgorithm())
        assertFails { CompactJws.sign(byteArrayOf(), ed, JwsAlgorithm.ED448) }
    }

    @Test
    fun `tampering and algorithm confusion are rejected`() = runTest {
        val key = generate(KeySpec.Ec(EcCurve.P256), SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256))
        val signed = CompactJws.sign("payload".encodeToByteArray(), key, JwsAlgorithm.ES256)
        val parts = signed.split('.').toMutableList()
        parts[2] = (if (parts[2].first() == 'A') "B" else "A") + parts[2].drop(1)

        assertFailsWith<InvalidJwsSignatureException> {
            CompactJws.verify(parts.joinToString("."), key, setOf(JwsAlgorithm.ES256))
        }
        assertFails {
            CompactJws.verify(signed, key, JwsAlgorithm.ES384)
        }
    }

    @Test
    fun `unsupported protected-header semantics are rejected`() = runTest {
        val key = generate(KeySpec.Ec(EcCurve.P256), SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256))

        assertFails {
            CompactJws.sign(
                byteArrayOf(),
                key,
                JwsAlgorithm.ES256,
                JsonObject(mapOf("b64" to JsonPrimitive(false))),
            )
        }
        assertFails {
            CompactJws.sign(
                byteArrayOf(),
                key,
                JwsAlgorithm.ES256,
                JsonObject(mapOf("crit" to JsonArray(listOf(JsonPrimitive("exp"))))),
            )
        }
    }

    @Test
    fun `curve confusion and padded compact encoding are rejected`() = runTest {
        val p256 = generate(KeySpec.Ec(EcCurve.P256), SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256))
        val p384 = generate(KeySpec.Ec(EcCurve.P384), SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256))
        val signed = CompactJws.sign("payload".encodeToByteArray(), p256, JwsAlgorithm.ES256)

        assertFails { CompactJws.sign(byteArrayOf(), p384, JwsAlgorithm.ES256) }
        assertFails { CompactJws.sign(byteArrayOf(), p256, JwsAlgorithm.ES256K) }
        assertFails { CompactJws.verify("$signed=", p256, JwsAlgorithm.ES256) }
    }

    @Test
    fun `RSA key is not intrinsically bound to one JOSE algorithm`() = runTest {
        val key = generate(KeySpec.Rsa(2048), JwsAlgorithm.PS256.toSignatureAlgorithm())

        assertEquals(JwsAlgorithm.PS256, key.selectJwsAlgorithm(setOf("PS256")))
        listOf(JwsAlgorithm.RS256, JwsAlgorithm.PS256).forEach { algorithm ->
            val signed = CompactJws.sign("payload".encodeToByteArray(), key, algorithm)
            assertEquals("payload", CompactJws.verify(signed, key, algorithm).payload.decodeToString())
        }
    }

    @Test
    fun `JWK alg metadata restricts signing and verification`() = runTest {
        val unrestricted = generate(KeySpec.Rsa(2048), JwsAlgorithm.RS256.toSignatureAlgorithm())
        val material = assertIs<EncodedKey.Jwk>(unrestricted.storedKey.material)
        val restricted = runtime.restore(
            Jwk.withMetadata(material, JwkMetadata(algorithm = JwsAlgorithm.RS256.identifier))
                .toStoredSoftwareKey(
                    id = KeyId("restricted-rsa"),
                    usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
                )
        )
        val rs256 = CompactJws.sign("payload".encodeToByteArray(), restricted, JwsAlgorithm.RS256)
        val ps256 = CompactJws.sign("payload".encodeToByteArray(), unrestricted, JwsAlgorithm.PS256)
        val exportedPublic = assertIs<EncodedKey.Jwk>(
            assertNotNull(restricted.capabilities.publicKeyExporter).exportPublicKey()
        )
        val exportedPrivate = assertIs<EncodedKey.Jwk>(
            assertNotNull(restricted.capabilities.privateKeyExporter).exportPrivateKey()
        )

        assertEquals(JwsAlgorithm.RS256, restricted.preferredJwsAlgorithm())
        assertEquals("RS256", Jwk.parse(exportedPublic)["alg"]?.let { (it as JsonPrimitive).content })
        assertEquals("RS256", Jwk.parse(exportedPrivate)["alg"]?.let { (it as JsonPrimitive).content })
        assertEquals("payload", CompactJws.verify(rs256, restricted, JwsAlgorithm.RS256).payload.decodeToString())
        assertFails { CompactJws.sign(byteArrayOf(), restricted, JwsAlgorithm.PS256) }
        assertFails { CompactJws.verify(ps256, restricted, JwsAlgorithm.PS256) }
    }

    @Test
    fun `any b64 or crit protected header is rejected`() = runTest {
        val key = generate(KeySpec.Ec(EcCurve.P256), SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256))

        assertFails {
            CompactJws.sign(
                byteArrayOf(),
                key,
                JwsAlgorithm.ES256,
                JsonObject(mapOf("b64" to JsonPrimitive(true))),
            )
        }
        assertFails {
            CompactJws.sign(
                byteArrayOf(),
                key,
                JwsAlgorithm.ES256,
                JsonObject(mapOf("crit" to JsonArray(emptyList()))),
            )
        }
    }

    @Test
    fun `JOSE uses capability predicates rather than advertised finite sets`() = runTest {
        val signatureAlgorithm = JwsAlgorithm.ES256.toSignatureAlgorithm()
        val key = object : Key {
            override val id = KeyId("predicate-key")
            override val spec = KeySpec.Ec(EcCurve.P256)
            override val usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY)
            override val capabilities = KeyCapabilities(
                signer = Signer { _, _ -> ByteArray(64) },
                verifier = Verifier { _, _, _ -> true },
                signatureAlgorithms = emptySet(),
                supportsSignatureAlgorithm = { it == signatureAlgorithm },
            )
        }

        val signed = CompactJws.sign("predicate".encodeToByteArray(), key, JwsAlgorithm.ES256)

        assertEquals("predicate", CompactJws.verify(signed, key, JwsAlgorithm.ES256).payload.decodeToString())
    }

    private suspend fun generate(
        spec: KeySpec,
        algorithm: SignatureAlgorithm,
    ): SoftwareKey = runtime.generateSoftwareKey(
        GenerateSoftwareKeyRequest(
            id = KeyId("jws-key"),
            spec = spec,
            usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
        ),
    )
}
