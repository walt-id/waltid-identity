package id.walt.crypto

import id.walt.crypto.keys.KeyType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IosKeyTest {

    private val softwareKeyTypes = listOf(KeyType.Ed25519, KeyType.secp256k1)

    // TODO: Hardware (Keychain) tests require a real app bundle context with entitlements.
    //  Add an iOS test app to run these. Uncomment when available.
    //  private val hardwareKeyTypes = listOf(KeyType.secp256r1, KeyType.RSA)

    // --- Hardware ---
    // These tests are correct but cannot run in the bare simulator test runner.
    // They mirror AndroidKeyTest's hardware section.

    // @Test
    // fun hardwareCreate() = runTest {
    //     for (type in hardwareKeyTypes) {
    //         val key = IosKey.Hardware.create(IosKey.Options(kid = "test-hw-$type", keyType = type))
    //         assertNotNull(key, "create failed for $type")
    //         assertEquals(type, key.keyType)
    //         assertTrue(key.hasPrivateKey)
    //     }
    // }

    // @Test
    // fun hardwareSignAndVerifyRaw() = runTest {
    //     for (type in hardwareKeyTypes) {
    //         val key = IosKey.Hardware.create(IosKey.Options(kid = "test-hw-sign-$type", keyType = type))
    //         val plaintext = "Hello $type".encodeToByteArray()
    //         val signature = key.signRaw(plaintext) as ByteArray
    //         assertTrue(signature.isNotEmpty())
    //         val result = key.verifyRaw(signature, plaintext)
    //         assertTrue(result.isSuccess, "verifyRaw failed for $type")
    //     }
    // }

    // @Test
    // fun hardwareSignAndVerifyJws() = runTest {
    //     for (type in hardwareKeyTypes) {
    //         val key = IosKey.Hardware.create(IosKey.Options(kid = "test-hw-jws-$type", keyType = type))
    //         val payload = """{"type":"$type"}""".encodeToByteArray()
    //         val jws = key.signJws(payload)
    //         assertEquals(2, jws.count { it == '.' })
    //         val result = key.verifyJws(jws)
    //         assertTrue(result.isSuccess, "verifyJws failed for $type")
    //     }
    // }

    // @Test
    // fun hardwareExportJwk() = runTest {
    //     for (type in hardwareKeyTypes) {
    //         val key = IosKey.Hardware.create(IosKey.Options(kid = "test-hw-jwk-$type", keyType = type))
    //         val jwk = key.exportJWK()
    //         assertTrue(jwk.contains("\"kty\""), "kty missing for $type")
    //     }
    // }

    // @Test
    // fun hardwareThumbprint() = runTest {
    //     for (type in hardwareKeyTypes) {
    //         val key = IosKey.Hardware.create(IosKey.Options(kid = "test-hw-thumb-$type", keyType = type))
    //         val thumbprint = key.getThumbprint()
    //         assertTrue(thumbprint.isNotEmpty(), "thumbprint empty for $type")
    //     }
    // }

    // --- Software ---

    @Test
    fun softwareCreate() = runTest {
        for (type in softwareKeyTypes) {
            val key = IosKey.Software.create(IosKey.Options(kid = "test-sw-$type", keyType = type))
            assertNotNull(key, "create failed for $type")
            assertEquals(type, key.keyType, "keyType mismatch for $type")
            assertTrue(key.hasPrivateKey, "hasPrivateKey false for $type")
        }
    }

    @Test
    fun softwareExportJwk() = runTest {
        for (type in softwareKeyTypes) {
            val key = IosKey.Software.create(IosKey.Options(kid = "test-sw-jwk-$type", keyType = type))
            val jwk = key.exportJWK()
            assertTrue(jwk.contains("\"kty\""), "kty missing for $type")
            when (type) {
                KeyType.Ed25519 -> {
                    assertTrue(jwk.contains("\"OKP\""), "OKP missing for Ed25519")
                    assertTrue(jwk.contains("\"Ed25519\""), "Ed25519 crv missing")
                }
                KeyType.secp256k1 -> {
                    assertTrue(jwk.contains("\"EC\""), "EC missing for secp256k1")
                    assertTrue(jwk.contains("\"secp256k1\""), "secp256k1 crv missing")
                }
                else -> {}
            }
        }
    }

    @Test
    fun softwareSignAndVerifyRaw() = runTest {
        for (type in softwareKeyTypes) {
            val key = IosKey.Software.create(IosKey.Options(kid = "test-sw-sign-$type", keyType = type))
            val plaintext = "Hello $type".encodeToByteArray()

            val signature = key.signRaw(plaintext) as ByteArray
            assertTrue(signature.isNotEmpty(), "signature empty for $type")

            val result = key.verifyRaw(signature, plaintext)
            assertTrue(result.isSuccess, "verifyRaw failed for $type: ${result.exceptionOrNull()}")
        }
    }

    @Test
    fun softwareSignAndVerifyJws() = runTest {
        for (type in softwareKeyTypes) {
            val key = IosKey.Software.create(IosKey.Options(kid = "test-sw-jws-$type", keyType = type))
            val payload = """{"type":"$type"}""".encodeToByteArray()

            val jws = key.signJws(payload)
            assertEquals(2, jws.count { it == '.' }, "JWS dot count wrong for $type")

            val result = key.verifyJws(jws)
            assertTrue(result.isSuccess, "verifyJws failed for $type: ${result.exceptionOrNull()}")
        }
    }

    @Test
    fun softwareThumbprint() = runTest {
        for (type in softwareKeyTypes) {
            val key = IosKey.Software.create(IosKey.Options(kid = "test-sw-thumb-$type", keyType = type))
            val thumbprint = key.getThumbprint()
            assertTrue(thumbprint.isNotEmpty(), "thumbprint empty for $type")
        }
    }
}
