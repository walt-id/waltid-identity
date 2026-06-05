package id.walt.crypto

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
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
}
