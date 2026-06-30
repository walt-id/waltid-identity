package id.walt.crypto

import id.walt.crypto.keys.KeyType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class AndroidKeyTest {

    private val platformKeyTypes = listOf(KeyType.secp256r1, KeyType.secp384r1, KeyType.secp521r1, KeyType.RSA)
    private val platformSignKeyTypes = platformKeyTypes
    private val softwareKeyTypes = listOf(KeyType.Ed25519, KeyType.secp256k1)
    private val testAliases = mutableListOf<String>()

    @After
    fun cleanup() = runTest {
        testAliases.forEach { alias ->
            runCatching { AndroidKey.Platform.delete(alias) }
        }
    }

    // --- Platform key store ---

    @Test
    fun platformCreate() = runTest {
        for (type in platformKeyTypes) {
            val alias = "test-hw-$type-${System.currentTimeMillis()}"
            testAliases.add(alias)

            val key = AndroidKey.Platform.create(AndroidKey.Options(kid = alias, keyType = type))
            assertNotNull(key, "create failed for $type")
            assertEquals(alias, key.getKeyId())
            assertEquals(type, key.keyType, "keyType mismatch for $type")
            assertTrue(key.hasPrivateKey, "hasPrivateKey false for $type")
        }
    }

    @Test
    fun platformExportJwk() = runTest {
        for (type in platformKeyTypes) {
            val alias = "test-hw-jwk-$type-${System.currentTimeMillis()}"
            testAliases.add(alias)

            val key = AndroidKey.Platform.create(AndroidKey.Options(kid = alias, keyType = type))
            val jwk = key.exportJWK()
            assertTrue(jwk.contains("\"kty\""), "kty missing for $type")
        }
    }

    @Test
    fun platformSignAndVerifyRaw() = runTest {
        for (type in platformSignKeyTypes) {
            val alias = "test-hw-sign-$type-${System.currentTimeMillis()}"
            testAliases.add(alias)

            val key = AndroidKey.Platform.create(AndroidKey.Options(kid = alias, keyType = type))
            val plaintext = "Hello $type".encodeToByteArray()

            val signature = key.signRaw(plaintext)
            assertTrue(signature.isNotEmpty(), "signature empty for $type")

            val result = key.verifyRaw(signature, plaintext)
            assertTrue(result.isSuccess, "verifyRaw failed for $type: ${result.exceptionOrNull()}")
        }
    }

    @Test
    fun platformSignAndVerifyJws() = runTest {
        for (type in platformSignKeyTypes) {
            val alias = "test-hw-jws-$type-${System.currentTimeMillis()}"
            testAliases.add(alias)

            val key = AndroidKey.Platform.create(AndroidKey.Options(kid = alias, keyType = type))
            val payload = """{"type":"$type"}""".encodeToByteArray()

            val jws = key.signJws(payload)
            assertEquals(2, jws.count { it == '.' }, "JWS dot count wrong for $type")

            val result = key.verifyJws(jws)
            assertTrue(result.isSuccess, "verifyJws failed for $type: ${result.exceptionOrNull()}")
        }
    }

    @Test
    fun platformThumbprint() = runTest {
        for (type in platformKeyTypes) {
            val alias = "test-hw-thumb-$type-${System.currentTimeMillis()}"
            testAliases.add(alias)

            val key = AndroidKey.Platform.create(AndroidKey.Options(kid = alias, keyType = type))
            val thumbprint = key.getThumbprint()
            assertTrue(thumbprint.isNotEmpty(), "thumbprint empty for $type")
        }
    }

    // --- Software ---

    @Test
    fun softwareCreate() = runTest {
        for (type in softwareKeyTypes) {
            val key = AndroidKey.Software.create(AndroidKey.Options(kid = "test-sw-$type", keyType = type))
            assertNotNull(key, "create failed for $type")
            assertEquals(type, key.keyType, "keyType mismatch for $type")
            assertTrue(key.hasPrivateKey, "hasPrivateKey false for $type")
        }
    }

    @Test
    fun softwareExportJwk() = runTest {
        for (type in softwareKeyTypes) {
            val key = AndroidKey.Software.create(AndroidKey.Options(kid = "test-sw-jwk-$type", keyType = type))
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
            val key = AndroidKey.Software.create(AndroidKey.Options(kid = "test-sw-sign-$type", keyType = type))
            val plaintext = "Hello $type".encodeToByteArray()

            val signature = key.signRaw(plaintext)
            assertTrue(signature.isNotEmpty(), "signature empty for $type")

            val result = key.verifyRaw(signature, plaintext)
            assertTrue(result.isSuccess, "verifyRaw failed for $type: ${result.exceptionOrNull()}")
        }
    }

    @Test
    fun softwareSignAndVerifyJws() = runTest {
        for (type in softwareKeyTypes) {
            val key = AndroidKey.Software.create(AndroidKey.Options(kid = "test-sw-jws-$type", keyType = type))
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
            val key = AndroidKey.Software.create(AndroidKey.Options(kid = "test-sw-thumb-$type", keyType = type))
            val thumbprint = key.getThumbprint()
            assertTrue(thumbprint.isNotEmpty(), "thumbprint empty for $type")
        }
    }
}
