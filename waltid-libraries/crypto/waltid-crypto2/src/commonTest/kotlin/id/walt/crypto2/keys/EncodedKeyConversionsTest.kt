package id.walt.crypto2.keys

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EncodedKeyConversionsTest {
    private val provider = CryptographySoftwareKeyProvider()
    private val runtime = CryptoRuntime(listOf(provider))

    @Test
    fun `EC EdDSA and RSA convert JWK to SPKI and back without private material`() = runTest {
        val cases = listOf(
            KeySpec.Ec(EcCurve.P256) to SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256),
            KeySpec.Edwards(EdwardsCurve.ED25519) to SignatureAlgorithm.EdDsa,
            KeySpec.Rsa(2048) to SignatureAlgorithm.RsaPkcs1(DigestAlgorithm.SHA_256),
        )
        cases.forEachIndexed { index, (spec, algorithm) ->
            val privateKey = runtime.generateSoftwareKey(
                GenerateSoftwareKeyRequest(
                    id = KeyId("conversion-$index"),
                    spec = spec,
                    usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
                )
            )
            val privateJwk = privateKey.storedKey.material as EncodedKey.Jwk
            val spki = privateJwk.toSpkiDer(spec)
            val publicJwk = spki.toPublicJwk(spec)
            val publicJson = Json.parseToJsonElement(publicJwk.data.toByteArray().decodeToString()).jsonObject
            assertFalse("d" in publicJson)
            assertFalse("p" in publicJson)

            val publicKey = runtime.restore(
                privateKey.storedKey.copy(
                    usages = setOf(KeyUsage.VERIFY),
                    material = publicJwk,
                )
            )
            val message = "conversion".encodeToByteArray()
            val signature = requireNotNull(privateKey.capabilities.signer).sign(message, algorithm)
            assertTrue(requireNotNull(publicKey.capabilities.verifier).verify(message, signature, algorithm))
        }
    }

    @Test
    fun `X25519 public key converts through SPKI`() = runTest {
        val spec = KeySpec.Montgomery(MontgomeryCurve.X25519)
        val key = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("x25519"),
                spec = spec,
                usages = setOf(KeyUsage.KEY_AGREEMENT),
            )
        )
        val spki = key.storedKey.material.toSpkiDer(spec)
        val publicJwk = spki.toPublicJwk(spec)

        assertFalse(publicJwk.privateMaterial)
        assertTrue(publicJwk.data.size > 0)
    }
}
