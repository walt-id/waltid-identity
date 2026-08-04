package id.walt.crypto

import id.walt.crypto.keys.JwkKeyMeta
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.KeySerialization
import id.walt.crypto.keys.jwk.JWKKey
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JWKKeyIosTest {

    private val softwareKeyTypes = listOf(KeyType.Ed25519, KeyType.secp256k1)

    @Test
    fun generate() = runTest {
        for (type in softwareKeyTypes) {
            val key = JWKKey.generate(type)
            assertNotNull(key, "generate failed for $type")
            assertEquals(type, key.keyType, "keyType mismatch for $type")
            assertTrue(key.hasPrivateKey, "hasPrivateKey false for $type")
        }
    }

    @Test
    fun exportJwk() = runTest {
        for (type in softwareKeyTypes) {
            val key = JWKKey.generate(type)
            val jwk = key.exportJWK()
            assertTrue(jwk.contains("\"d\""), "private key 'd' missing for $type")
            when (type) {
                KeyType.Ed25519 -> {
                    assertTrue(jwk.contains("\"OKP\""), "kty OKP missing for Ed25519")
                    assertTrue(jwk.contains("\"Ed25519\""), "crv Ed25519 missing")
                }
                KeyType.secp256k1 -> {
                    assertTrue(jwk.contains("\"EC\""), "kty EC missing for secp256k1")
                    assertTrue(jwk.contains("\"secp256k1\""), "crv secp256k1 missing")
                }
                else -> {}
            }
        }
    }

    @Test
    fun signAndVerifyRaw() = runTest {
        for (type in softwareKeyTypes) {
            val key = JWKKey.generate(type)
            val plaintext = "Hello $type".encodeToByteArray()

            val signature = key.signRaw(plaintext)
            assertNotNull(signature, "signRaw returned null for $type")
            assertTrue(signature.isNotEmpty(), "signature empty for $type")

            val result = key.verifyRaw(signature, plaintext)
            assertTrue(result.isSuccess, "verifyRaw failed for $type: ${result.exceptionOrNull()}")
        }
    }

    @Test
    fun signAndVerifyJws() = runTest {
        for (type in softwareKeyTypes) {
            val key = JWKKey.generate(type)
            val payload = """{"sub":"test","type":"$type"}""".encodeToByteArray()

            val jws = key.signJws(payload)
            assertNotNull(jws, "signJws returned null for $type")
            assertEquals(2, jws.count { it == '.' }, "JWS dot count wrong for $type")

            val result = key.verifyJws(jws)
            assertTrue(result.isSuccess, "verifyJws failed for $type: ${result.exceptionOrNull()}")
        }
    }

    @Test
    fun publicKeyExtraction() = runTest {
        for (type in softwareKeyTypes) {
            val key = JWKKey.generate(type)
            val pubKey = key.getPublicKey()
            assertEquals(type, pubKey.keyType, "public key type mismatch for $type")
            assertEquals(key.getKeyId(), pubKey.getKeyId(), "public key id mismatch for $type")
            assertTrue(!pubKey.hasPrivateKey, "public key has private for $type")
            assertTrue(!pubKey.exportJWK().contains("\"d\""), "public JWK contains 'd' for $type")
        }
    }

    @Test
    fun importJwkRoundtrip() = runTest {
        for (type in softwareKeyTypes) {
            val key = JWKKey.generate(type)
            val exported = key.exportJWK()

            val imported = JWKKey.importJWK(exported).getOrThrow()
            assertEquals(type, imported.keyType, "imported keyType mismatch for $type")
            assertTrue(imported.hasPrivateKey, "imported key missing private for $type")

            val plaintext = "roundtrip $type".encodeToByteArray()
            val sig = imported.signRaw(plaintext)
            val result = imported.verifyRaw(sig, plaintext)
            assertTrue(result.isSuccess, "imported key sign/verify failed for $type")
        }
    }

    @Test
    fun importRawPublicSoftwareKeys() = runTest {
        for (type in softwareKeyTypes) {
            val privateKey = JWKKey.generate(type)
            val rawPublicKey = privateKey.getPublicKeyRepresentation()
            val imported = JWKKey.importRawPublicKey(type, rawPublicKey, JwkKeyMeta("raw-$type")) as JWKKey

            assertEquals(type, imported.keyType, "imported keyType mismatch for $type")
            assertTrue(!imported.hasPrivateKey, "raw public import should not create private material for $type")

            val plaintext = "raw import $type".encodeToByteArray()
            val signature = privateKey.signRaw(plaintext)
            val result = imported.verifyRaw(signature, plaintext)
            assertTrue(result.isSuccess, "raw imported public key verify failed for $type: ${result.exceptionOrNull()}")

            val importedWithoutKid = JWKKey.importRawPublicKey(type, rawPublicKey, null) as JWKKey
            assertTrue("kid" !in importedWithoutKid.exportJWKObject(), "missing metadata should not add kid")
        }
    }

    @Test
    fun importJwkAcceptsSecp256k1P256KAlias() = runTest {
        val key = JWKKey.generate(KeyType.secp256k1)
        val aliasedJwk = key.exportJWK().replace("\"secp256k1\"", "\"P-256K\"")

        val imported = JWKKey.importJWK(aliasedJwk).getOrThrow()

        assertEquals(KeyType.secp256k1, imported.keyType)
    }

    @Test
    fun keySerializationInitializes() = runTest {
        val key = JWKKey.generate(KeyType.Ed25519)
        val serialized = KeySerialization.serializeKey(key)

        assertTrue(serialized.contains("\"jwk\""), "serialized key should use JWK polymorphic type")
    }

    @Test
    fun rejectsPkcs8PrivatePemImport() = runTest {
        assertPrivatePemImportUnsupported("PRIVATE KEY")
    }

    @Test
    fun rejectsPkcs1RsaPrivatePemImport() = runTest {
        assertPrivatePemImportUnsupported("RSA PRIVATE KEY")
    }

    @Test
    fun rejectsSec1EcPrivatePemImport() = runTest {
        assertPrivatePemImportUnsupported("EC PRIVATE KEY")
    }

    @Test
    fun importsPublicKeyPem() = runTest {
        val key = JWKKey.importPEM(publicKeyPem).getOrThrow()

        assertEquals(KeyType.secp256r1, key.keyType)
        assertFalse(key.hasPrivateKey)
    }

    @Test
    fun importsCertificatePemAsPublicKey() = runTest {
        val key = JWKKey.importPEM(certificatePem).getOrThrow()

        assertEquals(KeyType.secp256r1, key.keyType)
        assertFalse(key.hasPrivateKey)
    }

    private suspend fun assertPrivatePemImportUnsupported(header: String) {
        val exception = assertFailsWith<UnsupportedOperationException> {
            JWKKey.importPEM(privatePem(header)).getOrThrow()
        }

        assertEquals("Importing private PEM keys is not supported on iOS", exception.message)
    }

    private fun privatePem(header: String): String = """
        -----BEGIN $header-----
        cGVuZGluZw==
        -----END $header-----
    """.trimIndent()

    companion object {
        private val publicKeyPem = """
            -----BEGIN PUBLIC KEY-----
            MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEuo896Ho570UP24xyyNt7dE3U6qHl
            DNJth0Hc/u/uJ2H0+7gRyILHJOH15UTFrQWcmIlnnzNAplM+d8pelYwK2g==
            -----END PUBLIC KEY-----
        """.trimIndent()

        private val certificatePem = """
            -----BEGIN CERTIFICATE-----
            MIIC8TCCApigAwIBAgIKXkW8UsGZRHyHyDAKBggqhkjOPQQDAjB1MQswCQYDVQQG
            EwJBVTEtMCsGA1UEAwwkQXVzdHJvYWRzIFByZS1wcm9kdWN0aW9uIERUUyBSb290
            IENBMTcwNQYDVQQKDC5BdXN0cm9hZHMgUHJlLXByb2R1Y3Rpb24gRGlnaXRhbCBU
            cnVzdCBTZXJ2aWNlMB4XDTI0MDkwMjAzMjQ0MVoXDTQ0MDkwMjAzMjQ0MVowdTEL
            MAkGA1UEBhMCQVUxLTArBgNVBAMMJEF1c3Ryb2FkcyBQcmUtcHJvZHVjdGlvbiBE
            VFMgUm9vdCBDQTE3MDUGA1UECgwuQXVzdHJvYWRzIFByZS1wcm9kdWN0aW9uIERp
            Z2l0YWwgVHJ1c3QgU2VydmljZTBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABMJt
            zT2r7UHttv6jSGEso6dVKF9QwsEyXcb4EUzciXonsLIorpUiG5tuuIUoD0fRbGNV
            KD4yA3KJs6R8pe/94c6jggEOMIIBCjASBgNVHRMBAf8ECDAGAQH/AgEAMA4GA1Ud
            DwEB/wQEAwIBBjAdBgNVHQ4EFgQUjQCqUhB6NiazWEI4a0qWZ7mXlRYwgcQGA1Ud
            HwSBvDCBuTCBtqCBs6CBsIaBrWh0dHBzOi8vYXVzdHJvYWRzLWR0cy1wcmUtcHJk
            LnZpaS5hdTAxLm1hdHRyLmdsb2JhbC92MS9lY29zeXN0ZW1zLzljOTVmNjY2LWNk
            Y2UtNGU4YS1iY2Q3LWRkNzQ0ZjQ3ODhmNC92aWNhbHMvcHVibGljL2NlcnRpZmlj
            YXRlcy9jYS9kOTRkZTEyNi1lOTgyLTRmOTUtYTUzNS1iZDA3NjcwOWU2NmYvY3Js
            MAoGCCqGSM49BAMCA0cAMEQCIGxwNRWAq0B4DU/OlHjal0gULknk3JD4w1+Mtrpb
            yPxFAiAaQMxnrcRJopU6SRrNTq1x29UlFJdaE7XHvdXu1sXnDA==
            -----END CERTIFICATE-----
        """.trimIndent()
    }
}
