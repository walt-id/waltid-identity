package id.walt.cose

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EdwardsCurve
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.KeyCapabilities
import id.walt.crypto2.keys.Signer
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoseCrypto2Test {
    private val runtime = CryptoRuntime(listOf(CryptographySoftwareKeyProvider()))

    @Test
    fun `protected ES256 signs and verifies attached and detached payloads`() = runTest {
        val key = generate(KeySpec.Ec(EcCurve.P256))
        val headers = CoseHeaders(algorithm = Cose.Algorithm.ES256)
        val attached = CoseSign1.createAndSign(headers, payload = "payload".encodeToByteArray(), key = key)
        val detached = CoseSign1.createAndSignDetached(
            headers,
            detachedPayload = "detached".encodeToByteArray(),
            key = key,
            externalAad = "aad".encodeToByteArray(),
        )

        assertTrue(attached.verify(key, Cose.Algorithm.ES256))
        assertTrue(
            detached.verifyDetached(
                key,
                "detached".encodeToByteArray(),
                Cose.Algorithm.ES256,
                "aad".encodeToByteArray(),
            ),
        )
        assertFalse(
            detached.verifyDetached(
                key,
                "tampered".encodeToByteArray(),
                Cose.Algorithm.ES256,
                "aad".encodeToByteArray(),
            ),
        )
        assertFalse(
            detached.verifyDetached(
                key,
                "detached".encodeToByteArray(),
                Cose.Algorithm.ES256,
                "wrong-aad".encodeToByteArray(),
            ),
        )
    }

    @Test
    fun `EdDSA and RSA-PSS use protected algorithms`() = runTest {
        val ed = generate(KeySpec.Edwards(EdwardsCurve.ED25519))
        val rsa = generate(KeySpec.Rsa(2048))
        val p256 = generate(KeySpec.Ec(EcCurve.P256))

        assertEquals(Cose.Algorithm.PS256, rsa.selectCoseSignatureAlgorithm(setOf(Cose.Algorithm.PS256)))
        assertEquals(Cose.Algorithm.ES256, p256.selectCoseSignatureAlgorithm(setOf(Cose.Algorithm.ESP256)))
        listOf(ed to Cose.Algorithm.EdDSA, rsa to Cose.Algorithm.PS256).forEach { (key, algorithm) ->
            val signed = CoseSign1.createAndSign(
                CoseHeaders(algorithm = algorithm),
                payload = byteArrayOf(1, 2, 3),
                key = key,
            )
            assertTrue(signed.verify(key, algorithm))
        }
    }

    @Test
    fun `unprotected or incompatible algorithms are rejected`() = runTest {
        val key = generate(KeySpec.Ec(EcCurve.P256))

        assertFails {
            CoseSign1.createAndSign(
                protectedHeaders = CoseHeaders(),
                unprotectedHeaders = CoseHeaders(algorithm = Cose.Algorithm.ES256),
                payload = byteArrayOf(),
                key = key,
            )
        }
        assertFails {
            CoseSign1.createAndSign(
                protectedHeaders = CoseHeaders(
                    algorithm = Cose.Algorithm.ES256,
                    criticalHeaders = listOf(Cose.HeaderLabel.CONTENT_TYPE),
                    contentType = CoseContentType.AsString("application/example"),
                ),
                payload = byteArrayOf(),
                key = key,
            )
        }
        assertFails {
            CoseSign1.createAndSign(
                protectedHeaders = CoseHeaders(
                    algorithm = Cose.Algorithm.ES256,
                    criticalHeaders = emptyList(),
                ),
                payload = byteArrayOf(),
                key = key,
            )
        }

        val criticalAlgorithm = CoseSign1.createAndSign(
            protectedHeaders = CoseHeaders(
                algorithm = Cose.Algorithm.ES256,
                criticalHeaders = listOf(Cose.HeaderLabel.ALG),
            ),
            payload = byteArrayOf(),
            key = key,
        )
        assertTrue(criticalAlgorithm.verify(key, Cose.Algorithm.ES256))
        assertFails {
            CoseSign1.createAndSign(
                protectedHeaders = CoseHeaders(algorithm = Cose.Algorithm.ES256),
                unprotectedHeaders = CoseHeaders(algorithm = Cose.Algorithm.ES256),
                payload = byteArrayOf(),
                key = key,
            )
        }
        assertFails {
            CoseSign1.createAndSign(
                protectedHeaders = CoseHeaders(
                    algorithm = Cose.Algorithm.ES256,
                    criticalHeaders = listOf(99),
                ),
                payload = byteArrayOf(),
                key = key,
            )
        }

        val valid = CoseSign1.createAndSign(
            protectedHeaders = CoseHeaders(algorithm = Cose.Algorithm.ES256),
            payload = byteArrayOf(),
            key = key,
        )
        assertFails { valid.verify(key, Cose.Algorithm.ES384) }
        assertFails { valid.copy(signature = byteArrayOf(1)).verify(key, Cose.Algorithm.ES256) }
        assertFails {
            CoseSign1.createAndSign(
                protectedHeaders = CoseHeaders(algorithm = Cose.Algorithm.ES384),
                payload = byteArrayOf(),
                key = key,
            )
        }
    }

    @Test
    fun `RSA signature length rounds non-byte-aligned modulus up`() = runTest {
        val algorithm = Cose.Algorithm.RS256.toCrypto2SignatureAlgorithm()
        val key = object : Key {
            override val id = KeyId("rsa-2049")
            override val spec = KeySpec.Rsa(2049)
            override val usages = setOf(KeyUsage.SIGN)
            override val capabilities = KeyCapabilities(
                signer = Signer { _, _ -> ByteArray(257) },
                signatureAlgorithms = setOf(algorithm),
            )
        }

        assertEquals(257, key.toCoseSigner(Cose.Algorithm.RS256).sign(byteArrayOf()).size)
    }

    private suspend fun generate(spec: KeySpec): Key = runtime.generateSoftwareKey(
        GenerateSoftwareKeyRequest(
            id = KeyId("cose-key"),
            spec = spec,
            usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
        ),
    )
}
