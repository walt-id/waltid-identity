package id.walt.cose

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class CoseCrypto2Test {
    private val runtime = CryptoRuntime(defaultSoftwareKeyProviders())

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
    fun `fully specified ESP256 signs and verifies without weakening the algorithm allowlist`() = runTest {
        val key = generate(KeySpec.Ec(EcCurve.P256))
        val signed = CoseSign1.createAndSignDetached(
            protectedHeaders = CoseHeaders(algorithm = Cose.Algorithm.ESP256),
            detachedPayload = "reader-authentication".encodeToByteArray(),
            key = key,
        )

        assertTrue(
            signed.verifyDetached(
                key,
                "reader-authentication".encodeToByteArray(),
                Cose.Algorithm.ESP256,
            )
        )
        assertFails {
            signed.verifyDetached(
                key,
                "reader-authentication".encodeToByteArray(),
                Cose.Algorithm.ES256,
            )
        }
        assertFails {
            CoseSign1.createAndSignDetached(
                protectedHeaders = CoseHeaders(algorithm = Cose.Algorithm.ESP384),
                detachedPayload = byteArrayOf(),
                key = key,
            )
        }
    }

    @Test
    fun `fully specified algorithm identifiers match RFC 9864`() {
        assertEquals(-9, Cose.Algorithm.ESP256)
        assertEquals(-51, Cose.Algorithm.ESP384)
        assertEquals(-52, Cose.Algorithm.ESP512)
        assertEquals(-19, Cose.Algorithm.Ed25519)
        assertEquals(-53, Cose.Algorithm.Ed448)
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
        // A malformed signature is an invalid signature, not a caller error.
        assertFalse(valid.copy(signature = byteArrayOf(1)).verify(key, Cose.Algorithm.ES256))
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
