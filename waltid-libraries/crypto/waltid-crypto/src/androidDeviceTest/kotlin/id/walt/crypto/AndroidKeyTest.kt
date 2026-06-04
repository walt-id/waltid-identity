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

    private val testAliases = mutableListOf<String>()

    @After
    fun cleanup() = runTest {
        testAliases.forEach { alias ->
            runCatching { AndroidKey.delete(alias, KeyType.secp256r1) }
            runCatching { AndroidKey.delete(alias, KeyType.RSA) }
        }
    }

    @Test
    fun generateP256Key() = runTest {
        val alias = "test-p256-${System.currentTimeMillis()}"
        testAliases.add(alias)

        val key = AndroidKey.create(AndroidKey.Options(kid = alias, keyType = KeyType.secp256r1))
        assertNotNull(key)
        assertEquals(alias, key.getKeyId())
        assertEquals(KeyType.secp256r1, key.keyType)
        assertTrue(key.hasPrivateKey)
    }

    @Test
    fun generateRSAKey() = runTest {
        val alias = "test-rsa-${System.currentTimeMillis()}"
        testAliases.add(alias)

        val key = AndroidKey.create(AndroidKey.Options(kid = alias, keyType = KeyType.RSA))
        assertNotNull(key)
        assertEquals(alias, key.getKeyId())
        assertEquals(KeyType.RSA, key.keyType)
    }

    @Test
    fun exportJWK() = runTest {
        val alias = "test-jwk-export-${System.currentTimeMillis()}"
        testAliases.add(alias)

        val key = AndroidKey.create(AndroidKey.Options(kid = alias, keyType = KeyType.secp256r1))
        val jwk = key.exportJWK()
        assertNotNull(jwk)
        assertTrue(jwk.contains("\"kty\""))
        assertTrue(jwk.contains("\"crv\"") || jwk.contains("\"EC\""))
    }

    @Test
    fun signAndVerifyRaw() = runTest {
        val alias = "test-sign-raw-${System.currentTimeMillis()}"
        testAliases.add(alias)

        val key = AndroidKey.create(AndroidKey.Options(kid = alias, keyType = KeyType.secp256r1))
        val plaintext = "Hello, Android KeyStore!".toByteArray()

        val signature = key.signRaw(plaintext) as ByteArray
        assertNotNull(signature)
        assertTrue(signature.isNotEmpty())

        val result = key.verifyRaw(signature, plaintext)
        assertTrue(result.isSuccess)
    }

    @Test
    fun signAndVerifyJws() = runTest {
        val alias = "test-sign-jws-${System.currentTimeMillis()}"
        testAliases.add(alias)

        val key = AndroidKey.create(AndroidKey.Options(kid = alias, keyType = KeyType.secp256r1))
        val plaintext = """{"sub":"test","iat":1234567890}""".toByteArray()

        val jws = key.signJws(plaintext, mapOf("kid" to kotlinx.serialization.json.JsonPrimitive(alias)))
        assertNotNull(jws)
        assertTrue(jws.count { it == '.' } == 2)

        val result = key.verifyJws(jws)
        assertTrue(result.isSuccess)
    }

    @Test
    fun loadKeyByAlias() = runTest {
        val alias = "test-load-${System.currentTimeMillis()}"
        testAliases.add(alias)

        val original = AndroidKey.create(AndroidKey.Options(kid = alias, keyType = KeyType.secp256r1))
        val originalJwk = original.exportJWK()

        val loaded = AndroidKey.load(AndroidKey.Options(kid = alias, keyType = KeyType.secp256r1))
        val loadedJwk = loaded.exportJWK()

        assertEquals(originalJwk, loadedJwk)
    }

    @Test
    fun deleteKey() = runTest {
        val alias = "test-delete-${System.currentTimeMillis()}"

        AndroidKey.create(AndroidKey.Options(kid = alias, keyType = KeyType.secp256r1))
        val deleted = AndroidKey.delete(alias, KeyType.secp256r1)

        val loadResult = runCatching {
            AndroidKey.load(AndroidKey.Options(kid = alias, keyType = KeyType.secp256r1))
        }
        assertTrue(loadResult.isFailure)
    }

    @Test
    fun thumbprint() = runTest {
        val alias = "test-thumbprint-${System.currentTimeMillis()}"
        testAliases.add(alias)

        val key = AndroidKey.create(AndroidKey.Options(kid = alias, keyType = KeyType.secp256r1))
        val thumbprint = key.getThumbprint()
        assertNotNull(thumbprint)
        assertTrue(thumbprint.isNotEmpty())
    }
}
